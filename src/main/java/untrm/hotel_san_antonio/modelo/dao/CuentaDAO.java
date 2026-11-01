package untrm.hotel_san_antonio.modelo.dao;

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

/** Lo que hay guardado para la cuenta de una habitación ocupada (acceso a datos). La cuenta la arma CuentaService. */
public class CuentaDAO {

    /** La estadía en curso (reserva en CHECKIN) con los datos del huésped y de la empresa, si la hay. */
    public record EstadiaEnCurso(int idReserva, LocalDate ingreso, LocalDate salida, BigDecimal montoTotal,
                                 int idHuesped, String nombres, String apellidos, String tipoDocumento,
                                 String numDocumento, String telefono, String razonSocial, String ruc) { }

    /** Un producto de la tienda cargado a la habitación. */
    public record Consumo(LocalDateTime fecha, String producto, int cantidad, BigDecimal precioUnitario,
                          BigDecimal subtotal) { }

    /** Un pago hecho a la reserva; fechaEvento es la fecha real si se registró tarde (puede ser null). */
    public record PagoRegistrado(LocalDateTime fechaPago, LocalDate fechaEvento, String tipo, String metodo,
                                 BigDecimal monto) { }

    /** La estadía en curso de la habitación, o null si no tiene una reserva en CHECKIN. */
    public EstadiaEnCurso buscarEstadiaEnCurso(Connection con, int idHabitacion) throws SQLException {
        String sql = "SELECT r.id_reserva, r.fecha_checkin, r.fecha_checkout, r.monto_total, "
                + "hu.id_huesped, hu.nombres, hu.apellidos, hu.tipo_documento, hu.num_documento, hu.telefono, "
                + "e.razon_social, e.ruc "
                + "FROM reserva r JOIN huesped hu ON hu.id_huesped = r.id_huesped "
                + "LEFT JOIN empresa e ON e.id_empresa = r.id_empresa "
                + "WHERE r.id_habitacion = ? AND r.estado = 'CHECKIN' "
                + "ORDER BY r.fecha_checkin DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idHabitacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new EstadiaEnCurso(rs.getInt("id_reserva"), rs.getDate("fecha_checkin").toLocalDate(),
                        rs.getDate("fecha_checkout").toLocalDate(), rs.getBigDecimal("monto_total"),
                        rs.getInt("id_huesped"), rs.getString("nombres"), rs.getString("apellidos"),
                        rs.getString("tipo_documento"), rs.getString("num_documento"), rs.getString("telefono"),
                        rs.getString("razon_social"), rs.getString("ruc"));
            }
        }
    }

    /** Consumos de la tienda cargados a la habitación (venta sin comprobante = aún no cobrada), desde el ingreso. */
    public List<Consumo> listarConsumos(Connection con, int idHabitacion, int idHuesped, LocalDate desde)
            throws SQLException {
        String sql = "SELECT v.fecha_venta, p.nombre, d.cantidad, d.precio_unitario, d.subtotal "
                + "FROM venta_tienda v JOIN detalle_venta d ON d.id_venta = v.id_venta "
                + "JOIN producto p ON p.id_producto = d.id_producto "
                + "WHERE v.id_habitacion = ? AND v.id_huesped = ? AND v.id_comprobante IS NULL AND v.estado = 'ACTIVA' "
                + "AND v.fecha_venta >= ? ORDER BY v.fecha_venta";
        List<Consumo> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idHabitacion);
            ps.setInt(2, idHuesped);
            ps.setDate(3, Date.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Consumo(rs.getTimestamp("fecha_venta").toLocalDateTime(), rs.getString("nombre"),
                            rs.getInt("cantidad"), rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("subtotal")));
                }
            }
        }
        return lista;
    }

    /** Los pagos de la reserva, del más antiguo al más reciente. */
    public List<PagoRegistrado> listarPagos(Connection con, int idReserva) throws SQLException {
        String sql = "SELECT fecha_pago, fecha_evento, tipo_pago, metodo_pago, monto FROM pago "
                + "WHERE id_reserva = ? ORDER BY fecha_pago, id_pago";
        List<PagoRegistrado> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new PagoRegistrado(rs.getTimestamp("fecha_pago").toLocalDateTime(),
                            rs.getDate("fecha_evento") == null ? null : rs.getDate("fecha_evento").toLocalDate(),
                            rs.getString("tipo_pago"), rs.getString("metodo_pago"), rs.getBigDecimal("monto")));
                }
            }
        }
        return lista;
    }
}
