package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Producto;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAO {

    public List<Producto> buscarActivos(String texto, Integer idCategoria) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id_producto, p.codigo_barra, p.nombre, p.marca, p.id_categoria, c.nombre AS categoria, p.precio, p.stock, p.activo " +
                "FROM producto p INNER JOIN categoria c ON c.id_categoria = p.id_categoria WHERE p.activo = 1 ");

        List<Object> parametros = new ArrayList<>();
        String filtro = texto == null ? "" : texto.trim();

        if (!filtro.isEmpty()) {
            sql.append("AND (LOWER(p.nombre) LIKE ? OR LOWER(COALESCE(p.marca,'')) LIKE ?) ");
            String like = "%" + filtro.toLowerCase() + "%";
            parametros.add(like);
            parametros.add(like);
        }
        if (idCategoria != null) {
            sql.append("AND p.id_categoria = ? ");
            parametros.add(idCategoria);
        }
        sql.append("ORDER BY p.nombre");

        List<Producto> productos = new ArrayList<>();
        try (Connection cn = ConexionBD.conectar();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Producto p = new Producto();
                    p.setIdProducto(rs.getInt("id_producto"));
                    p.setCodigoBarra(rs.getString("codigo_barra"));
                    p.setNombre(rs.getString("nombre"));
                    p.setMarca(rs.getString("marca"));
                    p.setIdCategoria(rs.getInt("id_categoria"));
                    p.setNombre(rs.getString("categoria"));
                    p.setPrecio(rs.getBigDecimal("precio"));
                    p.setStock(rs.getInt("stock"));
                    p.setActivo(rs.getBoolean("activo"));
                    productos.add(p);
                }
            }
        }
        return productos;
    }

    public int obtenerStockParaActualizar(Connection cn, int idProducto) throws SQLException {
        String sql = "SELECT stock FROM producto WHERE id_producto = ? AND activo = 1 FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("El producto ya no está disponible.");
                }
                return rs.getInt("stock");
            }
        }
    }

    public void descontarStock(Connection cn, int idProducto, int cantidad) throws SQLException {
        String sql = "UPDATE producto SET stock = stock - ? WHERE id_producto = ? AND stock >= ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setInt(2, idProducto);
            ps.setInt(3, cantidad);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo actualizar el stock del producto.");
            }
        }
    }
}
