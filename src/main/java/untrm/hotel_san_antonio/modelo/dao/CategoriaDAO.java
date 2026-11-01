package untrm.hotel_san_antonio.modelo.dao;

import untrm.hotel_san_antonio.modelo.Categoria;
import untrm.hotel_san_antonio.modelo.CategoriaResumen;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CategoriaDAO {

    public List<Categoria> listar() throws SQLException {
        String sql = "SELECT id_categoria, nombre FROM categoria ORDER BY nombre";
        List<Categoria> categorias = new ArrayList<>();

        try (Connection cn = ConexionBD.conectar();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                categorias.add(new Categoria(rs.getInt("id_categoria"), rs.getString("nombre")));
            }
        }
        return categorias;
    }

    /** Las categorías con la cantidad de productos de cada una. */
    public List<CategoriaResumen> listarConConteo() throws SQLException {
        String sql = "SELECT c.id_categoria,c.nombre,COUNT(p.id_producto) FROM categoria c "
                + "LEFT JOIN producto p ON p.id_categoria=c.id_categoria GROUP BY c.id_categoria,c.nombre "
                + "ORDER BY c.nombre";
        List<CategoriaResumen> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(new CategoriaResumen(rs.getInt(1), rs.getString(2), rs.getInt(3)));
        }
        return lista;
    }

    /** Crea una categoría y devuelve su número. */
    public int insertar(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO categoria(nombre) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            if (ps.executeUpdate() != 1) throw new SQLException("No se guardó la categoría.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se obtuvo el identificador generado.");
                return rs.getInt(1);
            }
        }
    }

    /** Cambia el nombre de una categoría; devuelve false si ya no existía. */
    public boolean renombrar(Connection con, int idCategoria, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE categoria SET nombre=? WHERE id_categoria=?")) {
            ps.setString(1, nombre);
            ps.setInt(2, idCategoria);
            return ps.executeUpdate() == 1;
        }
    }

    public int contarProductos(Connection con, int idCategoria) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM producto WHERE id_categoria=?")) {
            ps.setInt(1, idCategoria);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Borra una categoría; devuelve false si ya no existía. */
    public boolean eliminar(Connection con, int idCategoria) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM categoria WHERE id_categoria=?")) {
            ps.setInt(1, idCategoria);
            return ps.executeUpdate() == 1;
        }
    }
}
