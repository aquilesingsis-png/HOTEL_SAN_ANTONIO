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

    /** Una venta que todavia no tiene comprobante (id_comprobante IS NULL), o null si no existe o ya se facturo. */
    public VentaTienda buscarPendientePorId(Connection con, int idVenta) throws SQLException {
        String sql = "SELECT * FROM venta_tienda WHERE id_venta = ? AND id_comprobante IS NULL";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                VentaTienda v = new VentaTienda();
                v.setIdVenta(rs.getInt("id_venta"));
                int idHuesped = rs.getInt("id_huesped");
                v.setIdHuesped(rs.wasNull() ? null : idHuesped);
                int idHabitacion = rs.getInt("id_habitacion");
                v.setIdHabitacion(rs.wasNull() ? null : idHabitacion);
                v.setIdUsuario(rs.getInt("id_usuario"));
                v.setClienteExterno(rs.getString("cliente_externo"));
                v.setFechaVenta(rs.getTimestamp("fecha_venta").toLocalDateTime());
                v.setTotal(rs.getBigDecimal("total"));
                return v;
            }
        }
    }

    /** Enlaza a un comprobante recien generado la venta indicada (para el origen "Venta de tienda"). */
    public void enlazarComprobante(Connection con, int idVenta, int idComprobante) throws SQLException {
        String sql = "UPDATE venta_tienda SET id_comprobante = ? WHERE id_venta = ? AND id_comprobante IS NULL";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idComprobante);
            ps.setInt(2, idVenta);
            ps.executeUpdate();
        }
    }

    /**
     * Enlaza a un comprobante recien generado todos los consumos de tienda que quedaron pendientes
     * durante una estadia (mismos que arma CuentaDAO para la cuenta de la habitacion).
     */
    public void enlazarComprobantePorEstadia(Connection con, int idHabitacion, int idHuesped,
                                              LocalDate ingreso, int idComprobante) throws SQLException {
        String sql = "UPDATE venta_tienda SET id_comprobante = ? WHERE id_habitacion = ? AND id_huesped = ? "
                + "AND id_comprobante IS NULL AND fecha_venta >= ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idComprobante);
            ps.setInt(2, idHabitacion);
            ps.setInt(3, idHuesped);
            ps.setDate(4, Date.valueOf(ingreso));
            ps.executeUpdate();
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
