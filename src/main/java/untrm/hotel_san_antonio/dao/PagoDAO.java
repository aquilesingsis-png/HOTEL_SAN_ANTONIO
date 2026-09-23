package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Pago;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Cuanto se cobro hoy por reservas (adelantos, saldos y pagos completos), para el Dashboard. */
    public BigDecimal sumarHoy(Connection con) throws SQLException {
        String sql = "SELECT COALESCE(SUM(monto), 0) FROM pago WHERE DATE(fecha_pago) = CURDATE()";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    /** Total cobrado por dia entre [desde, hasta], con 0 en los dias sin pagos. */
    public Map<LocalDate, BigDecimal> sumarPorDia(Connection con, LocalDate desde, LocalDate hasta) throws SQLException {
        Map<LocalDate, BigDecimal> porDia = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, BigDecimal.ZERO);
        }
        String sql = "SELECT DATE(fecha_pago) AS dia, SUM(monto) AS total FROM pago "
                + "WHERE DATE(fecha_pago) BETWEEN ? AND ? GROUP BY DATE(fecha_pago)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    porDia.put(rs.getDate("dia").toLocalDate(), rs.getBigDecimal("total"));
                }
            }
        }
        return porDia;
    }
}
