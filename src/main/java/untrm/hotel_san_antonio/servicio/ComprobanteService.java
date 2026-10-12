package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.ComprobanteDAO;
import untrm.hotel_san_antonio.dao.CuentaDAO;
import untrm.hotel_san_antonio.dao.DetalleVentaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.Comprobante;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Set;

/**
 * Genera el comprobante (boleta/nota de venta/factura) de una estadia que hizo check-in, o de una
 * venta de tienda que todavia no tiene comprobante. No cambia el estado de la reserva/habitacion:
 * eso lo hace el check-out por separado (son dos pasos distintos, como en cualquier hotel real).
 */
public class ComprobanteService {

    private static final Set<String> TIPOS = Set.of("BOLETA", "FACTURA", "NOTA_VENTA");
    private static final int INTENTOS_NUMERO = 5;

    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAO();
    private final ComprobanteDAO comprobanteDAO = new ComprobanteDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

    /** Lo que se hace dentro de la transaccion de una emision. */
    @FunctionalInterface
    private interface Emision {
        Comprobante emitir(Connection con) throws SQLException;
    }

    /**
     * Busca la cuenta de una estadia activa (CHECKIN) por numero de habitacion ("207"), codigo de
     * reserva ("R-0005") o documento/nombre del huesped. Si el texto es a la vez un numero de habitacion
     * con estadia activa y el numero de otra reserva, gana la habitacion.
     * @return la cuenta, o null si no hay una estadia activa que corresponda
     */
    public CuentaHabitacion cargarCuentaPorReferencia(String referencia) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        String limpio = referencia == null ? "" : referencia.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        try (Connection con = ConexionBD.conectar()) {
            Habitacion habitacion = habitacionDAO.buscarPorNumero(con, limpio);
            if (habitacion != null) {
                CuentaHabitacion cuenta = cuentaDAO.obtener(con, habitacion.getIdHabitacion());
                if (cuenta != null) {
                    return cuenta;
                }
            }
            Reserva porCodigo = reservaDAO.buscarPorCodigo(con, limpio);
            if (porCodigo != null) {
                return "CHECKIN".equals(porCodigo.getEstado())
                        ? cuentaDAO.obtener(con, porCodigo.getIdHabitacion()) : null;
            }
            List<Reserva> enCurso = reservaDAO.buscar(con, limpio, "CHECKIN", null, null);
            return enCurso.isEmpty() ? null : cuentaDAO.obtener(con, enCurso.get(0).getIdHabitacion());
        }
    }

    /** Una venta de tienda pendiente (sin comprobante todavia), junto con sus productos. */
    public static class VentaPendiente {
        public final VentaTienda venta;
        public final List<DetalleVenta> detalle;
        public VentaPendiente(VentaTienda venta, List<DetalleVenta> detalle) {
            this.venta = venta;
            this.detalle = detalle;
        }
    }

    /**
     * @return la venta y su detalle, o null si no existe esa venta o ya tiene comprobante
     * @throws IllegalStateException si la venta esta cargada a una habitacion (se factura con la estadia)
     */
    public VentaPendiente cargarVentaPorReferencia(String referencia) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        String soloDigitos = referencia == null ? "" : referencia.trim().replaceAll("\\D", "");
        if (soloDigitos.isEmpty() || soloDigitos.length() > 9) {
            return null;
        }
        int idVenta = Integer.parseInt(soloDigitos);
        try (Connection con = ConexionBD.conectar()) {
            VentaTienda venta = ventaDAO.buscarPendientePorId(con, idVenta);
            if (venta == null) {
                return null;
            }
            if (venta.getIdHabitacion() != null) {
                Habitacion cargada = habitacionDAO.buscarPorId(con, venta.getIdHabitacion());
                throw new IllegalStateException("La venta N.º " + idVenta + " está cargada a la habitación"
                        + (cargada == null ? "" : " " + cargada.getNumero())
                        + " y se factura junto con su estadía. Búscala con el origen «Cuenta de habitación».");
            }
            List<DetalleVenta> detalle = detalleDAO.listarPorVenta(con, idVenta);
            return new VentaPendiente(venta, detalle);
        }
    }

    /**
     * Genera el comprobante de una estadia y enlaza a el los consumos de tienda que quedaron
     * pendientes durante la estadia. Cada estadia tiene un solo comprobante, y la cuenta (total y saldo)
     * se vuelve a armar desde la base: lo que mostraba la pantalla pudo haber cambiado.
     * @throws IllegalStateException si todavia hay saldo pendiente o la estadia ya tiene comprobante
     */
    public Comprobante generarParaEstadia(CuentaHabitacion cuenta, String tipo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        validarTipo(tipo);
        if (cuenta == null) {
            throw new IllegalArgumentException("Cargue primero la cuenta de la habitación.");
        }
        int idUsuario = SesionActual.getUsuario().getIdUsuario();
        return emitirEnTransaccion(tipo, con -> {
            // la reserva queda bloqueada: dos personas no pueden emitir a la vez el comprobante de una estadia
            Reserva reserva = reservaDAO.buscarPorId(con, cuenta.getIdReserva(), true);
            if (reserva == null || !"CHECKIN".equals(reserva.getEstado())) {
                throw new IllegalStateException("La estadía ya no está en curso. Vuelve a cargar la operación.");
            }
            String existente = comprobanteDAO.numeroDeReserva(con, reserva.getIdReserva());
            if (existente != null) {
                throw new IllegalStateException("Esta estadía ya tiene el comprobante " + existente
                        + ". No se emite otro.");
            }
            CuentaHabitacion real = cuentaDAO.obtener(con, reserva.getIdHabitacion());
            if (real == null || real.getIdReserva() != reserva.getIdReserva()) {
                throw new IllegalStateException("La cuenta cambió. Vuelve a cargar la operación.");
            }
            if (real.getSaldo().signum() > 0) {
                throw new IllegalStateException("Aún hay un saldo pendiente de S/ "
                        + CuentaHabitacion.formato(real.getSaldo()) + ". Registra el pago antes de generar el comprobante.");
            }
            Comprobante c = new Comprobante();
            c.setIdReserva(reserva.getIdReserva());
            c.setIdUsuario(idUsuario);
            c.setTipo(tipo);
            c.setMontoTotal(real.getTotalCuenta());
            int idComprobante = insertarConNumero(con, c);

            ventaDAO.enlazarComprobantePorEstadia(con, real.getIdHabitacion(), real.getIdHuesped(),
                    real.getIngreso(), idComprobante);
            auditoriaDAO.registrar(con, idUsuario, "COMPROBANTE_EMITIR", "comprobante", idComprobante,
                    c.getNumero() + " · estadía R-" + String.format("%04d", reserva.getIdReserva())
                    + " · S/ " + CuentaHabitacion.formato(c.getMontoTotal()));
            return c;
        });
    }

    /** Genera el comprobante de una venta de tienda que no estaba ligada a ninguna habitacion. */
    public Comprobante generarParaVenta(VentaTienda venta, String tipo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        validarTipo(tipo);
        if (venta == null) {
            throw new IllegalArgumentException("Cargue primero la venta.");
        }
        int idUsuario = SesionActual.getUsuario().getIdUsuario();
        return emitirEnTransaccion(tipo, con -> {
            // el total sale de la base y no de lo que trae la pantalla
            VentaTienda real = ventaDAO.buscarPendientePorId(con, venta.getIdVenta(), true);
            if (real == null) {
                throw new IllegalStateException("La venta no existe o ya tiene comprobante.");
            }
            if (real.getIdHabitacion() != null) {
                throw new IllegalStateException("La venta está cargada a una habitación: se factura junto con su estadía.");
            }
            Comprobante c = new Comprobante();
            c.setIdReserva(null);
            c.setIdUsuario(idUsuario);
            c.setTipo(tipo);
            c.setMontoTotal(real.getTotal());
            int idComprobante = insertarConNumero(con, c);

            if (!ventaDAO.enlazarComprobante(con, real.getIdVenta(), idComprobante)) {
                throw new IllegalStateException("La venta ya tiene comprobante o está cargada a una habitación.");
            }
            auditoriaDAO.registrar(con, idUsuario, "COMPROBANTE_EMITIR", "comprobante", idComprobante,
                    c.getNumero() + " · venta N.º " + real.getIdVenta()
                    + " · S/ " + CuentaHabitacion.formato(c.getMontoTotal()));
            return c;
        });
    }

    private void validarTipo(String tipo) {
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Seleccione el tipo de comprobante.");
        }
    }

    /** Ejecuta la emision en una transaccion y suelta al final el turno de la serie. */
    private Comprobante emitirEnTransaccion(String tipo, Emision emision) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Comprobante c = emision.emitir(con);
                con.commit();
                return c;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                comprobanteDAO.liberarSerie(con, tipo);
            }
        }
    }

    /**
     * Toma el siguiente numero de la serie. Las emisiones de un mismo tipo se hacen de una en una (aunque
     * sean desde dos PCs); si aun asi otra se adelantara con el mismo numero, se pide el siguiente.
     */
    private int insertarConNumero(Connection con, Comprobante c) throws SQLException {
        comprobanteDAO.bloquearSerie(con, c.getTipo());
        for (int intento = 1; ; intento++) {
            c.setNumero(comprobanteDAO.generarNumero(con, c.getTipo()));
            try {
                return comprobanteDAO.insertar(con, c);
            } catch (SQLIntegrityConstraintViolationException duplicado) {
                if (intento >= INTENTOS_NUMERO) {
                    throw duplicado;
                }
            }
        }
    }
}
