package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Categoria;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
}
