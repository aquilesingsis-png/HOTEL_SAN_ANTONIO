package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/** Almacén: productos, precios, existencias y categorías (solo el Administrador). */
public class AlmacenDAO {

    public record Item(int id, String codigoBarra, String nombre, String marca, int idCategoria,
                       String categoria, BigDecimal precio, int stock, boolean activo) {
        /** Código corto que se ve en pantalla (P001). */
        public String codigo() {
            return String.format("P%03d", id);
        }
    }

    public record CategoriaFila(int id, String nombre, int productos) { }

    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999.99");

    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    private void admin() {
        Permisos.requerir("ADMINISTRADOR");
    }

    private int usuario() {
        return SesionActual.getUsuario().getIdUsuario();
    }

    // ------------------------------------------------------------------ consultas

    /** Todo el inventario; los filtros se aplican en pantalla porque son pocos productos. */
    public List<Item> listar() throws SQLException {
        admin();
        List<Item> lista = new ArrayList<>();
        String sql = "SELECT p.id_producto,p.codigo_barra,p.nombre,COALESCE(p.marca,''),p.id_categoria,c.nombre,"
                + "p.precio,p.stock,p.activo FROM producto p JOIN categoria c ON c.id_categoria=p.id_categoria "
                + "ORDER BY p.nombre";
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new Item(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getString(6), rs.getBigDecimal(7), rs.getInt(8), rs.getBoolean(9)));
            }
        }
        return lista;
    }

    public List<CategoriaFila> categorias() throws SQLException {
        admin();
        List<CategoriaFila> lista = new ArrayList<>();
        String sql = "SELECT c.id_categoria,c.nombre,COUNT(p.id_producto) FROM categoria c "
                + "LEFT JOIN producto p ON p.id_categoria=c.id_categoria GROUP BY c.id_categoria,c.nombre "
                + "ORDER BY c.nombre";
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(new CategoriaFila(rs.getInt(1), rs.getString(2), rs.getInt(3)));
        }
        return lista;
    }

    // ---------------------------------------------------------------- categorías

    public void guardarCategoria(Integer id, String nombre) throws SQLException {
        admin();
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 40)
            throw new IllegalArgumentException("La categoría debe tener entre 1 y 40 caracteres.");
        String sql = id == null ? "INSERT INTO categoria(nombre) VALUES (?)"
                : "UPDATE categoria SET nombre=? WHERE id_categoria=?";
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nombre.trim());
                if (id != null) ps.setInt(2, id);
                try {
                    if (ps.executeUpdate() != 1) throw new SQLException("No se guardó la categoría.");
                } catch (java.sql.SQLIntegrityConstraintViolationException repetida) {
                    throw new IllegalArgumentException("Ya existe una categoría con ese nombre.");
                }
                int afectado = id == null ? claveGenerada(ps) : id;
                auditoria.registrar(con, usuario(), id == null ? "CATEGORIA_CREAR" : "CATEGORIA_EDITAR",
                        "categoria", afectado, nombre.trim());
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    public void eliminarCategoria(int id) throws SQLException {
        admin();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM producto WHERE id_categoria=?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) throw new IllegalStateException(
                                "La categoría tiene productos asociados y no se puede eliminar.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM categoria WHERE id_categoria=?")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() != 1) throw new SQLException("La categoría ya no existe.");
                }
                auditoria.registrar(con, usuario(), "CATEGORIA_ELIMINAR", "categoria", id, "Sin productos asociados");
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    // ----------------------------------------------------------------- productos

    /**
     * Crea (id nulo) o edita un producto. Al editar no se toca el stock: eso se hace con {@link #moverStock}.
     * Si cambia el precio queda anotado en la auditoría con el valor anterior.
     */
    public void guardarProducto(Integer id, String codigoBarra, String nombre, String marca, int idCategoria,
                                BigDecimal precio, int stockInicial, boolean activo) throws SQLException {
        admin();
        if (codigoBarra == null || !codigoBarra.trim().matches("[A-Za-z0-9-]{3,20}"))
            throw new IllegalArgumentException("El código de barras debe tener 3 a 20 caracteres (letras, números o guion).");
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120)
            throw new IllegalArgumentException("Ingrese un nombre de producto de hasta 120 caracteres.");
        if (marca != null && marca.trim().length() > 60)
            throw new IllegalArgumentException("La marca supera 60 caracteres.");
        validarPrecio(precio);
        if (stockInicial < 0) throw new IllegalArgumentException("El stock no puede ser negativo.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                BigDecimal anterior = null;
                if (id != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT precio FROM producto WHERE id_producto=? FOR UPDATE")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalStateException("El producto ya no existe.");
                            anterior = rs.getBigDecimal(1);
                        }
                    }
                }
                String sql = id == null
                        ? "INSERT INTO producto(codigo_barra,nombre,marca,id_categoria,precio,stock,activo) VALUES (?,?,?,?,?,?,?)"
                        : "UPDATE producto SET codigo_barra=?,nombre=?,marca=?,id_categoria=?,precio=?,activo=? WHERE id_producto=?";
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, codigoBarra.trim());
                    ps.setString(2, nombre.trim());
                    ps.setString(3, marca == null || marca.isBlank() ? null : marca.trim());
                    ps.setInt(4, idCategoria);
                    ps.setBigDecimal(5, precio);
                    if (id == null) {
                        ps.setInt(6, stockInicial);
                        ps.setBoolean(7, activo);
                    } else {
                        ps.setBoolean(6, activo);
                        ps.setInt(7, id);
                    }
                    try {
                        if (ps.executeUpdate() != 1) throw new SQLException("No se guardó el producto.");
                    } catch (java.sql.SQLIntegrityConstraintViolationException repetido) {
                        throw new IllegalArgumentException("Ya existe un producto con ese código de barras.");
                    }
                    int afectado = id == null ? claveGenerada(ps) : id;
                    auditoria.registrar(con, usuario(), id == null ? "PRODUCTO_CREAR" : "PRODUCTO_EDITAR",
                            "producto", afectado, nombre.trim());
                    if (anterior != null && anterior.compareTo(precio) != 0) {
                        auditoria.registrar(con, usuario(), "PRODUCTO_PRECIO", "producto", afectado,
                                anterior + " → " + precio);
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.scale() > 2 || precio.signum() <= 0 || precio.compareTo(PRECIO_MAXIMO) > 0)
            throw new IllegalArgumentException("El precio debe estar entre S/ 0.01 y S/ 9999.99, con hasta dos decimales.");
    }

    // ------------------------------------------------------------------- existencias

    /**
     * Entrada y salida mueven unidades; ajuste fija el stock contado físicamente.
     * Queda un registro en movimiento_stock con el stock antes y después.
     */
    public void moverStock(int idProducto, String tipo, int cantidad, String motivo,
                           String referencia, String observacion) throws SQLException {
        admin();
        if (!List.of("ENTRADA", "SALIDA", "AJUSTE").contains(tipo))
            throw new IllegalArgumentException("Seleccione el tipo de movimiento.");
        if (cantidad <= 0)
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        if (motivo == null || motivo.isBlank() || motivo.length() > 100)
            throw new IllegalArgumentException("Indique un motivo válido.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                int anterior;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT stock FROM producto WHERE id_producto=? AND activo=1 FOR UPDATE")) {
                    ps.setInt(1, idProducto);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("El producto no existe o está inactivo.");
                        anterior = rs.getInt(1);
                    }
                }
                int nuevo = "AJUSTE".equals(tipo) ? cantidad : "ENTRADA".equals(tipo)
                        ? Math.addExact(anterior, cantidad) : anterior - cantidad;
                if (nuevo < 0) throw new IllegalArgumentException("No hay stock suficiente para la salida.");
                try (PreparedStatement ps = con.prepareStatement("UPDATE producto SET stock=? WHERE id_producto=?")) {
                    ps.setInt(1, nuevo);
                    ps.setInt(2, idProducto);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO movimiento_stock(id_producto,id_usuario,tipo,cantidad,stock_anterior,"
                        + "stock_resultante,motivo,referencia,observacion) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, idProducto);
                    ps.setInt(2, usuario());
                    ps.setString(3, tipo);
                    ps.setInt(4, cantidad);
                    ps.setInt(5, anterior);
                    ps.setInt(6, nuevo);
                    ps.setString(7, motivo.trim());
                    ps.setString(8, referencia == null || referencia.isBlank() ? null : referencia.trim());
                    ps.setString(9, observacion == null || observacion.isBlank() ? null : observacion.trim());
                    ps.executeUpdate();
                }
                auditoria.registrar(con, usuario(), "PRODUCTO_STOCK", "producto", idProducto,
                        anterior + " → " + nuevo + " (" + tipo + ")");
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private int claveGenerada(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) throw new SQLException("No se obtuvo el identificador generado.");
            return rs.getInt(1);
        }
    }
}
