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

    /**
     * Reserva el turno para emitir en la serie de ese tipo: una sola persona a la vez toma el siguiente
     * numero (aunque sea desde otra PC); las demas esperan hasta 10 segundos. Se suelta con liberarSerie.
     */
    public void bloquearSerie(Connection con, String tipo) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            ps.setString(1, "hotel_comprobante_" + tipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new IllegalStateException(
                            "Otra persona está emitiendo un comprobante. Intenta de nuevo en unos segundos.");
                }
            }
        }
    }

    /** Suelta el turno de la serie (si no se tenia, no pasa nada). */
    public void liberarSerie(Connection con, String tipo) {
        try (PreparedStatement ps = con.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, "hotel_comprobante_" + tipo);
            ps.executeQuery().close();
        } catch (SQLException ignorado) {
            // al cerrarse la conexion el turno se suelta igual
        }
    }

    /**
     * Siguiente numero correlativo de la serie de ese tipo (ej. "B001-000002"): el ultimo emitido mas uno.
     * Se llama con el turno de la serie ya tomado; la lectura con bloqueo trae siempre el dato mas reciente.
     */
    public String generarNumero(Connection con, String tipo) throws SQLException {
        String serie = SERIE.getOrDefault(tipo, "X001");
        String sql = "SELECT numero FROM comprobante WHERE tipo = ? ORDER BY id_comprobante DESC LIMIT 1 FOR UPDATE";
        int ultimo = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String numero = rs.getString(1);
                    ultimo = Integer.parseInt(numero.substring(numero.lastIndexOf('-') + 1));
                }
            }
        }
        return serie + "-" + String.format("%06d", ultimo + 1);
    }

    /** Numero del comprobante ya emitido para esa estadia, o null si todavia no tiene. */
    public String numeroDeReserva(Connection con, int idReserva) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT numero FROM comprobante WHERE id_reserva = ? LIMIT 1")) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
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
