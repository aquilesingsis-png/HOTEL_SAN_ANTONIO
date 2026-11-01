package untrm.hotel_san_antonio.modelo.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import untrm.hotel_san_antonio.modelo.CierreCaja;
import untrm.hotel_san_antonio.modelo.MovimientoLibro;
import untrm.hotel_san_antonio.modelo.TotalesCaja;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;

/**
 * Libro de caja: una sola lista con todo el dinero que entra y sale, venga de donde venga
 * (pagos de alojamiento, ventas del carrito, movimientos manuales y gastos). Acceso a datos.
 */
public class CajaDAO {

    /** Cuántos renglones trae como máximo la lista; uno más se pide para saber si hay más. */
    public static final int LIMITE = 5000;

    /** Los cuatro orígenes del dinero, unidos en una misma tabla virtual. */
    private static final String TODOS = ""
            + "SELECT COALESCE(TIMESTAMP(p.fecha_evento, TIME(p.fecha_pago)), p.fecha_pago) AS fecha, 'INGRESO' AS tipo, "
            + "CONCAT(IF(r.estado = 'CANCELADA', 'Adelanto retenido por cancelación', CONCAT('Pago de cliente - ', "
            + "CASE p.tipo_pago WHEN 'ADELANTO' THEN 'Adelanto' WHEN 'SALDO' THEN 'Saldo' ELSE 'Pago completo' END)), "
            + "' - Habitación ', hab.numero, ' (R-', LPAD(r.id_reserva, 4, '0'), ')', "
            + "IF(p.fecha_evento IS NULL, '', ' · registro tardío')) AS concepto, "
            + "p.monto AS monto, p.metodo_pago AS metodo, "
            + "(SELECT MIN(c.numero) FROM comprobante c WHERE c.id_reserva = r.id_reserva AND c.estado = 'EMITIDO') AS documento, "
            + "CONCAT(h.nombres, ' ', h.apellidos) AS cliente, CONCAT(u.nombre, ' ', u.apellido) AS responsable, "
            + "'Alojamiento' AS origen, r.id_reserva AS id_origen "
            + "FROM pago p JOIN reserva r ON r.id_reserva = p.id_reserva "
            + "JOIN huesped h ON h.id_huesped = r.id_huesped JOIN habitacion hab ON hab.id_habitacion = r.id_habitacion "
            + "JOIN usuario u ON u.id_usuario = p.id_usuario "
            + "UNION ALL "
            + "SELECT COALESCE(TIMESTAMP(v.fecha_evento, TIME(v.fecha_venta)), v.fecha_venta), "
            + "IF(v.estado = 'ANULADA', 'ANULADA', IF(v.id_habitacion IS NULL, 'INGRESO', 'CARGO')), "
            + "CONCAT('Venta de tienda', IF(v.fecha_evento IS NULL, '', ' (registro tardío)'), ' - ', COALESCE((SELECT GROUP_CONCAT(CONCAT(d.cantidad, ' x ', pr.nombre) SEPARATOR ', ') "
            + "FROM detalle_venta d JOIN producto pr ON pr.id_producto = d.id_producto WHERE d.id_venta = v.id_venta), "
            + "'sin detalle'), IF(v.id_habitacion IS NULL, '', CONCAT(' (a la habitación ', hb.numero, ')')), "
            + "IF(v.estado = 'ANULADA', CONCAT(' · ANULADA: ', v.motivo_anulacion), '')), "
            + "v.total, IF(v.id_habitacion IS NULL, COALESCE(v.metodo_pago, 'NO REGISTRADO'), 'CARGO A HABITACION'), "
            + "(SELECT c.numero FROM comprobante c WHERE c.id_comprobante = v.id_comprobante), "
            + "COALESCE(v.cliente_externo, (SELECT CONCAT(h2.nombres, ' ', h2.apellidos) FROM huesped h2 "
            + "WHERE h2.id_huesped = v.id_huesped), 'Cliente varios'), "
            + "CONCAT(u.nombre, ' ', u.apellido), 'Carrito', v.id_venta "
            + "FROM venta_tienda v JOIN usuario u ON u.id_usuario = v.id_usuario "
            + "LEFT JOIN habitacion hb ON hb.id_habitacion = v.id_habitacion "
            + "UNION ALL "
            + "SELECT m.fecha, m.tipo, CONCAT('Movimiento de caja - ', m.concepto, "
            + "IF(m.referencia IS NULL OR m.referencia = '', '', CONCAT(' (', m.referencia, ')'))), "
            + "m.monto, m.metodo_pago, NULL, '', CONCAT(u.nombre, ' ', u.apellido), 'Manual', m.id_movimiento "
            + "FROM movimiento_caja m JOIN usuario u ON u.id_usuario = m.id_usuario "
            + "UNION ALL "
            + "SELECT TIMESTAMP(g.fecha, TIME(g.fecha_registro)), 'EGRESO', "
            + "CONCAT('Gasto - ', g.concepto, ' (', g.categoria, ')'), g.monto, UPPER(g.metodo_pago), NULL, '', "
            + "CONCAT(u.nombre, ' ', u.apellido), 'Gasto', g.id_gasto "
            + "FROM gasto g JOIN usuario u ON u.id_usuario = g.id_usuario WHERE g.activo = TRUE";
    /** Los últimos 40 cierres de caja, del más reciente al más antiguo; con idUsuario solo los de esa persona. */
    public List<CierreCaja> listarCierres(Integer idUsuario) throws SQLException {
        String sql = "SELECT c.id_cierre, c.fecha, c.turno, CONCAT(u.nombre, ' ', u.apellido) AS responsable, "
                + "c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado, c.fecha_confirmacion "
                + "FROM cierre_caja c JOIN usuario u ON u.id_usuario = c.id_usuario "
                + (idUsuario == null ? "" : "WHERE c.id_usuario = ? ")
                + "ORDER BY c.fecha DESC, c.id_cierre DESC LIMIT 40";
        List<CierreCaja> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (idUsuario != null) {
                ps.setInt(1, idUsuario);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp confirmado = rs.getTimestamp("fecha_confirmacion");
                    lista.add(new CierreCaja(rs.getInt("id_cierre"), rs.getDate("fecha").toLocalDate(), rs.getString("turno"),
                            rs.getString("responsable"), rs.getBigDecimal("efectivo_esperado"),
                            rs.getBigDecimal("efectivo_contado"), rs.getBigDecimal("diferencia"), rs.getString("estado"),
                            confirmado == null ? null : confirmado.toLocalDateTime()));
                }
            }
        }
        return lista;
    }

    /**
     * @param tipo   INGRESO, EGRESO, CARGO, ANULADA o null (todos)
     * @param metodo EFECTIVO, YAPE, TRANSFERENCIA, TARJETA o null (todos)
     * @param origen Alojamiento, Carrito, Manual, Gasto o null (todos)
     * @param texto  se busca en el concepto, el cliente, el responsable y el documento
     */
    public List<MovimientoLibro> listarMovimientos(LocalDate desde, LocalDate hasta, String tipo, String metodo,
                                                   String origen, String texto) throws SQLException {
        List<Object> valores = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(TODOS).append(") t WHERE 1 = 1 ")
                .append(filtros(desde, hasta, tipo, metodo, origen, texto, valores))
                .append("ORDER BY t.fecha DESC LIMIT ").append(LIMITE + 1);   // uno de más, para saber si hay más

        List<MovimientoLibro> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new MovimientoLibro(rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("tipo"),
                            rs.getString("concepto"), rs.getBigDecimal("monto"), rs.getString("metodo"),
                            rs.getString("documento"), rs.getString("cliente"), rs.getString("responsable"),
                            rs.getString("origen"), rs.getInt("id_origen")));
                }
            }
        }
        return lista;
    }

    /** Los totales de todo lo que cumple los filtros, sin el corte de la lista. */
    public TotalesCaja totales(LocalDate desde, LocalDate hasta, String tipo, String metodo,
                               String origen, String texto) throws SQLException {
        List<Object> valores = new ArrayList<>();
        String sql = "SELECT COALESCE(SUM(CASE WHEN t.tipo = 'INGRESO' THEN t.monto END), 0), "
                + "COALESCE(SUM(CASE WHEN t.tipo = 'EGRESO' THEN t.monto END), 0), "
                + "COALESCE(SUM(CASE WHEN t.metodo = 'EFECTIVO' AND t.tipo = 'INGRESO' THEN t.monto "
                + "WHEN t.metodo = 'EFECTIVO' AND t.tipo = 'EGRESO' THEN -t.monto END), 0) "
                + "FROM (" + TODOS + ") t WHERE 1 = 1 "
                + filtros(desde, hasta, tipo, metodo, origen, texto, valores);
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new TotalesCaja(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3));
            }
        }
    }

    /** Las condiciones de los filtros (cada una termina con un espacio) y sus valores, en orden. */
    private String filtros(LocalDate desde, LocalDate hasta, String tipo, String metodo, String origen,
                           String texto, List<Object> valores) {
        StringBuilder sql = new StringBuilder();
        if (desde != null) {
            sql.append("AND t.fecha >= ? ");
            valores.add(java.sql.Timestamp.valueOf(desde.atStartOfDay()));
        }
        if (hasta != null) {
            sql.append("AND t.fecha < ? ");
            valores.add(java.sql.Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
        }
        if (tipo != null) {
            sql.append("AND t.tipo = ? ");
            valores.add(tipo);
        }
        if (metodo != null) {
            sql.append("AND t.metodo = ? ");
            valores.add(metodo);
        }
        if (origen != null) {
            sql.append("AND t.origen = ? ");
            valores.add(origen);
        }
        if (texto != null && !texto.isBlank()) {
            sql.append("AND (t.concepto LIKE ? OR t.cliente LIKE ? OR t.responsable LIKE ? OR t.documento LIKE ?) ");
            String patron = "%" + texto.trim() + "%";
            for (int i = 0; i < 4; i++) {
                valores.add(patron);
            }
        }
        return sql.toString();
    }

    /** Guarda un ingreso o egreso escrito a mano y devuelve su número. */
    public int insertarMovimiento(Connection con, int idUsuario, String tipo, String concepto, String metodo,
                                  BigDecimal monto, String referencia, String observacion) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO movimiento_caja(id_usuario,tipo,concepto,metodo_pago,monto,referencia,observacion) "
                + "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, tipo);
            ps.setString(3, concepto);
            ps.setString(4, metodo);
            ps.setBigDecimal(5, monto);
            ps.setString(6, referencia);
            ps.setString(7, observacion);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se obtuvo el identificador generado.");
                return rs.getInt(1);
            }
        }
    }
}
