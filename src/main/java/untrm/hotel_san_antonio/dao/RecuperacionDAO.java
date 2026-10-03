package untrm.hotel_san_antonio.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RecuperacionDAO {
    public record TokenPendiente(long id, String hash) {}

    public void anularPendientes(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE recuperacion_contrasena SET usado = NOW() WHERE id_usuario = ? AND usado IS NULL")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
    }

    public void insertar(Connection con, int idUsuario, String hash) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO recuperacion_contrasena (id_usuario, token_hash, expira) "
                + "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE))")) {
            ps.setInt(1, idUsuario);
            ps.setString(2, hash);
            ps.executeUpdate();
        }
    }

    public List<TokenPendiente> vigentes(Connection con, int idUsuario) throws SQLException {
        List<TokenPendiente> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_recuperacion, token_hash FROM recuperacion_contrasena "
                + "WHERE id_usuario = ? AND usado IS NULL AND expira > NOW() FOR UPDATE")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(new TokenPendiente(rs.getLong(1), rs.getString(2)));
            }
        }
        return lista;
    }

    public void usar(Connection con, long idToken) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE recuperacion_contrasena SET usado = NOW() WHERE id_recuperacion = ? AND usado IS NULL")) {
            ps.setLong(1, idToken);
            if (ps.executeUpdate() != 1) throw new SQLException("El token ya fue utilizado.");
        }
    }
}
