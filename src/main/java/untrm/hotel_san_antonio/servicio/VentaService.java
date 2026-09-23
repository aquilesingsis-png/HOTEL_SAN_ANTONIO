package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.DetalleVentaDAO;
import untrm.hotel_san_antonio.dao.ProductoDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class VentaService {

    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    public int registrarVenta(VentaTienda venta, List<DetalleVenta> detalles) throws SQLException {
        if (venta == null) {
            throw new IllegalArgumentException("La venta es obligatoria.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("El carrito está vacío.");
        }
        if (venta.getIdUsuario() <= 0) {
            throw new IllegalArgumentException("Debe existir un usuario autenticado.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta d : detalles) {
            if (d.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
            }
            if (d.getPrecioUnitario() == null || d.getPrecioUnitario().signum() < 0) {
                throw new IllegalArgumentException("Precio inválido en el carrito.");
            }
            BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);
            total = total.add(subtotal);
        }
        venta.setTotal(total);

        try (Connection cn = ConexionBD.conectar()) {
            boolean autoCommitAnterior = cn.getAutoCommit();
            cn.setAutoCommit(false);
            try {
                for (DetalleVenta d : detalles) {
                    int stock = productoDAO.obtenerStockParaActualizar(cn, d.getIdProducto());
                    if (stock < d.getCantidad()) {
                        throw new SQLException("Stock insuficiente para " + d.getNombreProducto() +
                                ". Disponible: " + stock + ".");
                    }
                }

                int idVenta = ventaDAO.insertar(cn, venta);
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
