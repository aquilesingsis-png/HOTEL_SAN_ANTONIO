package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.VentaTienda;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class VentaTiendaDAO {

    public int insertar(Connection cn, VentaTienda venta) throws SQLException {
        String sql = "INSERT INTO venta_tienda " +
                "(id_huesped, id_habitacion, id_usuario, id_comprobante, cliente_externo, total) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setNullableInt(ps, 1, venta.getIdHuesped());
            setNullableInt(ps, 2, venta.getIdHabitacion());
            ps.setInt(3, venta.getIdUsuario());
            setNullableInt(ps, 4, venta.getIdComprobante());
            if (venta.getClienteExterno() == null || venta.getClienteExterno().trim().isEmpty()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, venta.getClienteExterno().trim());
            }
            ps.setBigDecimal(6, venta.getTotal());

            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo registrar la venta de tiendita.");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se obtuvo el identificador de la venta.");
    }

    private void setNullableInt(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    /** Cuanto se vendio hoy en la tiendita, para el Dashboard. */
    public BigDecimal sumarHoy(Connection con) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM venta_tienda WHERE DATE(fecha_venta) = CURDATE()";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    /** Cuantas ventas de tiendita se hicieron hoy, para el Dashboard. */
    public int contarHoy(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*) FROM venta_tienda WHERE DATE(fecha_venta) = CURDATE()";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Total vendido en tiendita por dia entre [desde, hasta], con 0 en los dias sin ventas. */
    public Map<LocalDate, BigDecimal> sumarPorDia(Connection con, LocalDate desde, LocalDate hasta) throws SQLException {
        Map<LocalDate, BigDecimal> porDia = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, BigDecimal.ZERO);
        }
        String sql = "SELECT DATE(fecha_venta) AS dia, SUM(total) AS total FROM venta_tienda "
                + "WHERE DATE(fecha_venta) BETWEEN ? AND ? GROUP BY DATE(fecha_venta)";
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
