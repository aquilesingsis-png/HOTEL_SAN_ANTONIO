package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CambioHabitacionDAO {
    public void registrar(Connection con, int idReserva, int anterior, int nueva, int usuario,
                         BigDecimal precioAnterior, BigDecimal precioNuevo, String motivo) throws SQLException {
        String sql = "INSERT INTO cambio_habitacion (id_reserva, id_habitacion_anterior, "
                + "id_habitacion_nueva, id_usuario, precio_anterior, precio_nuevo, motivo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            ps.setInt(2, anterior);
            ps.setInt(3, nueva);
            ps.setInt(4, usuario);
            ps.setBigDecimal(5, precioAnterior);
            ps.setBigDecimal(6, precioNuevo);
            ps.setString(7, motivo);
            ps.executeUpdate();
        }
    }
}
