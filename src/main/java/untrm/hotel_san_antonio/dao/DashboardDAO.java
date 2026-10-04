package untrm.hotel_san_antonio.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Consultas extra de la pantalla de Inicio: salidas del día, alertas del administrador y movimiento por hora. */
public class DashboardDAO {

    /** Números de las habitaciones cuyo huésped debe salir hoy (estadía con check-in y salida hoy). */
    public List<String> salidasHoy(Connection con) throws SQLException {
        List<String> numeros = new ArrayList<>();
        String sql = "SELECT h.numero FROM reserva r JOIN habitacion h ON h.id_habitacion = r.id_habitacion "
                + "WHERE r.estado = 'CHECKIN' AND r.fecha_checkout = CURDATE() ORDER BY h.numero";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) numeros.add(rs.getString(1));
        }
        return numeros;
    }

    /** Productos activos sin stock. */
    public int productosAgotados(Connection con) throws SQLException {
        return contar(con, "SELECT COUNT(*) FROM producto WHERE activo = 1 AND stock = 0");
    }

    /** Productos activos con poco stock (de 1 hasta el umbral). */
    public int productosConStockBajo(Connection con, int umbral) throws SQLException {
        return contar(con, "SELECT COUNT(*) FROM producto WHERE activo = 1 AND stock > 0 AND stock <= " + umbral);
    }

    /** Cuentas bloqueadas ahora por demasiados intentos de ingreso. */
    public int cuentasBloqueadas(Connection con) throws SQLException {
        return contar(con, "SELECT COUNT(*) FROM usuario WHERE bloqueado_hasta IS NOT NULL AND bloqueado_hasta > NOW()");
    }

    /** Días desde el último respaldo hecho desde el sistema, o -1 si nunca se hizo. */
    public int diasDesdeUltimoRespaldo(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATEDIFF(CURDATE(), MAX(DATE(fecha))) FROM auditoria "
                     + "WHERE accion = 'RESPALDO_CREAR'")) {
            rs.next();
            int dias = rs.getInt(1);
            return rs.wasNull() ? -1 : dias;
        }
    }

    /**
     * Cuántas operaciones (ventas del carrito y cobros de reservas) hubo en cada hora del día.
     * No cuenta los registros tardíos, porque la hora en que se escribieron no es la hora en que pasaron.
     *
     * @param ultimosTreintaDias false = solo hoy; true = los últimos 30 días juntos
     */
    public Map<Integer, Integer> movimientoPorHora(Connection con, boolean ultimosTreintaDias) throws SQLException {
        String desde = ultimosTreintaDias ? "DATE_SUB(CURDATE(), INTERVAL 29 DAY)" : "CURDATE()";
        String sql = "SELECT h, COUNT(*) FROM ("
                + "SELECT HOUR(fecha_pago) AS h, DATE(fecha_pago) AS d FROM pago WHERE fecha_evento IS NULL "
                + "UNION ALL "
                + "SELECT HOUR(fecha_venta), DATE(fecha_venta) FROM venta_tienda WHERE fecha_evento IS NULL"
                + ") t WHERE d >= " + desde + " GROUP BY h";
        Map<Integer, Integer> porHora = new TreeMap<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) porHora.put(rs.getInt(1), rs.getInt(2));
        }
        return porHora;
    }

    private int contar(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
