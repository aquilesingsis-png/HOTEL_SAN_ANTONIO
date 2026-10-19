package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.CierreCajaDAO;
import untrm.hotel_san_antonio.dao.ComprobanteDAO;
import untrm.hotel_san_antonio.dao.CuentaDAO;
import untrm.hotel_san_antonio.dao.DetalleVentaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.ProductoDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.Comprobante;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.modelo.Producto;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class VentaService {

    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final DiasSinUsoService diasSinUso = new DiasSinUsoService();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final ComprobanteDAO comprobanteDAO = new ComprobanteDAO();
    private final CierreCajaDAO cierreDAO = new CierreCajaDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Lo que paso al anular una venta: cuantas unidades volvieron al stock, que comprobante se anulo con ella
     * (null si no tenia) y, si era un consumo de habitacion que el huesped ya habia pagado, cuanto se le debe devolver.
     */
    public record Anulacion(int idVenta, BigDecimal total, int unidades, String comprobanteAnulado,
                            BigDecimal aDevolver) { }

    public int registrarVenta(VentaTienda venta, List<DetalleVenta> detalles) throws SQLException {
        return guardar(venta, detalles, null, null);
    }

    /**
     * Venta de un día en que el sistema no se usó. Se guarda con la fecha real del hecho (la fecha de registro
     * no se toca), con el motivo y con quién la regularizó. Solo vale para ventas cobradas al momento.
     */
    public int registrarVentaPasada(VentaTienda venta, List<DetalleVenta> detalles, LocalDate fechaEvento,
                                    String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (fechaEvento == null || !fechaEvento.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Elija un día anterior a hoy.");
        }
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 300) {
            throw new IllegalArgumentException("Indique el motivo del registro tardío (máximo 300 caracteres).");
        }
        if (venta != null && venta.getIdHabitacion() != null) {
            throw new IllegalArgumentException("Las ventas cargadas a una habitación se registran con su estadía.");
        }
        return guardar(venta, detalles, fechaEvento, motivo.trim());
    }

    private int guardar(VentaTienda venta, List<DetalleVenta> detalles, LocalDate fechaEvento, String motivo)
            throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (venta == null) {
            throw new IllegalArgumentException("La venta es obligatoria.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("El carrito está vacío.");
        }
        if (venta.getIdUsuario() <= 0 || venta.getIdUsuario() != SesionActual.getUsuario().getIdUsuario()) {
            throw new IllegalArgumentException("Debe existir un usuario autenticado.");
        }

        BigDecimal total = BigDecimal.ZERO;
        Set<Integer> ids = new HashSet<>();
        for (DetalleVenta d : detalles) {
            if (d == null || d.getIdProducto() <= 0 || !ids.add(d.getIdProducto())) {
                throw new IllegalArgumentException("El carrito contiene productos inválidos o repetidos.");
            }
            if (d.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
            }
            if (d.getPrecioUnitario() == null || d.getPrecioUnitario().signum() <= 0
                    || d.getPrecioUnitario().scale() > 2) {
                throw new IllegalArgumentException("Precio inválido en el carrito.");
            }
            BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);
            total = total.add(subtotal);
        }
        if (total.compareTo(new BigDecimal("999999.99")) > 0) {
            throw new IllegalArgumentException("El total supera el máximo admitido para una venta.");
        }
        venta.setTotal(total);
        if (venta.getIdHabitacion() == null) {
            if (venta.getMetodoPago() == null
                    || !Set.of("EFECTIVO", "YAPE", "TRANSFERENCIA", "TARJETA").contains(venta.getMetodoPago())) {
                throw new IllegalArgumentException("Seleccione el medio de pago de la venta.");
            }
        } else {
            venta.setMetodoPago(null); // lo cargado a una habitacion se cobra despues, con la cuenta
        }

        try (Connection cn = ConexionBD.conectar()) {
            boolean autoCommitAnterior = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try {
                if (fechaEvento != null) {
                    diasSinUso.requerirDiaPendiente(cn, fechaEvento);
                }
                for (DetalleVenta d : detalles) {
                    Producto producto = productoDAO.bloquearParaVenta(cn, d.getIdProducto());
                    if (producto.getPrecio().compareTo(d.getPrecioUnitario()) != 0) {
                        throw new IllegalStateException("El precio de " + d.getNombreProducto()
                                + " cambió. Actualice el carrito antes de confirmar.");
                    }
                    if (producto.getStock() < d.getCantidad()) {
                        throw new SQLException("Stock insuficiente para " + d.getNombreProducto() +
                                ". Disponible: " + producto.getStock() + ".");
                    }
                }

                int idVenta = fechaEvento == null ? ventaDAO.insertar(cn, venta)
                        : ventaDAO.insertarRetroactiva(cn, venta, fechaEvento, motivo, venta.getIdUsuario());
                for (DetalleVenta d : detalles) {
                    d.setIdVenta(idVenta);
                    detalleDAO.insertar(cn, d);
                    productoDAO.descontarStock(cn, d.getIdProducto(), d.getCantidad());
                }

                cn.commit();
                cn.setAutoCommit(autoCommitAnterior);
                return idVenta;
            } catch (Exception ex) {
                cn.rollback();
                try { cn.setAutoCommit(autoCommitAnterior); } catch (SQLException ignored) {}
                if (ex instanceof SQLException) {
                    throw (SQLException) ex;
                }
                throw new SQLException(ex.getMessage(), ex);
            }
        }
    }

    /**
     * Anula una venta de tienda. No se borra: queda marcada con quien la anulo, cuando y por que; sus productos
     * vuelven al stock, deja de sumar en caja, reportes y cierres y, si tenia boleta o factura, tambien se anula.
     * Recepcion solo anula ventas registradas hoy; el administrador, cualquiera. Una venta cobrada que ya entro en
     * un cierre de caja confirmado no se puede anular (el cierre ya contó ese dinero).
     *
     * @throws IllegalStateException si la venta no se puede anular (el mensaje explica por que)
     */
    public Anulacion anular(int idVenta, String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 3 || razon.length() > 200) {
            throw new IllegalArgumentException("Indique el motivo de la anulación (entre 3 y 200 caracteres).");
        }
        int idUsuario = SesionActual.getUsuario().getIdUsuario();
        boolean admin = SesionActual.esAdministrador();
        try (Connection con = ConexionBD.conectar()) {
            // lectura previa, fuera de la transaccion: solo sirve para saber que hay que bloquear primero (la estadia)
            VentaTienda previa = ventaDAO.buscarPorId(con, idVenta, false);
            if (previa == null) {
                throw new IllegalStateException("No existe la venta N.º " + idVenta + ".");
            }
            con.setAutoCommit(false);
            boolean cierresTomados = false;
            try {
                // quien cierra la caja y quien anula una venta no se pisan: el cierre cuenta lo que ya esta confirmado
                cierreDAO.bloquearCierres(con);
                cierresTomados = true;

                // todas las operaciones bloquean en el mismo orden (estadia, ventas, comprobante) para no trabarse entre si
                int idReserva = -1;
                if (previa.getIdHabitacion() != null && previa.getIdHuesped() != null) {
                    idReserva = reservaDAO.bloquearEstadiaEnCurso(con, previa.getIdHabitacion(),
                            previa.getIdHuesped(), previa.getFechaVenta().toLocalDate());
                }
                VentaTienda venta = ventaDAO.buscarPorId(con, idVenta, true);
                if (venta == null || !Objects.equals(venta.getIdHabitacion(), previa.getIdHabitacion())) {
                    throw new IllegalStateException("La venta N.º " + idVenta
                            + " cambió mientras se anulaba. Vuelve a intentarlo.");
                }
                if (venta.isAnulada()) {
                    throw new IllegalStateException("La venta N.º " + idVenta + " ya está anulada.");
                }
                if (!admin && (venta.getFechaEvento() != null || !venta.getFechaVenta().toLocalDate().equals(LocalDate.now()))) {
                    throw new IllegalStateException("Recepción solo puede anular ventas registradas hoy. "
                            + "Esta venta la debe anular el administrador.");
                }

                BigDecimal aDevolver = BigDecimal.ZERO;
                String donde;
                if (venta.getIdHabitacion() == null) {
                    exigirFueraDeCierre(con, venta);
                    donde = venta.getMetodoPago();
                } else {
                    aDevolver = exigirConsumoPendiente(con, venta, idReserva, admin);
                    donde = "cargada a la hab. " + numeroHabitacion(con, venta.getIdHabitacion());
                }

                String comprobante = anularComprobanteDeLaVenta(con, venta, idUsuario, razon);
                if (!ventaDAO.anular(con, idVenta, idUsuario, razon)) {
                    throw new IllegalStateException("La venta N.º " + idVenta + " ya está anulada.");
                }
                int unidades = 0;
                for (DetalleVenta d : detalleDAO.listarPorVenta(con, idVenta)) {
                    productoDAO.reponerStock(con, d.getIdProducto(), d.getCantidad());
                    unidades += d.getCantidad();
                }
                auditoriaDAO.registrar(con, idUsuario, "VENTA_ANULAR", "venta_tienda", idVenta,
                        "venta N.º " + idVenta + " · S/ " + CuentaHabitacion.formato(venta.getTotal()) + " · " + donde
                        + " · " + unidades + (unidades == 1 ? " unidad vuelve" : " unidades vuelven") + " al stock"
                        + (comprobante == null ? "" : " · comprobante " + comprobante + " anulado")
                        + (aDevolver.signum() > 0 ? " · a devolver al huésped S/ " + CuentaHabitacion.formato(aDevolver) : "")
                        + " · motivo: " + razon);
                con.commit();
                return new Anulacion(idVenta, venta.getTotal(), unidades, comprobante, aDevolver);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                if (cierresTomados) {
                    cierreDAO.liberarCierres(con);
                }
            }
        }
    }

    /** Una venta cobrada que ya entro en un cierre de caja confirmado no se puede anular. */
    private void exigirFueraDeCierre(Connection con, VentaTienda venta) throws SQLException {
        LocalDateTime ultimoCierre = cierreDAO.ultimaConfirmacion(con);
        if (ultimoCierre != null && venta.getFechaVenta().isBefore(ultimoCierre)) {
            throw new IllegalStateException("Esta venta ya está dentro del cierre de caja confirmado el "
                    + ultimoCierre.format(FECHA_HORA) + " y no se puede anular. Si fue un error, el administrador "
                    + "puede corregirlo con un movimiento de caja.");
        }
    }

    /**
     * Un consumo cargado a una habitacion solo se anula mientras la estadia siga en curso y el consumo no este en
     * el comprobante de la estadia.
     *
     * @param idReserva la estadia en curso, ya bloqueada (nadie le emite el comprobante ni la cierra mientras se
     *                  anula el consumo), o -1 si ya no hay
     * @return cuanto de este consumo el huesped ya habia pagado con la cuenta (se le debe devolver); solo el
     *         administrador puede anular un consumo asi
     */
    private BigDecimal exigirConsumoPendiente(Connection con, VentaTienda venta, int idReserva, boolean admin)
            throws SQLException {
        if (idReserva < 0) {
            throw new IllegalStateException("La estadía de la habitación " + numeroHabitacion(con, venta.getIdHabitacion())
                    + " en la que se hizo este consumo ya terminó: se cobró con la cuenta de la habitación y ya no se puede anular.");
        }
        if (venta.getIdComprobante() != null) {
            Comprobante c = comprobanteDAO.buscarPorId(con, venta.getIdComprobante());
            throw new IllegalStateException("Este consumo ya está en el comprobante " + (c == null ? "" : c.getNumero() + " ")
                    + "de la estadía. Primero hay que anular ese comprobante (solo el administrador).");
        }
        CuentaHabitacion cuenta = cuentaDAO.obtener(con, venta.getIdHabitacion());
        if (cuenta == null || cuenta.getIdReserva() != idReserva) {
            throw new IllegalStateException("La estadía cambió. Vuelve a cargar la lista y reintenta.");
        }
        // lo que falta por pagar de la cuenta cubre primero a este consumo; el resto ya lo habia pagado el huesped
        BigDecimal pendiente = cuenta.getSaldo().max(BigDecimal.ZERO);
        BigDecimal pagado = venta.getTotal().subtract(pendiente).max(BigDecimal.ZERO).min(venta.getTotal());
        if (pagado.signum() > 0 && !admin) {
            throw new IllegalStateException("El huésped ya pagó este consumo con la cuenta de la habitación. "
                    + "Solo el administrador puede anularlo (y debe devolver el dinero al huésped).");
        }
        return pagado;
    }

    /** Si la venta tenia boleta o factura (emitida al venderla), se anula junto con ella. @return su numero, o null */
    private String anularComprobanteDeLaVenta(Connection con, VentaTienda venta, int idUsuario, String razon)
            throws SQLException {
        if (venta.getIdComprobante() == null || venta.getIdHabitacion() != null) {
            return null;
        }
        comprobanteDAO.bloquear(con, venta.getIdComprobante());
        Comprobante c = comprobanteDAO.buscarPorId(con, venta.getIdComprobante());
        if (c == null || c.isAnulado()) {
            return null;
        }
        String motivo = abreviar("Se anuló la venta N.º " + venta.getIdVenta() + ": " + razon, 200);
        if (!comprobanteDAO.anular(con, c.getIdComprobante(), idUsuario, motivo)) {
            return null;
        }
        auditoriaDAO.registrar(con, idUsuario, "COMPROBANTE_ANULAR", "comprobante", c.getIdComprobante(),
                c.getNumero() + " · S/ " + CuentaHabitacion.formato(c.getMontoTotal()) + " · " + motivo);
        return c.getNumero();
    }

    private String numeroHabitacion(Connection con, int idHabitacion) throws SQLException {
        Habitacion h = habitacionDAO.buscarPorId(con, idHabitacion);
        return h == null ? String.valueOf(idHabitacion) : h.getNumero();
    }

    private static String abreviar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
