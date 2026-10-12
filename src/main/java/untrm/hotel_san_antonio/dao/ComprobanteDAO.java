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

    /** Un comprobante por su numero (ej. "B001-000009"), o null si no existe. */
    public Comprobante buscarPorNumero(Connection con, String numero) throws SQLException {
        return buscar(con, "c.numero = ?", numero, null);
    }

    /** Un comprobante por su id, o null si no existe. */
    public Comprobante buscarPorId(Connection con, int idComprobante) throws SQLException {
        return buscar(con, "c.id_comprobante = ?", null, idComprobante);
    }

    private Comprobante buscar(Connection con, String condicion, String texto, Integer id) throws SQLException {
        String sql = "SELECT c.*, CONCAT(u.nombre, ' ', u.apellido) AS emitido_por FROM comprobante c "
                + "JOIN usuario u ON u.id_usuario = c.id_usuario WHERE " + condicion;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (texto != null) {
                ps.setString(1, texto);
            } else {
                ps.setInt(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Comprobante c = new Comprobante();
                c.setIdComprobante(rs.getInt("id_comprobante"));
                int idReserva = rs.getInt("id_reserva");
                c.setIdReserva(rs.wasNull() ? null : idReserva);
                c.setIdUsuario(rs.getInt("id_usuario"));
                c.setTipo(rs.getString("tipo"));
                c.setNumero(rs.getString("numero"));
                c.setFechaEmision(rs.getTimestamp("fecha_emision").toLocalDateTime());
                c.setMontoTotal(rs.getBigDecimal("monto_total"));
                c.setClienteTipoDoc(rs.getString("cliente_tipo_doc"));
                c.setClienteDocumento(rs.getString("cliente_documento"));
                c.setClienteNombre(rs.getString("cliente_nombre"));
                c.setClienteDireccion(rs.getString("cliente_direccion"));
                c.setSubtotal(rs.getBigDecimal("subtotal"));
                c.setIgv(rs.getBigDecimal("igv"));
                c.setEmitidoPor(rs.getString("emitido_por"));
                return c;
            }
        }
    }

    /** Inserta el comprobante (con el cliente y el desglose) y devuelve el id generado. */
    public int insertar(Connection con, Comprobante c) throws SQLException {
        String sql = "INSERT INTO comprobante (id_reserva, id_usuario, tipo, numero, monto_total, cliente_tipo_doc, "
                + "cliente_documento, cliente_nombre, cliente_direccion, subtotal, igv) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setString(6, c.getClienteTipoDoc());
            ps.setString(7, c.getClienteDocumento());
            ps.setString(8, c.getClienteNombre());
            ps.setString(9, c.getClienteDireccion());
            ps.setBigDecimal(10, c.getSubtotal());
            ps.setBigDecimal(11, c.getIgv());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
