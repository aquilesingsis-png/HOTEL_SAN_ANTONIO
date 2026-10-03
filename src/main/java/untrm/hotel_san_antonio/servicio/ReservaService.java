package untrm.hotel_san_antonio.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import untrm.hotel_san_antonio.dao.EmpresaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.HuespedDAO;
import untrm.hotel_san_antonio.dao.PagoDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.dao.ReservaHuespedDAO;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.CambioHabitacionDAO;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.Validador;

/** Fuente única de las reglas de negocio del módulo Reservas. */
public class ReservaService {

    public static final int MAX_NOCHES = 60;
    private static final int MAX_DIAS_ANTICIPACION = 365;
    private static final BigDecimal PORCENTAJE_ADELANTO = new BigDecimal("0.50");
    private static final Set<String> METODOS_PAGO = Set.of("YAPE", "TRANSFERENCIA", "EFECTIVO", "TARJETA");

    private final HuespedDAO huespedDAO = new HuespedDAO();
    private final EmpresaDAO empresaDAO = new EmpresaDAO();
    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final ReservaHuespedDAO reservaHuespedDAO = new ReservaHuespedDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();
    private final CambioHabitacionDAO cambioHabitacionDAO = new CambioHabitacionDAO();

    public record ResultadoCambio(BigDecimal total, BigDecimal pagado, BigDecimal saldo) {}

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
                reservaDAO.bloquearHabitacion(con, Math.min(idAnterior, idNueva));
                reservaDAO.bloquearHabitacion(con, Math.max(idAnterior, idNueva));
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
                if ("CHECKIN".equals(reserva.getEstado())) {
                    habitacionDAO.cambiarEstado(con, idAnterior, "LIMPIEZA");
                    habitacionDAO.cambiarEstado(con, idNueva, "OCUPADA");
                }
                cambioHabitacionDAO.registrar(con, idReserva, idAnterior, idNueva, usuario,
                        reserva.getMontoTotal(), totalNuevo, textoOpcional(motivo));
                auditoriaDAO.registrar(con, usuario, "CAMBIO_HABITACION", "reserva", idReserva,
                        anterior.getNumero() + " → " + nueva.getNumero());
                con.commit();
                return new ResultadoCambio(totalNuevo, pagado, totalNuevo.subtract(pagado));
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    private boolean esMismoTipoOSuperior(Connection con, Habitacion anterior, Habitacion nueva) throws SQLException {
        if (anterior.getIdTipo() == nueva.getIdTipo()) return true;
        Integer actual = habitacionDAO.nivelCategoria(con, anterior.getIdTipo());
        Integer destino = habitacionDAO.nivelCategoria(con, nueva.getIdTipo());
        return actual != null && destino != null && destino > actual;
    }

