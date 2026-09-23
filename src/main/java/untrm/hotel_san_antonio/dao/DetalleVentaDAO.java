package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.DetalleVenta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DetalleVentaDAO {

    /** Los productos de una venta, con el nombre ya resuelto (para mostrarlos, ej. en un comprobante). */
    public List<DetalleVenta> listarPorVenta(Connection con, int idVenta) throws SQLException {
        String sql = "SELECT d.id_detalle, d.id_venta, d.id_producto, d.cantidad, d.precio_unitario, d.subtotal, "
                + "p.nombre FROM detalle_venta d JOIN producto p ON p.id_producto = d.id_producto "
                + "WHERE d.id_venta = ?";
        List<DetalleVenta> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DetalleVenta d = new DetalleVenta();
                    d.setIdDetalle(rs.getInt("id_detalle"));
                    d.setIdVenta(rs.getInt("id_venta"));
                    d.setIdProducto(rs.getInt("id_producto"));
                    d.setCantidad(rs.getInt("cantidad"));
                    d.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                    d.setSubtotal(rs.getBigDecimal("subtotal"));
                    d.setNombreProducto(rs.getString("nombre"));
                    lista.add(d);
                }
            }
        }
        return lista;
    }

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
