package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.CuentaHabitacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Arma la cuenta de una habitacion ocupada a partir de la reserva en CHECKIN. */
public class CuentaDAO {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** @return la cuenta, o null si la habitacion no tiene una reserva en CHECKIN */
    public CuentaHabitacion obtener(Connection con, int idHabitacion) throws SQLException {
        String sqlReserva = "SELECT r.id_reserva, r.fecha_checkin, r.fecha_checkout, r.monto_total, "
                + "hu.id_huesped, hu.nombres, hu.apellidos, hu.tipo_documento, hu.num_documento, hu.telefono, "
                + "e.razon_social, e.ruc "
                + "FROM reserva r JOIN huesped hu ON hu.id_huesped = r.id_huesped "
                + "LEFT JOIN empresa e ON e.id_empresa = r.id_empresa "
                + "WHERE r.id_habitacion = ? AND r.estado = 'CHECKIN' "
                + "ORDER BY r.fecha_checkin DESC LIMIT 1";

        CuentaHabitacion cuenta = new CuentaHabitacion();
        int idHuesped;
        LocalDate ingreso;
        try (PreparedStatement ps = con.prepareStatement(sqlReserva)) {
            ps.setInt(1, idHabitacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ingreso = rs.getDate("fecha_checkin").toLocalDate();
                LocalDate salida = rs.getDate("fecha_checkout").toLocalDate();
                BigDecimal montoTotal = rs.getBigDecimal("monto_total");
                idHuesped = rs.getInt("id_huesped");

                cuenta.setIdReserva(rs.getInt("id_reserva"));
                cuenta.setIdHabitacion(idHabitacion);
                cuenta.setIdHuesped(idHuesped);
                cuenta.setIngreso(ingreso);
                cuenta.setHuesped(rs.getString("nombres") + " " + rs.getString("apellidos"));
                cuenta.setTipoDocumento(rs.getString("tipo_documento"));
                cuenta.setNumDocumento(rs.getString("num_documento"));
                cuenta.setTelefono(rs.getString("telefono"));
                if (rs.getString("razon_social") != null) {
                    cuenta.setEmpresa(rs.getString("razon_social") + " (RUC " + rs.getString("ruc") + ")");
                }
                cuenta.setFechaIngreso(ingreso.format(FECHA));
                cuenta.setFechaSalida(salida.format(FECHA));
                cuenta.setSalida(salida);

                // Alojamiento: una fila por noche, desde el ingreso hasta la salida, a la tarifa pactada
                int noches = (int) ChronoUnit.DAYS.between(ingreso, salida);
                BigDecimal porNoche = montoTotal.divide(BigDecimal.valueOf(noches), 2, RoundingMode.HALF_UP);
                cuenta.setTarifaNoche(porNoche);
                cuenta.setNoches(noches);
                BigDecimal acumulado = BigDecimal.ZERO;
                for (int i = 0; i < noches; i++) {
                    LocalDate desde = ingreso.plusDays(i);
                    // la ultima noche absorbe los centimos de redondeo para que el total coincida con la reserva
                    BigDecimal importe = (i == noches - 1) ? montoTotal.subtract(acumulado) : porNoche;
                    acumulado = acumulado.add(importe);
                    cuenta.getCargos().add(new CuentaHabitacion.Linea(desde.format(FECHA), desde.plusDays(1).format(FECHA), "Alojamiento",
                            "Noche " + (i + 1), 1, importe, importe));
                }
            }
        }

        // Consumos de la tienda cargados a la habitacion (venta sin comprobante = aun no cobrada)
        String sqlConsumos = "SELECT v.fecha_venta, p.nombre, d.cantidad, d.precio_unitario, d.subtotal "
                + "FROM venta_tienda v JOIN detalle_venta d ON d.id_venta = v.id_venta "
                + "JOIN producto p ON p.id_producto = d.id_producto "
                + "WHERE v.id_habitacion = ? AND v.id_huesped = ? AND v.id_comprobante IS NULL "
                + "AND v.fecha_venta >= ? ORDER BY v.fecha_venta";
        try (PreparedStatement ps = con.prepareStatement(sqlConsumos)) {
            ps.setInt(1, idHabitacion);
            ps.setInt(2, idHuesped);
            ps.setDate(3, Date.valueOf(ingreso));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cuenta.getCargos().add(new CuentaHabitacion.Linea(
                            rs.getTimestamp("fecha_venta").toLocalDateTime().format(FECHA), "—", "Tienda",
                            rs.getString("nombre"), rs.getInt("cantidad"),
                            rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("subtotal")));
                }
            }
        }

        String sqlPagos = "SELECT fecha_pago, tipo_pago, metodo_pago, monto FROM pago "
                + "WHERE id_reserva = ? ORDER BY fecha_pago, id_pago";
        try (PreparedStatement ps = con.prepareStatement(sqlPagos)) {
            ps.setInt(1, cuenta.getIdReserva());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cuenta.getPagos().add(new CuentaHabitacion.PagoLinea(
                            rs.getTimestamp("fecha_pago").toLocalDateTime().format(FECHA),
                            capitalizar(rs.getString("tipo_pago")),
                            capitalizar(rs.getString("metodo_pago")),
                            rs.getBigDecimal("monto")));
                }
            }
        }
        return cuenta;
    }

    private String capitalizar(String texto) {
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }
}
