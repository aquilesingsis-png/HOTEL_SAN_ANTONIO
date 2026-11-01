package untrm.hotel_san_antonio.modelo.dao;

import untrm.hotel_san_antonio.modelo.CambioUsuario;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditoriaDAO {
    public void registrar(Connection con, int idUsuario, String accion, String entidad,
                         int idEntidad, String detalle) throws SQLException {
        String sql = "INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, origen) "
                + "VALUES (?, ?, ?, ?, ?, 'SISTEMA')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, accion);
            ps.setString(3, entidad);
            ps.setInt(4, idEntidad);
            ps.setString(5, detalle != null && detalle.length() > 500 ? detalle.substring(0, 497) + "..." : detalle);
            ps.executeUpdate();
        }
    }

    /** Cuando se registro por ultima vez alguna de esas acciones sobre esa entidad, o null si nunca. */
    public LocalDateTime ultimaFecha(Connection con, String entidad, int idEntidad, String... acciones)
            throws SQLException {
        String marcas = String.join(",", Collections.nCopies(acciones.length, "?"));
        String sql = "SELECT MAX(fecha) FROM auditoria WHERE entidad = ? AND id_entidad = ? AND accion IN (" + marcas + ")";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, entidad);
            ps.setInt(2, idEntidad);
            for (int i = 0; i < acciones.length; i++) {
                ps.setString(3 + i, acciones[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Timestamp fecha = rs.getTimestamp(1);
                return fecha == null ? null : fecha.toLocalDateTime();
            }
        }
    }

    /**
     * Los cambios a cuentas de usuario, del más reciente al más antiguo: lo que hizo el programa y los cambios
     * hechos directo en la base de datos (sin repetir cada cambio que ya anotó el programa).
     */
    public List<CambioUsuario> listarCambiosDeUsuarios() throws SQLException {
        String sql = "SELECT a.fecha, COALESCE(afectado.usuario, CONCAT('#', a.id_entidad)), a.accion, "
                + "COALESCE(a.detalle, ''), COALESCE(actor.usuario, 'Base de datos') FROM auditoria a "
                + "LEFT JOIN usuario actor ON actor.id_usuario = a.id_usuario "
                + "LEFT JOIN usuario afectado ON afectado.id_usuario = a.id_entidad "
                + "WHERE a.entidad = 'usuario' AND (a.tipo_operacion IS NULL OR a.origen = 'BASE_DE_DATOS') "
                + "ORDER BY a.fecha DESC, a.id_auditoria DESC";
        List<CambioUsuario> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); java.sql.Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(new CambioUsuario(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2),
                    rs.getString(3), rs.getString(4), rs.getString(5)));
        }
        return lista;
    }
}
