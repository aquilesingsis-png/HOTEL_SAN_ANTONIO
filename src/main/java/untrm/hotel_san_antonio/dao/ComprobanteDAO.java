package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Comprobante;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class ComprobanteDAO {

    private static final java.util.Map<String, String> SERIE = java.util.Map.of(
            "BOLETA", "B001",
            "FACTURA", "F001",
            "NOTA_VENTA", "NV001"
    );

    /** Siguiente numero correlativo de la serie de ese tipo (ej. "B001-000002"). */
    public String generarNumero(Connection con, String tipo) throws SQLException {
        String serie = SERIE.getOrDefault(tipo, "X001");
        String sql = "SELECT COUNT(*) FROM comprobante WHERE tipo = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int correlativo = rs.getInt(1) + 1;
                return serie + "-" + String.format("%06d", correlativo);
            }
        }
    }

    /** Inserta el comprobante y devuelve el id generado. */
    public int insertar(Connection con, Comprobante c) throws SQLException {
        String sql = "INSERT INTO comprobante (id_reserva, id_usuario, tipo, numero, monto_total) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (c.getIdReserva() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, c.getIdReserva());
            }
            ps.setInt(2, c.getIdUsuario());
            ps.setString(3, c.getTipo());
            ps.setString(4, c.getNumero());
            ps.setBigDecimal(5, c.getMontoTotal());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
