package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsuarioDAO {

    public boolean hayUsuarios(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM usuario LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    public List<String> listarRoles(Connection con) throws SQLException {
        String sql = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'rol'";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("No se encontró la configuración de roles.");
            Matcher matcher = Pattern.compile("'([^']+)'").matcher(rs.getString(1));
            List<String> roles = new ArrayList<>();
            while (matcher.find()) roles.add(matcher.group(1));
            return roles;
        }
    }

    public int insertar(Connection con, Usuario usuario) throws SQLException {
        String sql = "INSERT INTO usuario (nombre, apellido, usuario, contrasena_hash, rol, activo) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario.getNombre());
            ps.setString(2, usuario.getApellido());
            ps.setString(3, usuario.getUsuario());
            ps.setString(4, usuario.getContrasenaHash());
            ps.setString(5, usuario.getRol());
            ps.setBoolean(6, usuario.isActivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se obtuvo el ID del usuario.");
                return rs.getInt(1);
            }
        }
    }

    public List<Usuario> listar(Connection con) throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM usuario ORDER BY nombre, apellido"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    public Usuario bloquearPorId(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM usuario WHERE id_usuario = ? FOR UPDATE")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapear(rs) : null; }
        }
    }

    public int contarAdministradoresActivos(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_usuario FROM usuario WHERE rol = 'ADMINISTRADOR' AND activo = TRUE FOR UPDATE");
             ResultSet rs = ps.executeQuery()) {
            int total = 0;
            while (rs.next()) total++;
            return total;
        }
    }

    public void cambiarRol(Connection con, int idUsuario, String nuevoRol) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET rol = ? WHERE id_usuario = ?")) {
            ps.setString(1, nuevoRol); ps.setInt(2, idUsuario);
            if (ps.executeUpdate() != 1) throw new SQLException("No se pudo cambiar el rol.");
        }
    }

    public void actualizarHashLegacy(int idUsuario, String hashAnterior, String hashNuevo) throws SQLException {
        String sql = "UPDATE usuario SET contrasena_hash = ? WHERE id_usuario = ? AND contrasena_hash = ?";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hashNuevo);
            ps.setInt(2, idUsuario);
            ps.setString(3, hashAnterior);
            ps.executeUpdate();
        }
    }

    public Usuario buscarPorUsuario(String usuario) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return buscarPorUsuario(con, usuario);
        }
    }

    public Usuario buscarPorUsuario(Connection con, String usuario) throws SQLException {
        String sql = "SELECT * FROM usuario WHERE usuario = ? AND activo = TRUE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
        }
        return null;
    }

    public void actualizarContrasena(Connection con, int idUsuario, String hash) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET contrasena_hash = ? WHERE id_usuario = ? AND activo = TRUE")) {
            ps.setString(1, hash);
            ps.setInt(2, idUsuario);
            if (ps.executeUpdate() != 1) throw new SQLException("No se pudo actualizar la contraseña.");
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario(rs.getInt("id_usuario"));
        u.setNombre(rs.getString("nombre"));
        u.setApellido(rs.getString("apellido"));
        u.setUsuario(rs.getString("usuario"));
        u.setContrasenaHash(rs.getString("contrasena_hash"));
        u.setRol(rs.getString("rol"));
        u.setActivo(rs.getBoolean("activo"));
        return u;
    }
}
