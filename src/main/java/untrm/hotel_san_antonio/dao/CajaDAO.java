package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;

/**
 * Libro de caja: una sola lista con todo el dinero que entra y sale, venga de donde venga
 * (pagos de alojamiento, ventas del carrito, movimientos manuales y gastos).
 */
public class CajaDAO {

    /** Un renglon del libro de caja. tipo: INGRESO, EGRESO o CARGO (venta cargada a una habitacion). */
    public record Movimiento(LocalDateTime fecha, String tipo, String concepto, BigDecimal monto,
                             String metodo, String documento, String cliente, String responsable,
                             String origen) { }

    /** Los cuatro origenes del dinero, unidos en una misma tabla virtual. */
    private static final String TODOS = ""
            + "SELECT COALESCE(TIMESTAMP(p.fecha_evento, TIME(p.fecha_pago)), p.fecha_pago) AS fecha, 'INGRESO' AS tipo, "
            + "CONCAT('Pago de cliente - ', CASE p.tipo_pago WHEN 'ADELANTO' THEN 'Adelanto' WHEN 'SALDO' THEN 'Saldo' "
            + "ELSE 'Pago completo' END, ' - Habitación ', hab.numero, ' (R-', LPAD(r.id_reserva, 4, '0'), ')') AS concepto, "
            + "p.monto AS monto, p.metodo_pago AS metodo, "
            + "(SELECT MIN(c.numero) FROM comprobante c WHERE c.id_reserva = r.id_reserva) AS documento, "
            + "CONCAT(h.nombres, ' ', h.apellidos) AS cliente, CONCAT(u.nombre, ' ', u.apellido) AS responsable, "
            + "'Alojamiento' AS origen "
            + "FROM pago p JOIN reserva r ON r.id_reserva = p.id_reserva "
            + "JOIN huesped h ON h.id_huesped = r.id_huesped JOIN habitacion hab ON hab.id_habitacion = r.id_habitacion "
            + "JOIN usuario u ON u.id_usuario = p.id_usuario "
            + "UNION ALL "
            + "SELECT v.fecha_venta, IF(v.id_habitacion IS NULL, 'INGRESO', 'CARGO'), "
            + "CONCAT('Venta de tienda - ', COALESCE((SELECT GROUP_CONCAT(CONCAT(d.cantidad, ' x ', pr.nombre) SEPARATOR ', ') "
            + "FROM detalle_venta d JOIN producto pr ON pr.id_producto = d.id_producto WHERE d.id_venta = v.id_venta), "
            + "'sin detalle'), IF(v.id_habitacion IS NULL, '', CONCAT(' (a la habitación ', hb.numero, ')'))), "
            + "v.total, IF(v.id_habitacion IS NULL, COALESCE(v.metodo_pago, 'NO REGISTRADO'), 'CARGO A HABITACION'), "
            + "(SELECT c.numero FROM comprobante c WHERE c.id_comprobante = v.id_comprobante), "
            + "COALESCE(v.cliente_externo, (SELECT CONCAT(h2.nombres, ' ', h2.apellidos) FROM huesped h2 "
            + "WHERE h2.id_huesped = v.id_huesped), 'Cliente varios'), "
            + "CONCAT(u.nombre, ' ', u.apellido), 'Carrito' "
            + "FROM venta_tienda v JOIN usuario u ON u.id_usuario = v.id_usuario "
            + "LEFT JOIN habitacion hb ON hb.id_habitacion = v.id_habitacion "
            + "UNION ALL "
            + "SELECT m.fecha, m.tipo, CONCAT('Movimiento de caja - ', m.concepto, "
            + "IF(m.referencia IS NULL OR m.referencia = '', '', CONCAT(' (', m.referencia, ')'))), "
            + "m.monto, m.metodo_pago, NULL, '', CONCAT(u.nombre, ' ', u.apellido), 'Manual' "
            + "FROM movimiento_caja m JOIN usuario u ON u.id_usuario = m.id_usuario "
            + "UNION ALL "
            + "SELECT TIMESTAMP(g.fecha, TIME(g.fecha_registro)), 'EGRESO', "
            + "CONCAT('Gasto - ', g.concepto, ' (', g.categoria, ')'), g.monto, UPPER(g.metodo_pago), NULL, '', "
            + "CONCAT(u.nombre, ' ', u.apellido), 'Gasto' "
            + "FROM gasto g JOIN usuario u ON u.id_usuario = g.id_usuario WHERE g.activo = TRUE";

    /**
     * @param tipo   INGRESO, EGRESO, CARGO o null (todos)
     * @param metodo EFECTIVO, YAPE, TRANSFERENCIA, TARJETA o null (todos)
     * @param origen Alojamiento, Carrito, Manual, Gasto o null (todos)
     * @param texto  se busca en el concepto, el cliente, el responsable y el documento
     */
    public List<Movimiento> listar(LocalDate desde, LocalDate hasta, String tipo, String metodo,
                                   String origen, String texto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(TODOS).append(") t WHERE 1 = 1 ");
        List<Object> valores = new ArrayList<>();
        if (desde != null) {
            sql.append("AND DATE(t.fecha) >= ? ");
            valores.add(Date.valueOf(desde));
        }
        if (hasta != null) {
            sql.append("AND DATE(t.fecha) <= ? ");
            valores.add(Date.valueOf(hasta));
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
        sql.append("ORDER BY t.fecha DESC LIMIT 5000");

        List<Movimiento> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Movimiento(rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("tipo"),
                            rs.getString("concepto"), rs.getBigDecimal("monto"), rs.getString("metodo"),
                            rs.getString("documento"), rs.getString("cliente"), rs.getString("responsable"),
                            rs.getString("origen")));
                }
            }
        }
        return lista;
    }
}
