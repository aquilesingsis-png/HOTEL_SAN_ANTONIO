package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.DetalleVentaDAO;
import untrm.hotel_san_antonio.dao.ProductoDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.modelo.Producto;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class VentaService {

    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final DiasSinUsoService diasSinUso = new DiasSinUsoService();

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
}
