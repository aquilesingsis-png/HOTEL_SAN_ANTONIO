package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Consultas del latido del sistema, los cortes (días sin uso) y los días ya regularizados. */
public class DiasSinUsoDAO {

    /** Un tramo de días seguidos sin uso y el estado de su plazo. */
    public record Corte(int id, LocalDate desde, LocalDate hasta, LocalDateTime habilitaHasta,
                        boolean vigente, long minutosRestantes, int reaperturas) { }

    /** Lo registrado para un día pasado: ventas del carrito y pagos de reservas. */
    public record ResumenDia(int ventas, BigDecimal totalVentas, int pagos, BigDecimal totalPagos) { }

    /** Fecha de hoy según el servidor de base de datos (el mismo reloj de todo el sistema). */
    public LocalDate hoy(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT CURDATE()")) {
            rs.next();
            return rs.getDate(1).toLocalDate();
        }
    }

    /** Último día en que el sistema estuvo en uso, o null si todavía no hay ninguno. */
    public LocalDate ultimaActividad(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(fecha) FROM actividad_dia")) {
            rs.next();
            Date fecha = rs.getDate(1);
            return fecha == null ? null : fecha.toLocalDate();
        }
    }

    /** Anota que el sistema está en uso hoy (la primera vez crea el renglón del día). */
    public void marcarActividad(Connection con, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO actividad_dia (fecha, id_usuario) VALUES (CURDATE(), ?) "
                + "ON DUPLICATE KEY UPDATE ultimo_registro = NOW()")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
    }

    /** Crea el corte con plazo de 24 horas desde ahora; devuelve su id, o 0 si ya existía. */
    public int crearCorte(Connection con, LocalDate desde, LocalDate hasta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO corte_sistema (dia_desde, dia_hasta, habilita_hasta) "
                + "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 24 HOUR))", Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            if (ps.executeUpdate() != 1) return 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Todos los cortes, del más reciente al más antiguo. */
    public List<Corte> cortes(Connection con) throws SQLException {
        List<Corte> lista = new ArrayList<>();
        String sql = "SELECT id_corte, dia_desde, dia_hasta, habilita_hasta, habilita_hasta > NOW(), "
                + "GREATEST(0, TIMESTAMPDIFF(MINUTE, NOW(), habilita_hasta)), reaperturas "
                + "FROM corte_sistema ORDER BY id_corte DESC";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new Corte(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(),
                        rs.getTimestamp(4).toLocalDateTime(), rs.getBoolean(5), rs.getLong(6), rs.getInt(7)));
            }
        }
        return lista;
    }

    public Set<LocalDate> diasCompletados(Connection con) throws SQLException {
        Set<LocalDate> dias = new HashSet<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT fecha FROM dia_regularizado")) {
            while (rs.next()) dias.add(rs.getDate(1).toLocalDate());
        }
        return dias;
    }

    /** Id del corte con plazo vigente al que pertenece el día, si el día aún no se completó; si no, null. */
    public Integer corteHabilitado(Connection con, LocalDate dia) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT c.id_corte FROM corte_sistema c WHERE ? BETWEEN c.dia_desde AND c.dia_hasta "
                + "AND c.habilita_hasta > NOW() AND NOT EXISTS (SELECT 1 FROM dia_regularizado d WHERE d.fecha = ?)")) {
            ps.setDate(1, Date.valueOf(dia));
            ps.setDate(2, Date.valueOf(dia));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public void completarDia(Connection con, LocalDate dia, int idCorte, int idUsuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO dia_regularizado (fecha, id_corte, id_usuario) VALUES (?, ?, ?)")) {
            ps.setDate(1, Date.valueOf(dia));
            ps.setInt(2, idCorte);
            ps.setInt(3, idUsuario);
            ps.executeUpdate();
        }
    }

    /** Amplía el plazo 24 horas más y cuenta la reapertura. */
    public void reabrir(Connection con, int idCorte) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE corte_sistema SET habilita_hasta = DATE_ADD(NOW(), INTERVAL 24 HOUR), "
                + "reaperturas = reaperturas + 1 WHERE id_corte = ?")) {
            ps.setInt(1, idCorte);
            ps.executeUpdate();
        }
    }

    /** Lo que ya se registró para ese día (por fecha del hecho). */
    public ResumenDia resumenDia(Connection con, LocalDate dia) throws SQLException {
        int ventas = 0;
        int pagos = 0;
        BigDecimal totalVentas = BigDecimal.ZERO;
        BigDecimal totalPagos = BigDecimal.ZERO;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM venta_tienda WHERE fecha_evento = ?")) {
            ps.setDate(1, Date.valueOf(dia));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                ventas = rs.getInt(1);
                totalVentas = rs.getBigDecimal(2);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(monto), 0) FROM pago WHERE fecha_evento = ?")) {
            ps.setDate(1, Date.valueOf(dia));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                pagos = rs.getInt(1);
                totalPagos = rs.getBigDecimal(2);
            }
        }
        return new ResumenDia(ventas, totalVentas, pagos, totalPagos);
    }
}
