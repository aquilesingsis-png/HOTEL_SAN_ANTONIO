package untrm.hotel_san_antonio.modelo.dao;

import untrm.hotel_san_antonio.modelo.ItemInventario;
import untrm.hotel_san_antonio.modelo.Producto;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                    p.setNombreCategoria(rs.getString("categoria"));
                    p.setPrecio(rs.getBigDecimal("precio"));
                    p.setStock(rs.getInt("stock"));
                    p.setActivo(rs.getBoolean("activo"));
                    productos.add(p);
                }
            }
        }
        return productos;
    }

    /** Consulta el producto actual sin depender del filtro visible en la tienda. */
    public Producto buscarActivoPorId(int idProducto) throws SQLException {
        String sql = "SELECT id_producto, nombre, precio, stock FROM producto "
                + "WHERE id_producto = ? AND activo = 1";
        try (Connection cn = ConexionBD.conectar(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Producto producto = new Producto();
                producto.setIdProducto(rs.getInt("id_producto"));
                producto.setNombre(rs.getString("nombre"));
                producto.setPrecio(rs.getBigDecimal("precio"));
                producto.setStock(rs.getInt("stock"));
                return producto;
            }
        }
    }

    /** Lee stock y precio actuales dentro de la transacción de venta. */
    public Producto bloquearParaVenta(Connection cn, int idProducto) throws SQLException {
        String sql = "SELECT precio, stock FROM producto WHERE id_producto = ? AND activo = 1 FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El producto ya no está disponible.");
                Producto producto = new Producto();
                producto.setIdProducto(idProducto);
                producto.setPrecio(rs.getBigDecimal("precio"));
                producto.setStock(rs.getInt("stock"));
                return producto;
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

    /** Devuelve al stock lo que una venta anulada se habia llevado (aunque el producto ya este desactivado). */
    public void reponerStock(Connection cn, int idProducto, int cantidad) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("UPDATE producto SET stock = stock + ? WHERE id_producto = ?")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, idProducto);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo devolver el stock del producto.");
            }
        }
    }

    /** El stock y el nombre de un producto activo, tal como están ahora. */
    public record Existencia(int stock, String nombre) { }

    /** Todo el inventario, con el nombre de la categoría de cada producto. */
    public List<ItemInventario> listarInventario() throws SQLException {
        String sql = "SELECT p.id_producto,p.codigo_barra,p.nombre,COALESCE(p.marca,''),p.id_categoria,c.nombre,"
                + "p.precio,p.stock,p.activo FROM producto p JOIN categoria c ON c.id_categoria=p.id_categoria "
                + "ORDER BY p.nombre";
        List<ItemInventario> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new ItemInventario(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getString(6), rs.getBigDecimal(7), rs.getInt(8), rs.getBoolean(9)));
            }
        }
        return lista;
    }

    /** El precio actual, bloqueando la fila; null si el producto no existe. */
    public BigDecimal precioBloqueando(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT precio FROM producto WHERE id_producto=? FOR UPDATE")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    /** El nombre, bloqueando la fila; null si el producto no existe. */
    public String nombreBloqueando(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT nombre FROM producto WHERE id_producto=? FOR UPDATE")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Stock y nombre de un producto activo, bloqueando la fila; null si no existe o está inactivo. */
    public Existencia existenciaActivaBloqueando(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT stock, nombre FROM producto WHERE id_producto=? AND activo=1 FOR UPDATE")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Existencia(rs.getInt(1), rs.getString(2)) : null;
            }
        }
    }

    /** Cuántas veces se vendió el producto o tuvo movimientos de stock. */
    public int contarHistorial(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM detalle_venta WHERE id_producto=?) "
                        + "+ (SELECT COUNT(*) FROM movimiento_stock WHERE id_producto=?)")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Crea un producto y devuelve su número. */
    public int insertar(Connection con, String codigoBarra, String nombre, String marca, int idCategoria,
                        BigDecimal precio, int stock, boolean activo) throws SQLException {
        String sql = "INSERT INTO producto(codigo_barra,nombre,marca,id_categoria,precio,stock,activo) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigoBarra);
            ps.setString(2, nombre);
            ps.setString(3, marca);
            ps.setInt(4, idCategoria);
            ps.setBigDecimal(5, precio);
            ps.setInt(6, stock);
            ps.setBoolean(7, activo);
            if (ps.executeUpdate() != 1) throw new SQLException("No se guardó el producto.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se obtuvo el identificador generado.");
                return rs.getInt(1);
            }
        }
    }

    /** Cambia los datos de un producto, sin tocar su stock. */
    public void actualizar(Connection con, int idProducto, String codigoBarra, String nombre, String marca,
                           int idCategoria, BigDecimal precio, boolean activo) throws SQLException {
        String sql = "UPDATE producto SET codigo_barra=?,nombre=?,marca=?,id_categoria=?,precio=?,activo=? WHERE id_producto=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, codigoBarra);
            ps.setString(2, nombre);
            ps.setString(3, marca);
            ps.setInt(4, idCategoria);
            ps.setBigDecimal(5, precio);
            ps.setBoolean(6, activo);
            ps.setInt(7, idProducto);
            if (ps.executeUpdate() != 1) throw new SQLException("No se guardó el producto.");
        }
    }

    /** Borra un producto; devuelve false si ya no existía. */
    public boolean eliminar(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM producto WHERE id_producto=?")) {
            ps.setInt(1, idProducto);
            return ps.executeUpdate() == 1;
        }
    }

    /** Fija el stock de un producto. */
    public void fijarStock(Connection con, int idProducto, int stock) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE producto SET stock=? WHERE id_producto=?")) {
            ps.setInt(1, stock);
            ps.setInt(2, idProducto);
            ps.executeUpdate();
        }
    }
}
