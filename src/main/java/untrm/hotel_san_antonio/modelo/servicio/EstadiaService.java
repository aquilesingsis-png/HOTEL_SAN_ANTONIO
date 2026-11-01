package untrm.hotel_san_antonio.modelo.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;
import untrm.hotel_san_antonio.modelo.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.modelo.dao.CambioHabitacionDAO;
import untrm.hotel_san_antonio.modelo.dao.ComprobanteDAO;
import untrm.hotel_san_antonio.modelo.dao.HabitacionDAO;
import untrm.hotel_san_antonio.modelo.dao.PagoDAO;
import untrm.hotel_san_antonio.modelo.dao.ReservaDAO;
import untrm.hotel_san_antonio.modelo.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.seguridad.Permisos;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

/**
 * Lo que pasa con una reserva ya hecha y con la habitación mientras el huésped está en el hotel: check-in,
 * deshacer un check-in, cambio de habitación y reservas que no llegaron. Registrar y cancelar reservas está en
 * ReservaService.
 */
public class EstadiaService {

    public record ResultadoCambio(BigDecimal total, BigDecimal pagado, BigDecimal saldo) {}
    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final CambioHabitacionDAO cambioHabitacionDAO = new CambioHabitacionDAO();
    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final ComprobanteDAO comprobanteDAO = new ComprobanteDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();
    private final CuentaService cuentas = new CuentaService();

    public List<Habitacion> listarHabitacionesParaCambio(int idReserva) throws SQLException {
        obtenerIdUsuarioAutenticado();
        try (Connection con = ConexionBD.conectar()) {
            Reserva reserva = reservaDAO.buscarPorId(con, idReserva, false);
            if (reserva == null || !("CONFIRMADA".equals(reserva.getEstado())
                    || "CHECKIN".equals(reserva.getEstado()))) return List.of();
            Habitacion origen = habitacionDAO.buscarPorId(con, reserva.getIdHabitacion());
            if (origen == null) return List.of();
            List<Habitacion> resultado = new java.util.ArrayList<>();
            for (Habitacion candidata : habitacionDAO.listar(con)) {
                if (candidata.getIdHabitacion() == origen.getIdHabitacion()
                        || !esMismoTipoOSuperior(con, origen, candidata)
                        || candidata.getTipo().getCapacidad() < reserva.getNumHuespedes()
                        || "MANTENIMIENTO".equals(candidata.getEstado())
                        || "LIMPIEZA".equals(candidata.getEstado())
                        || ("CHECKIN".equals(reserva.getEstado())
                            && !"DISPONIBLE".equals(candidata.getEstado()))
                        || reservaDAO.existeCruce(con, candidata.getIdHabitacion(),
                                reserva.getFechaCheckin(), reserva.getFechaCheckout())) continue;
                resultado.add(candidata);
            }
            return resultado;
        }
    }

