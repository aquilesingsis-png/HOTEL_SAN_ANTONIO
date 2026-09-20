package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Pago;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PagoDAO {

    public void insertar(Connection con, Pago p) throws SQLException {
        String sql = "INSERT INTO pago (id_reserva, id_usuario, monto, metodo_pago, tipo_pago) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, p.getIdReserva());
            ps.setInt(2, p.getIdUsuario());
            ps.setBigDecimal(3, p.getMonto());
            ps.setString(4, p.getMetodoPago());
            ps.setString(5, p.getTipoPago());
            ps.executeUpdate();
        }
    }
}
