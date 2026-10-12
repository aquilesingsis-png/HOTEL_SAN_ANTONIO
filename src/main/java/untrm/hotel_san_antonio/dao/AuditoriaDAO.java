package untrm.hotel_san_antonio.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;

public class AuditoriaDAO {
    public void registrar(Connection con, int idUsuario, String accion, String entidad,
                         int idEntidad, String detalle) throws SQLException {
        String sql = "INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle) "
                + "VALUES (?, ?, ?, ?, ?)";
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
}