    /** Bloquea ambas habitaciones, valida nuevamente y registra el cambio íntegro. */
    public ResultadoCambio cambiarHabitacion(int idReserva, int idNueva, String motivo) throws SQLException {
        int usuario = obtenerIdUsuarioAutenticado();
        if (motivo != null && motivo.length() > 300) {
            throw new IllegalArgumentException("El motivo no puede superar 300 caracteres.");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Reserva inicial = reservaDAO.buscarPorId(con, idReserva, false);
                if (inicial == null) throw new IllegalArgumentException("La reserva no existe.");
                int idAnterior = inicial.getIdHabitacion();
                if (idAnterior == idNueva) throw new IllegalArgumentException("Seleccione otra habitación.");
                bloquearHabitacion(con, Math.min(idAnterior, idNueva));
                bloquearHabitacion(con, Math.max(idAnterior, idNueva));
                Reserva reserva = reservaDAO.buscarPorId(con, idReserva, true);
                if (reserva == null || reserva.getIdHabitacion() != idAnterior) {
                    throw new IllegalStateException("La reserva cambió; actualice la pantalla.");
                }
                if (!("CONFIRMADA".equals(reserva.getEstado()) || "CHECKIN".equals(reserva.getEstado()))
                        || !LocalDate.now().isBefore(reserva.getFechaCheckout())) {
                    throw new IllegalStateException("Solo se puede cambiar una reserva activa.");
                }
                Habitacion anterior = habitacionDAO.buscarPorId(con, idAnterior);
                Habitacion nueva = habitacionDAO.buscarPorId(con, idNueva);
                if (anterior == null || nueva == null || !esMismoTipoOSuperior(con, anterior, nueva)) {
                    throw new IllegalArgumentException("La nueva habitación debe ser del mismo tipo o superior configurado.");
                }
                if (nueva.getTipo().getCapacidad() < reserva.getNumHuespedes()) {
                    throw new IllegalArgumentException("La nueva habitación no admite a todos los huéspedes.");
                }
                if ("MANTENIMIENTO".equals(nueva.getEstado()) || "LIMPIEZA".equals(nueva.getEstado())
                        || ("CHECKIN".equals(reserva.getEstado()) && !"DISPONIBLE".equals(nueva.getEstado()))
                        || reservaDAO.existeCruce(con, idNueva, reserva.getFechaCheckin(), reserva.getFechaCheckout())) {
                    throw new ConflictoFechasException("La nueva habitación no está disponible.");
                }
                LocalDate desde = "CHECKIN".equals(reserva.getEstado())
                        ? LocalDate.now().isAfter(reserva.getFechaCheckin()) ? LocalDate.now() : reserva.getFechaCheckin()
                        : reserva.getFechaCheckin();
                long noches = ChronoUnit.DAYS.between(desde, reserva.getFechaCheckout());
                BigDecimal diferencia = nueva.getTipo().getPrecioBase()
                        .subtract(anterior.getTipo().getPrecioBase()).multiply(BigDecimal.valueOf(noches));
                BigDecimal totalNuevo = reserva.getMontoTotal().add(diferencia).setScale(2, RoundingMode.HALF_UP);
                if (totalNuevo.signum() < 0) throw new IllegalStateException("La tarifa calculada no es válida.");
                BigDecimal pagado = pagoDAO.sumarPorReserva(con, idReserva);
                reservaDAO.cambiarHabitacionYPrecio(con, idReserva, idNueva, totalNuevo);
                int consumosTrasladados = 0;
                if ("CHECKIN".equals(reserva.getEstado())) {
                    habitacionDAO.cambiarEstado(con, idAnterior, "LIMPIEZA");
                    habitacionDAO.cambiarEstado(con, idNueva, "OCUPADA");
                    // la cuenta se arma por habitación: los consumos aún sin cobrar se van con el huésped
                    consumosTrasladados = ventaDAO.trasladarConsumosPendientes(con, idAnterior, idNueva,
                            reserva.getIdHuesped(), reserva.getFechaCheckin());
                }
                cambioHabitacionDAO.registrar(con, idReserva, idAnterior, idNueva, usuario,
                        reserva.getMontoTotal(), totalNuevo, textoOpcional(motivo));
                auditoriaDAO.registrar(con, usuario, "CAMBIO_HABITACION", "reserva", idReserva,
                        anterior.getNumero() + " → " + nueva.getNumero()
                        + (consumosTrasladados > 0 ? " · consumos trasladados: " + consumosTrasladados : ""));
                con.commit();
                return new ResultadoCambio(totalNuevo, pagado, totalNuevo.subtract(pagado));
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    /**
     * Cancela las reservas confirmadas cuyo huesped no llego (desde el mediodia del dia siguiente al de su llegada).
     * El adelanto no se devuelve: queda como cobrado, igual que en cualquier cancelacion.
     * @return cuantas reservas se marcaron
     */
    public int marcarNoLlegaron() throws SQLException {
        int idUsuario = obtenerIdUsuarioAutenticado();
        try (Connection conexion = ConexionBD.conectar()) {
            conexion.setAutoCommit(false);
            try {
                List<Reserva> noLlegaron = reservaDAO.listarNoLlegaron(conexion);
                int marcadas = 0;
                for (Reserva r : noLlegaron) {
                    if (reservaDAO.cancelarSiCancelable(conexion, r.getIdReserva(), "No llegó",
                            "Cancelada por el sistema: no hizo check-in hasta el mediodía del día siguiente a su llegada. "
                            + "El adelanto no se devuelve.")) {
                        auditoriaDAO.registrar(conexion, idUsuario, "RESERVA_NO_LLEGO", "reserva", r.getIdReserva(),
                                "hab. " + r.getNumeroHabitacion() + " · debía llegar el "
                                + r.getFechaCheckin().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                        marcadas++;
                    }
                }
                conexion.commit();
                return marcadas;
            } catch (SQLException | RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    private String numeroHabitacion(Connection con, int idHabitacion) throws SQLException {
        Habitacion h = habitacionDAO.buscarPorId(con, idHabitacion);
        return h == null ? String.valueOf(idHabitacion) : h.getNumero();
    }

    private boolean esMismoTipoOSuperior(Connection con, Habitacion anterior, Habitacion nueva) throws SQLException {
        if (anterior.getIdTipo() == nueva.getIdTipo()) return true;
        Integer actual = habitacionDAO.nivelCategoria(con, anterior.getIdTipo());
        Integer destino = habitacionDAO.nivelCategoria(con, nueva.getIdTipo());
        return actual != null && destino != null && destino > actual;
    }

    public void hacerCheckIn(int idHabitacion) throws SQLException {
        int idUsuario = obtenerIdUsuarioAutenticado();
        try (Connection conexion = ConexionBD.conectar()) {
            conexion.setAutoCommit(false);
            try {
                bloquearHabitacion(conexion, idHabitacion);
                if (!"DISPONIBLE".equals(habitacionDAO.obtenerEstado(conexion, idHabitacion))) {
                    throw new IllegalStateException("La habitación no está disponible para recibir al huésped todavía.");
                }
                int idReserva = reservaDAO.buscarParaCheckin(conexion, idHabitacion, LocalDate.now());
                if (idReserva < 0 || !reservaDAO.actualizarEstadoSiEs(
                        conexion, idReserva, "CONFIRMADA", "CHECKIN")) {
                    throw new IllegalStateException("La habitación no tiene una reserva confirmada para hoy.");
                }
                habitacionDAO.cambiarEstado(conexion, idHabitacion, "OCUPADA");
                auditoriaDAO.registrar(conexion, idUsuario, "CHECKIN", "reserva", idReserva,
                        "hab. " + numeroHabitacion(conexion, idHabitacion));
                conexion.commit();
            } catch (SQLException | RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Deshace un check-in hecho por error: la reserva vuelve a CONFIRMADA y la habitacion a DISPONIBLE. Solo el mismo
     * dia en que se hizo y mientras la estadia no tenga consumos de tienda ni comprobante. Los pagos se conservan
     * en la reserva (si hay que devolverlos, se registra un egreso en Caja).
     */
    public void deshacerCheckIn(int idHabitacion, String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 3 || razon.length() > 200) {
            throw new IllegalArgumentException("Indique el motivo (entre 3 y 200 caracteres).");
        }
        int idUsuario = obtenerIdUsuarioAutenticado();
        try (Connection conexion = ConexionBD.conectar()) {
            conexion.setAutoCommit(false);
            try {
                bloquearHabitacion(conexion, idHabitacion);
                CuentaHabitacion cuenta = cuentas.obtener(conexion, idHabitacion);
                if (cuenta == null) {
                    throw new IllegalStateException("La habitación no tiene una estadía en curso.");
                }
                int idReserva = cuenta.getIdReserva();
                LocalDateTime hecho = auditoriaDAO.ultimaFecha(conexion, "reserva", idReserva, "CHECKIN", "CHECKIN_DIRECTO");
                if (hecho == null || !hecho.toLocalDate().equals(LocalDate.now())) {
                    throw new IllegalStateException("El check-in de esta habitación no se hizo hoy, por eso ya no se puede deshacer.");
                }
                String comprobante = comprobanteDAO.numeroDeReserva(conexion, idReserva);
                if (comprobante != null) {
                    throw new IllegalStateException("La estadía ya tiene el comprobante " + comprobante
                            + ". El administrador debe anularlo primero.");
                }
                if (ventaDAO.contarConsumosActivos(conexion, idHabitacion, cuenta.getIdHuesped(), cuenta.getIngreso()) > 0) {
                    throw new IllegalStateException("Ya hay consumos de la tienda cargados a esta habitación. "
                            + "Anúlalos primero y vuelve a intentarlo.");
                }
                if (!reservaDAO.actualizarEstadoSiEs(conexion, idReserva, "CHECKIN", "CONFIRMADA")) {
                    throw new IllegalStateException("La estadía ya no está en curso.");
                }
                habitacionDAO.cambiarEstado(conexion, idHabitacion, "DISPONIBLE");
                auditoriaDAO.registrar(conexion, idUsuario, "CHECKIN_DESHACER", "reserva", idReserva,
                        "hab. " + numeroHabitacion(conexion, idHabitacion) + " · R-" + String.format("%04d", idReserva)
                        + " · " + cuenta.getHuesped() + " · motivo: " + razon);
                conexion.commit();
            } catch (SQLException | RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    public LocalDate proximaReserva(int idHabitacion) throws SQLException {
        try (Connection conexion = ConexionBD.conectar()) {
            LocalDate hoy = LocalDate.now();
            return reservaDAO.primerCruce(conexion, idHabitacion, hoy, hoy.plusYears(1));
        }
    }
    private int obtenerIdUsuarioAutenticado() {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (SesionActual.getUsuario() == null || SesionActual.getUsuario().getIdUsuario() <= 0) {
            throw new IllegalStateException("No existe una sesión de usuario válida. Inicie sesión nuevamente.");
        }
        return SesionActual.getUsuario().getIdUsuario();
    }

    private String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Bloquea la habitación para que dos operaciones sobre la misma no se crucen; falla si ya no existe. */
    private void bloquearHabitacion(Connection con, int idHabitacion) throws SQLException {
        if (!reservaDAO.bloquearHabitacion(con, idHabitacion)) {
            throw new IllegalStateException("La habitación seleccionada ya no existe.");
        }
    }
}
