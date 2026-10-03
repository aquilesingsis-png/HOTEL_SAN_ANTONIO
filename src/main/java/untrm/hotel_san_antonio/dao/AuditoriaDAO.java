package untrm.hotel_san_antonio.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
            ps.setString(5, detalle);
            ps.executeUpdate();
        }
    }
}
