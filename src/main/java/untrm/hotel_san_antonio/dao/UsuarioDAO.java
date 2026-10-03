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

    public void actualizarDatos(Connection con, int idUsuario, String nombre, String apellido, boolean activo)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET nombre = ?, apellido = ?, activo = ? WHERE id_usuario = ?")) {
            ps.setString(1, nombre); ps.setString(2, apellido); ps.setBoolean(3, activo); ps.setInt(4, idUsuario);
            if (ps.executeUpdate() != 1) throw new SQLException("No se pudo actualizar el usuario.");
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

    /** Busca el usuario activo por su nombre y lo bloquea hasta terminar la operación de ingreso. */
    public Usuario bloquearActivoPorLogin(Connection con, String login) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM usuario WHERE usuario = ? AND activo = TRUE FOR UPDATE")) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapear(rs) : null; }
        }
    }

    /** Minutos que faltan para que termine el bloqueo (0 si la cuenta no está bloqueada). */
    public int minutosDeBloqueo(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CASE WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta > NOW() "
                + "THEN GREATEST(1, CEIL(TIMESTAMPDIFF(SECOND, NOW(), bloqueado_hasta) / 60)) ELSE 0 END "
                + "FROM usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    /** Suma un intento fallido de ingreso y devuelve cuántos lleva. */
    public int registrarFallo(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET intentos_fallidos = intentos_fallidos + 1 WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT intentos_fallidos FROM usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public void bloquear(Connection con, int idUsuario, int minutos) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET bloqueado_hasta = DATE_ADD(NOW(), INTERVAL ? MINUTE), intentos_fallidos = 0 "
                + "WHERE id_usuario = ?")) {
            ps.setInt(1, minutos);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    /** Borra los intentos fallidos y cualquier bloqueo (ingreso correcto o desbloqueo del administrador). */
    public void limpiarIntentos(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
    }

    /** Suma un código de recuperación incorrecto y devuelve cuántos lleva. */
    public int registrarFalloRecuperacion(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET intentos_recuperacion = intentos_recuperacion + 1 WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT intentos_recuperacion FROM usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public void limpiarIntentosRecuperacion(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuario SET intentos_recuperacion = 0 WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
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
        java.sql.Timestamp bloqueo = rs.getTimestamp("bloqueado_hasta");
        if (bloqueo != null) u.setBloqueadoHasta(bloqueo.toLocalDateTime());
        java.sql.Timestamp creado = rs.getTimestamp("fecha_creacion");
        if (creado != null) u.setFechaCreacion(creado.toLocalDateTime());
        return u;
    }
}
