package untrm.hotel_san_antonio.servicio;

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
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.SesionActual;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Genera el comprobante (boleta/nota de venta/factura) de una estadia que hizo check-in, o de una
 * venta de tienda que todavia no tiene comprobante. No cambia el estado de la reserva/habitacion:
 * eso lo hace el check-out por separado (son dos pasos distintos, como en cualquier hotel real).
 */
public class ComprobanteService {

    private static final int USUARIO_POR_DEFECTO = 2;

    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAO();
    private final ComprobanteDAO comprobanteDAO = new ComprobanteDAO();

    /**
     * Busca la cuenta de una estadia activa (CHECKIN) por codigo de reserva ("R-0005") o por
     * numero de habitacion ("207").
     * @return la cuenta, o null si no se encontro la habitacion / no hay una estadia activa alli
     */
    public CuentaHabitacion cargarCuentaPorReferencia(String referencia) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            Integer idHabitacion = resolverIdHabitacion(con, referencia);
            if (idHabitacion == null) {
                return null;
            }
            return cuentaDAO.obtener(con, idHabitacion);
        }
    }

    private Integer resolverIdHabitacion(Connection con, String referencia) throws SQLException {
        String limpio = referencia.trim();

        // Primero se prueba como codigo/nombre/documento de una reserva (ej. "R-0005", "RSV-005", "5")
        var reserva = reservaDAO.buscarUno(con, limpio);
        if (reserva != null) {
            return reserva.getIdHabitacion();
        }

        // Si no matcheo ninguna reserva, se prueba como numero de habitacion (ej. "207")
        Habitacion h = habitacionDAO.buscarPorNumero(con, limpio);
        return h == null ? null : h.getIdHabitacion();
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

    /** @return la venta y su detalle, o null si no existe esa venta o ya tiene comprobante */
    public VentaPendiente cargarVentaPorReferencia(String referencia) throws SQLException {
        String soloDigitos = referencia.trim().replaceAll("\\D", "");
        if (soloDigitos.isEmpty()) {
            return null;
        }
        int idVenta = Integer.parseInt(soloDigitos);
        try (Connection con = ConexionBD.conectar()) {
            VentaTienda venta = ventaDAO.buscarPendientePorId(con, idVenta);
            if (venta == null) {
                return null;
            }
            List<DetalleVenta> detalle = detalleDAO.listarPorVenta(con, idVenta);
            return new VentaPendiente(venta, detalle);
        }
    }

    /**
     * Genera el comprobante de una estadia y enlaza a el los consumos de tienda que quedaron
     * pendientes durante la estadia.
     * @throws IllegalStateException si todavia hay saldo pendiente
     */
    public Comprobante generarParaEstadia(CuentaHabitacion cuenta, String tipo) throws SQLException {
        if (cuenta.getSaldo().signum() > 0) {
            throw new IllegalStateException("Aún hay un saldo pendiente de S/ "
                    + CuentaHabitacion.formato(cuenta.getSaldo()) + ". Registra el pago antes de generar el comprobante.");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Comprobante c = new Comprobante();
                c.setIdReserva(cuenta.getIdReserva());
                c.setIdUsuario(idUsuario());
                c.setTipo(tipo);
                c.setNumero(comprobanteDAO.generarNumero(con, tipo));
                c.setMontoTotal(cuenta.getTotalCuenta());
                int idComprobante = comprobanteDAO.insertar(con, c);
                c.setIdComprobante(idComprobante);

                ventaDAO.enlazarComprobantePorEstadia(con, cuenta.getIdHabitacion(), cuenta.getIdHuesped(),
                        cuenta.getIngreso(), idComprobante);

                con.commit();
                return c;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Genera el comprobante de una venta de tienda que no estaba ligada a ninguna habitacion. */
    public Comprobante generarParaVenta(VentaTienda venta, String tipo) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Comprobante c = new Comprobante();
                c.setIdReserva(null);
                c.setIdUsuario(idUsuario());
                c.setTipo(tipo);
                c.setNumero(comprobanteDAO.generarNumero(con, tipo));
                c.setMontoTotal(venta.getTotal());
                int idComprobante = comprobanteDAO.insertar(con, c);
                c.setIdComprobante(idComprobante);

                ventaDAO.enlazarComprobante(con, venta.getIdVenta(), idComprobante);

                con.commit();
                return c;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private int idUsuario() {
        return SesionActual.getUsuario() != null ? SesionActual.getUsuario().getIdUsuario() : USUARIO_POR_DEFECTO;
    }
}
