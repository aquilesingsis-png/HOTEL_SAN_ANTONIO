package untrm.hotel_san_antonio.modelo.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cierre de caja por turno (acceso a datos). Las reglas del cierre están en CajaService. */
public class CierreCajaDAO {

    /** El último cierre confirmado: con cuánto empieza el turno siguiente y a qué hora se confirmó. */
    public record CierrePrevio(BigDecimal fondoSiguiente, LocalDateTime cuando) { }

    /** Un cierre que ya existe para esa fecha y turno. */
    public record CierreExistente(int id, String estado) { }

    /** Todos los datos de un cierre que se guarda. */
    public record Registro(int idUsuario, LocalDate fecha, String turno, BigDecimal fondoInicial, BigDecimal cobros,
                           BigDecimal otros, BigDecimal salidas, BigDecimal esperado, BigDecimal contado,
                           BigDecimal diferencia, BigDecimal entregado, BigDecimal fondoSiguiente, String observacion,
                           String estado, LocalDateTime confirmacion) { }

    private static final String NOMBRE_BLOQUEO = "hotel_cierre_caja";

    /** La hora actual de la base de datos. */
    public LocalDateTime ahora(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT NOW()"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getTimestamp(1).toLocalDateTime();
        }
    }

    /** El último cierre confirmado, o null si todavía no hay ninguno. */
    public CierrePrevio ultimoConfirmado(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT fondo_siguiente, COALESCE(fecha_confirmacion, fecha_registro) AS cuando FROM cierre_caja "
                + "WHERE estado = 'CONFIRMADO' ORDER BY cuando DESC, id_cierre DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? new CierrePrevio(rs.getBigDecimal(1), rs.getTimestamp(2).toLocalDateTime()) : null;
        }
    }

    /** Hora del último cierre confirmado: lo cobrado antes de esa hora ya se contó en un cierre. Null si no hay. */
    public LocalDateTime ultimaConfirmacion(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT MAX(COALESCE(fecha_confirmacion, fecha_registro)) "
                + "FROM cierre_caja WHERE estado = 'CONFIRMADO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            Timestamp cuando = rs.getTimestamp(1);
            return cuando == null ? null : cuando.toLocalDateTime();
        }
    }

    /** Efectivo cobrado en el período: pagos de alojamiento y ventas de la tienda pagadas en efectivo. */
    public BigDecimal cobrosEfectivo(Connection con, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return sumar(con, "SELECT COALESCE((SELECT SUM(monto) FROM pago WHERE metodo_pago = 'EFECTIVO' "
                + "AND fecha_pago >= ? AND fecha_pago < ?), 0) + COALESCE((SELECT SUM(total) FROM venta_tienda "
                + "WHERE id_habitacion IS NULL AND metodo_pago = 'EFECTIVO' AND estado = 'ACTIVA' "
                + "AND fecha_venta >= ? AND fecha_venta < ?), 0)", desde, hasta, 2);
    }

    /** Otros ingresos en efectivo (movimientos manuales) del período. */
    public BigDecimal otrosIngresosEfectivo(Connection con, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return sumar(con, "SELECT COALESCE(SUM(monto), 0) FROM movimiento_caja WHERE tipo = 'INGRESO' "
                + "AND metodo_pago = 'EFECTIVO' AND fecha >= ? AND fecha < ?", desde, hasta, 1);
    }

    /** Salidas de efectivo del período: egresos manuales y gastos. */
    public BigDecimal salidasEfectivo(Connection con, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return sumar(con, "SELECT COALESCE((SELECT SUM(monto) FROM movimiento_caja WHERE tipo = 'EGRESO' "
                + "AND metodo_pago = 'EFECTIVO' AND fecha >= ? AND fecha < ?), 0) + COALESCE((SELECT SUM(monto) "
                + "FROM gasto WHERE activo = 1 AND metodo_pago = 'EFECTIVO' AND fecha_registro >= ? "
                + "AND fecha_registro < ?), 0)", desde, hasta, 2);
    }

    /** Ejecuta una suma cuya consulta recibe el mismo período (desde, hasta) una o dos veces. */
    private BigDecimal sumar(Connection con, String sql, LocalDateTime desde, LocalDateTime hasta,
                             int veces) throws SQLException {
        Timestamp d = Timestamp.valueOf(desde);
        Timestamp h = Timestamp.valueOf(hasta);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < veces; i++) {
                ps.setTimestamp(2 * i + 1, d);
                ps.setTimestamp(2 * i + 2, h);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** El cierre de esa fecha y turno, bloqueando la fila; null si todavía no existe. */
    public CierreExistente buscarBloqueando(Connection con, LocalDate fecha, String turno) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_cierre, estado FROM cierre_caja WHERE fecha = ? AND turno = ? FOR UPDATE")) {
            ps.setDate(1, java.sql.Date.valueOf(fecha));
            ps.setString(2, turno);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new CierreExistente(rs.getInt(1), rs.getString(2)) : null;
            }
        }
    }

    /** Guarda un cierre nuevo y devuelve su número. */
    public int insertar(Connection con, Registro r) throws SQLException {
        String sql = "INSERT INTO cierre_caja(id_usuario, fecha, turno, fondo_inicial, cobros_efectivo, otros_ingresos, "
                + "salidas_efectivo, efectivo_esperado, efectivo_contado, diferencia, efectivo_entregado, "
                + "fondo_siguiente, observacion, estado, fecha_confirmacion) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, r);
            if (ps.executeUpdate() < 1) {
                throw new SQLException("No se guardó el cierre.");
            }
            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("No se obtuvo el número del cierre.");
                }
                return claves.getInt(1);
            }
        }
    }

    /** Cambia un borrador ya guardado. */
    public void actualizar(Connection con, int idCierre, Registro r) throws SQLException {
        String sql = "UPDATE cierre_caja SET id_usuario=?, fecha=?, turno=?, fondo_inicial=?, cobros_efectivo=?, "
                + "otros_ingresos=?, salidas_efectivo=?, efectivo_esperado=?, efectivo_contado=?, diferencia=?, "
                + "efectivo_entregado=?, fondo_siguiente=?, observacion=?, estado=?, fecha_confirmacion=? "
                + "WHERE id_cierre=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            asignar(ps, r);
            ps.setInt(16, idCierre);
            if (ps.executeUpdate() < 1) {
                throw new SQLException("No se guardó el cierre.");
            }
        }
    }

    private void asignar(PreparedStatement ps, Registro r) throws SQLException {
        ps.setInt(1, r.idUsuario());
        ps.setDate(2, java.sql.Date.valueOf(r.fecha()));
        ps.setString(3, r.turno());
        ps.setBigDecimal(4, r.fondoInicial());
        ps.setBigDecimal(5, r.cobros());
        ps.setBigDecimal(6, r.otros());
        ps.setBigDecimal(7, r.salidas());
        ps.setBigDecimal(8, r.esperado());
        ps.setBigDecimal(9, r.contado());
        ps.setBigDecimal(10, r.diferencia());
        ps.setBigDecimal(11, r.entregado());
        ps.setBigDecimal(12, r.fondoSiguiente());
        ps.setString(13, r.observacion());
        ps.setString(14, r.estado());
        ps.setTimestamp(15, r.confirmacion() == null ? null : Timestamp.valueOf(r.confirmacion()));
    }

    /** Intenta tomar el turno de los cierres (espera hasta 10 segundos); false si otra persona lo tiene. */
    public boolean tomarTurno(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            ps.setString(1, NOMBRE_BLOQUEO);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    public void soltarTurno(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, NOMBRE_BLOQUEO);
            ps.executeQuery().close();
        } catch (SQLException ignorado) {
            // al cerrarse la conexión se suelta igual
        }
    }
}
