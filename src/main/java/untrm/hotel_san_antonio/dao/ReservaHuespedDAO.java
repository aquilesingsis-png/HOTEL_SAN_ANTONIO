package untrm.hotel_san_antonio.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import untrm.hotel_san_antonio.modelo.Huesped;

/** Relación entre una reserva y todas las personas que ocupan la habitación. */
public class ReservaHuespedDAO {
    public void asociar(Connection con, int idReserva, int idHuesped, boolean principal) throws SQLException {
        String sql = "INSERT INTO reserva_huesped (id_reserva, id_huesped, principal) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            ps.setInt(2, idHuesped);
            ps.setBoolean(3, principal);
            ps.executeUpdate();
        }
    }

    public List<Huesped> listar(Connection con, int idReserva) throws SQLException {
        String sql = "SELECT h.* FROM reserva_huesped rh JOIN huesped h ON h.id_huesped = rh.id_huesped "
                + "WHERE rh.id_reserva = ? ORDER BY rh.principal DESC, rh.fecha_asociacion";
        List<Huesped> resultado = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Huesped h = new Huesped(rs.getString("tipo_documento"), rs.getString("num_documento"),
                            rs.getString("nombres"), rs.getString("apellidos"), rs.getString("pais_procedencia"),
                            rs.getString("telefono"), rs.getString("email"));
                    h.setIdHuesped(rs.getInt("id_huesped"));
                    resultado.add(h);
                }
            }
        }
        return resultado;
    }
}