    /** Captura una estadía ya concluida sin falsificar fecha_reserva ni fecha_pago. */
    public int registrarHistorica(List<Huesped> huespedes, Empresa empresa, Reserva reserva,
                                  Pago pago, LocalDate fechaEvento, LocalDate fechaPagoReal,
                                  String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        validarHuespedes(huespedes, reserva);
        validarEmpresa(empresa);
        if (reserva.getFechaCheckin() == null || reserva.getFechaCheckout() == null
                || !reserva.getFechaCheckout().isAfter(reserva.getFechaCheckin())
                || reserva.getFechaCheckout().isAfter(LocalDate.now())
                || reserva.getFechaCheckin().isAfter(LocalDate.now().minusDays(1))) {
            throw new IllegalArgumentException("La estadía histórica debe haber empezado antes de hoy y haber concluido.");
        }
        if (fechaEvento == null || fechaEvento.isAfter(LocalDate.now())
                || fechaPagoReal == null || fechaPagoReal.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Las fechas reales no pueden ser futuras.");
        }
        if (motivo == null || motivo.isBlank() || motivo.length() > 300) {
            throw new IllegalArgumentException("Indique el motivo de registro tardío (máximo 300 caracteres).");
        }
        if (reserva.getMontoTotal() == null || reserva.getMontoTotal().signum() <= 0
                || reserva.getMontoTotal().scale() > 2) {
            throw new IllegalArgumentException("El total histórico no es válido.");
        }
        validarPagos(List.of(pago), reserva.getMontoTotal(), true);
        int usuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                reservaDAO.bloquearHabitacion(con, reserva.getIdHabitacion());
                int capacidad = reservaDAO.obtenerCapacidadHabitacion(con, reserva.getIdHabitacion());
                if (capacidad <= 0 || huespedes.size() > capacidad)
                    throw new IllegalArgumentException("La habitación no admite a todos los huéspedes.");
                if (reservaDAO.existeCruceHistorico(con, reserva.getIdHabitacion(),
                        reserva.getFechaCheckin(), reserva.getFechaCheckout()))
                    throw new ConflictoFechasException("Ya existe una estadía para esa habitación y fechas.");
                reserva.setIdHuesped(obtenerOCrearHuesped(con, huespedes.get(0)));
                reserva.setIdUsuario(usuario);
                reserva.setEstado("FINALIZADA");
                reserva.setAdelanto(pago.getMonto());
                reserva.setIdEmpresa(empresa == null ? null : empresaDAO.guardar(con, empresa));
                int idReserva = reservaDAO.insertar(con, reserva);
                asociarHuespedes(con, idReserva, reserva.getIdHuesped(), huespedes);
                reservaDAO.marcarRegistroRetroactivo(con, idReserva, fechaEvento, motivo.trim(), usuario);
                pago.setIdReserva(idReserva);
                pago.setIdUsuario(usuario);
                pago.setTipoPago("COMPLETO");
                pagoDAO.insertarRetroactivo(con, pago, fechaPagoReal, motivo.trim(), usuario);
                auditoriaDAO.registrar(con, usuario, "RESERVA_RETROACTIVA", "reserva", idReserva,
                        "Hecho: " + fechaEvento + "; pago: " + fechaPagoReal + "; " + motivo.trim());
                con.commit();
                return idReserva;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    public void hacerCheckIn(int idHabitacion) throws SQLException {
        obtenerIdUsuarioAutenticado();
        try (Connection conexion = ConexionBD.conectar()) {
            conexion.setAutoCommit(false);
            try {
                reservaDAO.bloquearHabitacion(conexion, idHabitacion);
                if (!"DISPONIBLE".equals(habitacionDAO.obtenerEstado(conexion, idHabitacion))) {
                    throw new IllegalStateException("La habitación no está disponible para recibir al huésped todavía.");
                }
                int idReserva = reservaDAO.buscarParaCheckin(conexion, idHabitacion, LocalDate.now());
                if (idReserva < 0 || !reservaDAO.actualizarEstadoSiEs(
                        conexion, idReserva, "CONFIRMADA", "CHECKIN")) {
                    throw new IllegalStateException("La habitación no tiene una reserva confirmada para hoy.");
                }
                habitacionDAO.cambiarEstado(conexion, idHabitacion, "OCUPADA");
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

    /** Registra una reserva confirmada y su pago, o un check-in inmediato, en una transacción. */
    public int registrar(Huesped huesped, Empresa empresa, Reserva reserva, List<Pago> pagos,
                         boolean checkinInmediato) throws SQLException {
        return registrar(List.of(huesped), empresa, reserva, pagos, checkinInmediato);
    }

    public int registrar(List<Huesped> huespedes, Empresa empresa, Reserva reserva, List<Pago> pagos,
                         boolean checkinInmediato) throws SQLException {
        validarHuespedes(huespedes, reserva);
        validarEmpresa(empresa);
        validarReserva(reserva, checkinInmediato, false);
        BigDecimal sumaPagos = validarPagos(pagos, reserva.getMontoTotal(), checkinInmediato);
        int idUsuario = obtenerIdUsuarioAutenticado();

        try (Connection conexion = ConexionBD.conectar()) {
            conexion.setAutoCommit(false);
            try {
                prepararReservaDentroDeTransaccion(conexion, reserva, checkinInmediato);
                reserva.setIdHuesped(obtenerOCrearHuesped(conexion, huespedes.get(0)));
                reserva.setIdUsuario(idUsuario);
                reserva.setEstado(checkinInmediato ? "CHECKIN" : "CONFIRMADA");
                reserva.setAdelanto(sumaPagos);
                reserva.setIdEmpresa(empresa == null ? null : empresaDAO.guardar(conexion, empresa));

                int idReserva = reservaDAO.insertar(conexion, reserva);
                asociarHuespedes(conexion, idReserva, reserva.getIdHuesped(), huespedes);
                boolean pagoCompleto = sumaPagos.compareTo(reserva.getMontoTotal()) == 0;
                for (Pago pago : pagos) {
                    pago.setIdReserva(idReserva);
                    pago.setIdUsuario(idUsuario);
                    pago.setTipoPago(pagoCompleto ? "COMPLETO" : "ADELANTO");
                    pagoDAO.insertar(conexion, pago);
                }
                if (checkinInmediato) {
                    habitacionDAO.cambiarEstado(conexion, reserva.getIdHabitacion(), "OCUPADA");
                }
                conexion.commit();
                return idReserva;
            } catch (SQLException | RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** Cancela solo si el estado persistido continúa siendo PENDIENTE o CONFIRMADA. */
    public void cancelar(int idReserva, String motivo, String detalle) throws SQLException {
        obtenerIdUsuarioAutenticado();
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Seleccione un motivo de cancelación.");
        }
        if (detalle != null && detalle.length() > 500) {
            throw new IllegalArgumentException("El detalle de cancelación no puede superar 500 caracteres.");
        }
        try (Connection conexion = ConexionBD.conectar()) {
            if (!reservaDAO.cancelarSiCancelable(conexion, idReserva, motivo.trim(), textoOpcional(detalle))) {
                throw new IllegalStateException("La reserva ya no está en un estado que permita cancelarla.");
            }
        }
    }

    public List<Reserva> buscar(String texto, String estado, LocalDate desde, LocalDate hasta) throws SQLException {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final del filtro no puede ser anterior a la inicial.");
        }
        try (Connection conexion = ConexionBD.conectar()) {
            return reservaDAO.buscar(conexion, texto, estado, desde, hasta);
        }
    }

    public Huesped buscarHuespedLocal(String tipoDocumento, String documento) throws SQLException {
        return huespedDAO.buscarPorDocumento(tipoDocumento, documento);
    }

    public List<Huesped> listarHuespedes(int idReserva) throws SQLException {
        try (Connection conexion = ConexionBD.conectar()) {
            return reservaHuespedDAO.listar(conexion, idReserva);
        }
    }

    /** Se invoca únicamente después de una respuesta RENIEC válida para el mismo DNI. */
    public Huesped reconciliarIdentidadDni(String dni, Huesped verificado) throws SQLException {
        if (!Validador.esDniValido(dni) || verificado == null
                || !"DNI".equals(verificado.getTipoDocumento())
                || !dni.equals(verificado.getNumDocumento())) {
            throw new IllegalArgumentException("La respuesta de identidad no corresponde al DNI consultado.");
        }
        if (!Validador.esNombreValido(verificado.getNombres())
                || !Validador.esNombreValido(verificado.getApellidos())) {
            throw new IllegalArgumentException("RENIEC devolvió una identidad incompleta.");
        }
        int usuario = obtenerIdUsuarioAutenticado();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Huesped local = huespedDAO.buscarPorDocumento(con, "DNI", dni);
                String antes = local == null ? "" : (local.getNombres() + " " + local.getApellidos()).trim();
                if (local != null && huespedDAO.actualizarIdentidadVerificada(con, local, verificado)) {
                    String detalle = "DNI terminado en " + dni.substring(4) + ". Antes: " + antes + ". Ahora: "
                            + (verificado.getNombres() + " " + verificado.getApellidos()).trim();
                    auditoriaDAO.registrar(con, usuario, "IDENTIDAD_RENIEC", "huesped", local.getIdHuesped(),
                            detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
                    local.setNombres(verificado.getNombres());
                    local.setApellidos(verificado.getApellidos());
                }
                con.commit();
                return local;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    public Empresa buscarEmpresaLocal(String ruc) throws SQLException {
        try (Connection conexion = ConexionBD.conectar()) {
            return empresaDAO.buscarPorRuc(conexion, ruc);
        }
    }

    public List<Integer> listarPisos() throws SQLException {
        try (Connection conexion = ConexionBD.conectar()) {
            return habitacionDAO.listarPisos(conexion);
        }
    }

    public List<String> listarTiposHabitacion() throws SQLException {
        try (Connection conexion = ConexionBD.conectar()) {
            return habitacionDAO.listarNombresTipo(conexion);
        }
    }

    public List<Habitacion> listarHabitaciones() throws SQLException {
        return habitacionDAO.listar();
    }

    /** Para acciones sensibles, un texto ambiguo obliga a introducir el código exacto. */
    public Reserva buscarUno(String texto) throws SQLException {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try (Connection conexion = ConexionBD.conectar()) {
            Reserva exacta = reservaDAO.buscarPorCodigo(conexion, texto);
            if (exacta != null) {
                return exacta;
            }
            List<Reserva> coincidencias = reservaDAO.buscar(conexion, texto, null, null, null);
            if (coincidencias.size() > 1) {
                throw new IllegalArgumentException("Se encontraron " + coincidencias.size()
                        + " reservas. Ingrese el código exacto, por ejemplo R-0005.");
            }
            return coincidencias.isEmpty() ? null : coincidencias.get(0);
        }
    }

    public List<Habitacion> buscarDisponibles(LocalDate ingreso, LocalDate salida,
                                               Integer piso, String tipoNombre) throws SQLException {
        validarRangoConsulta(ingreso, salida);
        try (Connection conexion = ConexionBD.conectar()) {
            return habitacionDAO.buscarDisponibles(conexion, ingreso, salida, piso, tipoNombre);
        }
    }

    public List<Reserva> listarEnRango(LocalDate desde, LocalDate hasta) throws SQLException {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango del calendario no es válido.");
        }
        try (Connection conexion = ConexionBD.conectar()) {
            return reservaDAO.listarEnRango(conexion, desde, hasta);
        }
    }

    private void prepararReservaDentroDeTransaccion(Connection conexion, Reserva reserva,
                                                      boolean checkinInmediato) throws SQLException {
        reservaDAO.bloquearHabitacion(conexion, reserva.getIdHabitacion());
        int capacidad = reservaDAO.obtenerCapacidadHabitacion(conexion, reserva.getIdHabitacion());
        if (capacidad <= 0) {
            throw new IllegalStateException("La habitación seleccionada ya no existe.");
        }
        if (reserva.getNumHuespedes() > capacidad) {
            throw new IllegalArgumentException("La habitación admite como máximo " + capacidad + " huésped(es).");
        }
        if (checkinInmediato
                && !"DISPONIBLE".equals(habitacionDAO.obtenerEstado(conexion, reserva.getIdHabitacion()))) {
            throw new IllegalStateException("La habitación ya no está disponible. Actualice la lista.");
        }
        if (reservaDAO.existeCruce(conexion, reserva.getIdHabitacion(),
                reserva.getFechaCheckin(), reserva.getFechaCheckout())) {
            throw new ConflictoFechasException("La habitación ya tiene una reserva vigente en esas fechas.");
        }
    }

    private int obtenerOCrearHuesped(Connection conexion, Huesped huesped) throws SQLException {
        Huesped existente = huespedDAO.buscarPorDocumento(
                conexion, huesped.getTipoDocumento(), huesped.getNumDocumento());
        if (existente != null) {
            // el nombre lo manda RENIEC; el pais, telefono y correo los puede corregir el recepcionista
            huespedDAO.actualizarContacto(conexion, existente.getIdHuesped(),
                    textoOpcional(huesped.getPaisProcedencia()), textoOpcional(huesped.getTelefono()),
                    textoOpcional(huesped.getEmail()));
            return existente.getIdHuesped();
        }
        try {
            return huespedDAO.insertar(conexion, huesped);
        } catch (SQLException error) {
            if (error.getSQLState() == null || !error.getSQLState().startsWith("23")) throw error;
            // Otra transacción pudo registrar el mismo documento entre SELECT e INSERT.
            Huesped concurrente = huespedDAO.buscarPorDocumento(
                    conexion, huesped.getTipoDocumento(), huesped.getNumDocumento(), true);
            if (concurrente == null) throw error;
            return concurrente.getIdHuesped();
        }
    }

    private void asociarHuespedes(Connection con, int idReserva, int idTitular,
                                  List<Huesped> huespedes) throws SQLException {
        reservaHuespedDAO.asociar(con, idReserva, idTitular, true);
        for (int i = 1; i < huespedes.size(); i++) {
            int id = obtenerOCrearHuesped(con, huespedes.get(i));
            reservaHuespedDAO.asociar(con, idReserva, id, false);
        }
    }

    private void validarHuespedes(List<Huesped> huespedes, Reserva reserva) {
        if (reserva == null || huespedes == null || huespedes.isEmpty()
                || huespedes.size() != reserva.getNumHuespedes()) {
            throw new IllegalArgumentException("Identifique a todas las personas que ingresarán a la habitación.");
        }
        Set<String> documentos = new HashSet<>();
        for (Huesped huesped : huespedes) {
            validarHuesped(huesped);
            String clave = huesped.getTipoDocumento() + ":" + huesped.getNumDocumento();
            if (!documentos.add(clave)) {
                throw new IllegalArgumentException("Una persona aparece dos veces en la reserva.");
            }
        }
    }

    private void validarHuesped(Huesped huesped) {
        if (huesped == null) {
            throw new IllegalArgumentException("Complete los datos del huésped.");
        }
        String tipoDocumento = huesped.getTipoDocumento();
        String documento = huesped.getNumDocumento();
        boolean documentoValido = "DNI".equals(tipoDocumento) ? Validador.esDniValido(documento)
                : "PASAPORTE".equals(tipoDocumento) && Validador.esPasaporteValido(documento);
        if (!documentoValido) {
            throw new IllegalArgumentException("El documento de identidad no es válido.");
        }
        if (!Validador.esNombreValido(huesped.getNombres())
                || !Validador.esNombreValido(huesped.getApellidos())) {
            throw new IllegalArgumentException("Los nombres y apellidos solo pueden tener letras (máximo 80 caracteres).");
        }
        if (!Validador.esPaisValido(huesped.getPaisProcedencia())) {
            throw new IllegalArgumentException("El país de procedencia no es válido.");
        }
        if (huesped.getTelefono() != null && !huesped.getTelefono().isBlank()
                && !Validador.esTelefonoValido(huesped.getTelefono())) {
            throw new IllegalArgumentException("El teléfono debe tener 9 dígitos y empezar con 9.");
        }
        if (huesped.getEmail() != null && !huesped.getEmail().isBlank()
                && !Validador.esEmailValido(huesped.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico no es válido.");
        }
    }

    private void validarEmpresa(Empresa empresa) {
        if (empresa == null) {
            return;
        }
        if (!Validador.esRucValido(empresa.getRuc())) {
            throw new IllegalArgumentException("El RUC de la empresa no es válido.");
        }
        if (empresa.getRazonSocial() == null || empresa.getRazonSocial().isBlank()
                || empresa.getRazonSocial().length() > 150) {
            throw new IllegalArgumentException("Ingrese una razón social válida.");
        }
        if (empresa.getDireccion() != null && empresa.getDireccion().length() > 200) {
            throw new IllegalArgumentException("La dirección fiscal no puede superar 200 caracteres.");
        }
    }

    private void validarReserva(Reserva reserva, boolean checkinInmediato, boolean pendiente) {
        if (reserva == null || reserva.getIdHabitacion() <= 0) {
            throw new IllegalArgumentException("Seleccione una habitación.");
        }
        LocalDate hoy = LocalDate.now();
        LocalDate ingreso = reserva.getFechaCheckin();
        LocalDate salida = reserva.getFechaCheckout();
        validarRangoConsulta(ingreso, salida);
        if (ingreso.isBefore(hoy)) {
            throw new IllegalArgumentException("La fecha de ingreso no puede ser anterior a hoy.");
        }
        if (pendiente && !ingreso.isAfter(hoy)) {
            throw new IllegalArgumentException("Una reserva pendiente debe tener ingreso a partir de mañana.");
        }
        if (ingreso.isAfter(hoy.plusDays(MAX_DIAS_ANTICIPACION))) {
            throw new IllegalArgumentException("Solo se puede reservar con hasta "
                    + MAX_DIAS_ANTICIPACION + " días de anticipación.");
        }
        if (ChronoUnit.DAYS.between(ingreso, salida) > MAX_NOCHES) {
            throw new IllegalArgumentException("La estadía no puede superar " + MAX_NOCHES + " noches.");
        }
        if (checkinInmediato != ingreso.equals(hoy)) {
            throw new IllegalArgumentException("El check-in inmediato solo es posible con ingreso hoy.");
        }
        if (reserva.getNumHuespedes() <= 0) {
            throw new IllegalArgumentException("La cantidad de huéspedes debe ser mayor a cero.");
        }
        if (reserva.getMontoTotal() == null || reserva.getMontoTotal().signum() <= 0) {
            throw new IllegalArgumentException("El total de la estadía no es válido.");
        }
    }

    private BigDecimal validarPagos(List<Pago> pagos, BigDecimal total, boolean checkinInmediato) {
        if (pagos == null || pagos.isEmpty() || pagos.size() > 2) {
            throw new IllegalArgumentException("Debe registrarse el pago en uno o dos métodos.");
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (Pago pago : pagos) {
            if (pago == null || pago.getMonto() == null || pago.getMonto().signum() <= 0
                    || pago.getMonto().scale() > 2) {
                throw new IllegalArgumentException("Cada monto debe ser mayor a cero y tener hasta dos decimales.");
            }
            validarMetodoPago(pago.getMetodoPago());
            suma = suma.add(pago.getMonto());
        }
        BigDecimal minimo = checkinInmediato ? total
                : total.multiply(PORCENTAJE_ADELANTO).setScale(2, RoundingMode.HALF_UP);
        if (suma.compareTo(minimo) < 0) {
            throw new IllegalArgumentException(checkinInmediato
                    ? "El check-in requiere el pago completo."
                    : "El adelanto debe ser al menos el 50 % del total.");
        }
        if (suma.compareTo(total) > 0) {
            throw new IllegalArgumentException("El pago no puede superar el total de la estadía.");
        }
        return suma;
    }

    private void validarMetodoPago(String metodo) {
        if (!METODOS_PAGO.contains(metodo)) {
            throw new IllegalArgumentException("Seleccione un método de pago válido.");
        }
    }

    private void validarRangoConsulta(LocalDate ingreso, LocalDate salida) {
        if (ingreso == null || salida == null || !salida.isAfter(ingreso)) {
            throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de ingreso.");
        }
    }

    private int obtenerIdUsuarioAutenticado() {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (SesionActual.getUsuario() == null || SesionActual.getUsuario().getIdUsuario() <= 0) {
            throw new IllegalStateException("No existe una sesión de usuario válida. Inicie sesión nuevamente.");
        }
        return SesionActual.getUsuario().getIdUsuario();
    }

    private BigDecimal valorMonetario(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
