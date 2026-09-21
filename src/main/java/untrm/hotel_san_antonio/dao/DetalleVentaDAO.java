package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.DetalleVenta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DetalleVentaDAO {

    public void insertar(Connection cn, DetalleVenta detalle) throws SQLException {
        String sql = "INSERT INTO detalle_venta " +
                "(id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, detalle.getIdVenta());
            ps.setInt(2, detalle.getIdProducto());
            ps.setInt(3, detalle.getCantidad());
            ps.setBigDecimal(4, detalle.getPrecioUnitario());
            ps.setBigDecimal(5, detalle.getSubtotal());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo registrar un detalle de la venta.");
            }
        }
    }
}
