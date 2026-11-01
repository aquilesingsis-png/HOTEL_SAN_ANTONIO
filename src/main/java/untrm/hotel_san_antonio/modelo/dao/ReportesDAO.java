package untrm.hotel_san_antonio.modelo.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;

/** Consultas de los reportes: ocupación, ingresos y ventas de tienda (acceso a datos). */
public class ReportesDAO {

    /** Una reserva que se cruza con el período: sus fechas, las personas, la habitación y el tipo. */
    public record ReservaEnPeriodo(LocalDate ingreso, LocalDate salida, int huespedes, int idHabitacion, String tipo) { }

    /** Un cobro: un pago de alojamiento o una venta de tienda cobrada en el momento. */
    public record Cobro(LocalDate dia, String origen, String metodo, BigDecimal monto) { }

    /** Una línea de venta de la tienda: un producto vendido en una venta. */
    public record LineaVendida(LocalDate dia, int idVenta, String codigoBarra, String producto, String categoria,
                               int cantidad, BigDecimal subtotal) { }

    /** Cuántas habitaciones hay de cada tipo (solo los tipos que tienen alguna). */
    public Map<String, Integer> habitacionesPorTipo(Connection con) throws SQLException {
        Map<String, Integer> inventario = new TreeMap<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT t.nombre,COUNT(h.id_habitacion) FROM tipo_habitacion t "
                + "LEFT JOIN habitacion h ON h.id_tipo=t.id_tipo GROUP BY t.id_tipo,t.nombre")) {
            while (rs.next()) if (rs.getInt(2) > 0) inventario.put(rs.getString(1), rs.getInt(2));
        }
        return inventario;
    }

    /** Las reservas no canceladas que tienen alguna noche entre inicio y fin. */
    public List<ReservaEnPeriodo> reservasQueCruzan(Connection con, LocalDate inicio, LocalDate fin) throws SQLException {
        String sql = "SELECT r.fecha_checkin,r.fecha_checkout,r.num_huespedes,h.id_habitacion,t.nombre "
                + "FROM reserva r JOIN habitacion h ON h.id_habitacion=r.id_habitacion "
                + "JOIN tipo_habitacion t ON t.id_tipo=h.id_tipo "
                + "WHERE r.estado <> 'CANCELADA' "
                + "AND r.fecha_checkin<=? AND r.fecha_checkout>?";
        List<ReservaEnPeriodo> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fin));
            ps.setDate(2, Date.valueOf(inicio));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ReservaEnPeriodo(rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate(),
                            rs.getInt(3), rs.getInt(4), rs.getString(5)));
                }
            }
        }
        return lista;
    }

    public List<String> categorias() throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM categoria ORDER BY nombre")) {
            while (rs.next()) lista.add(rs.getString(1));
        }
        return lista;
    }

    public List<String> tiposHabitacion() throws SQLException {
        List<String> tipos = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM tipo_habitacion ORDER BY nombre")) {
            while (rs.next()) tipos.add(rs.getString(1));
        }
        return tipos;
    }

    /**
     * Dinero cobrado: pagos de alojamiento y ventas de tienda cobradas en el momento. Lo cargado a
     * una habitación no se cuenta aquí porque se cobra después con el pago de la habitación.
     *
     * @param origen Alojamiento, Tienda o null (todos)
     * @param metodo medio de pago o null (todos)
     */
    public List<Cobro> cobros(LocalDate desde, LocalDate hasta, String origen, String metodo) throws SQLException {
        String sql = "SELECT dia, origen, metodo, monto FROM ("
                + "SELECT DATE(COALESCE(TIMESTAMP(p.fecha_evento, TIME(p.fecha_pago)), p.fecha_pago)) AS dia, "
                + "'Alojamiento' AS origen, p.metodo_pago AS metodo, p.monto AS monto FROM pago p "
                + "UNION ALL SELECT COALESCE(v.fecha_evento, DATE(v.fecha_venta)), 'Tienda', COALESCE(v.metodo_pago, 'NO REGISTRADO'), v.total "
                + "FROM venta_tienda v WHERE v.id_habitacion IS NULL AND v.estado = 'ACTIVA') t WHERE dia BETWEEN ? AND ? ";
        List<Object> valores = new ArrayList<>(List.of(Date.valueOf(desde), Date.valueOf(hasta)));
        if (origen != null) {
            sql += "AND origen = ? ";
            valores.add(origen);
        }
        if (metodo != null) {
            sql += "AND metodo = ? ";
            valores.add(metodo);
        }
        sql += "ORDER BY dia";
        List<Cobro> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Cobro(rs.getDate("dia").toLocalDate(), rs.getString("origen"),
                            rs.getString("metodo"), rs.getBigDecimal("monto")));
                }
            }
        }
        return lista;
    }

    /**
     * Las líneas de las ventas activas de la tienda entre dos fechas.
     *
     * @param categoria nombre de la categoría o null (todas)
     * @param texto     parte del nombre o del código de barras del producto (puede ser null)
     */
    public List<LineaVendida> lineasVendidas(LocalDate desde, LocalDate hasta, String categoria,
                                             String texto) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COALESCE(v.fecha_evento, DATE(v.fecha_venta)) AS dia, v.id_venta, p.codigo_barra, "
                + "p.nombre AS producto, c.nombre AS categoria, d.cantidad, d.subtotal "
                + "FROM detalle_venta d JOIN venta_tienda v ON v.id_venta = d.id_venta "
                + "JOIN producto p ON p.id_producto = d.id_producto JOIN categoria c ON c.id_categoria = p.id_categoria "
                + "WHERE COALESCE(v.fecha_evento, DATE(v.fecha_venta)) BETWEEN ? AND ? AND v.estado = 'ACTIVA' ");
        List<Object> valores = new ArrayList<>(List.of(Date.valueOf(desde), Date.valueOf(hasta)));
        if (categoria != null) {
            sql.append("AND c.nombre = ? ");
            valores.add(categoria);
        }
        if (texto != null && !texto.isBlank()) {
            sql.append("AND (p.nombre LIKE ? OR p.codigo_barra LIKE ?) ");
            valores.add("%" + texto.trim() + "%");
            valores.add("%" + texto.trim() + "%");
        }
        sql.append("ORDER BY COALESCE(v.fecha_evento, DATE(v.fecha_venta)), v.fecha_venta");
        List<LineaVendida> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new LineaVendida(rs.getDate("dia").toLocalDate(), rs.getInt("id_venta"),
                            rs.getString("codigo_barra"), rs.getString("producto"), rs.getString("categoria"),
                            rs.getInt("cantidad"), rs.getBigDecimal("subtotal")));
                }
            }
        }
        return lista;
    }
}
