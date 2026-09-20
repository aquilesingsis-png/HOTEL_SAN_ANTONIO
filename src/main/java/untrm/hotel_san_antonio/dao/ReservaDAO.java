package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Reserva;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;

public class ReservaDAO {

    /** true si la habitacion ya tiene una reserva vigente que se cruza con esas fechas. */
    public boolean existeCruce(Connection con, int idHabitacion, LocalDate ingreso, LocalDate salida) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reserva WHERE id_habitacion = ? "
                + "AND estado IN ('PENDIENTE','CONFIRMADA','CHECKIN') "
                + "AND fecha_checkin < ? AND fecha_checkout > ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idHabitacion);
            ps.setDate(2, Date.valueOf(salida));
            ps.setDate(3, Date.valueOf(ingreso));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Fecha de ingreso de la primera reserva vigente de la habitacion que se cruza con el rango
     * [desde, hasta), o null si el rango esta libre. La estadia en curso no cuenta si se pide desde su salida.
     */
    public LocalDate primerCruce(Connection con, int idHabitacion, LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT MIN(fecha_checkin) FROM reserva WHERE id_habitacion = ? "
                + "AND estado IN ('PENDIENTE','CONFIRMADA','CHECKIN') "
                + "AND fecha_checkin < ? AND fecha_checkout > ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idHabitacion);
            ps.setDate(2, Date.valueOf(hasta));
            ps.setDate(3, Date.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Date fecha = rs.getDate(1);
                return fecha == null ? null : fecha.toLocalDate();
            }
        }
    }

    /** Extiende la estadia: nueva fecha de salida y el monto de las noches agregadas se suma al total. */
    public void ampliar(Connection con, int idReserva, LocalDate nuevaSalida, java.math.BigDecimal incremento) throws SQLException {
        String sql = "UPDATE reserva SET fecha_checkout = ?, monto_total = monto_total + ? WHERE id_reserva = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(nuevaSalida));
            ps.setBigDecimal(2, incremento);
            ps.setInt(3, idReserva);
            ps.executeUpdate();
        }
    }

    /** Id de la reserva CONFIRMADA que corresponde recibir hoy en la habitacion, o -1 si no hay. */
    public int buscarParaCheckin(Connection con, int idHabitacion, LocalDate hoy) throws SQLException {
        String sql = "SELECT id_reserva FROM reserva WHERE id_habitacion = ? AND estado = 'CONFIRMADA' "
                + "AND fecha_checkin <= ? AND fecha_checkout > ? ORDER BY fecha_checkin LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idHabitacion);
            ps.setDate(2, Date.valueOf(hoy));
            ps.setDate(3, Date.valueOf(hoy));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public void actualizarEstado(Connection con, int idReserva, String estado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE reserva SET estado = ? WHERE id_reserva = ?")) {
            ps.setString(1, estado);
            ps.setInt(2, idReserva);
            ps.executeUpdate();
        }
    }

    /** Cierra la estadia: la reserva pasa a FINALIZADA. */
    public void finalizar(Connection con, int idReserva) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE reserva SET estado = 'FINALIZADA' WHERE id_reserva = ?")) {
            ps.setInt(1, idReserva);
            ps.executeUpdate();
        }
    }

    /** Inserta la reserva y devuelve el id generado. */
    public int insertar(Connection con, Reserva r) throws SQLException {
        String sql = "INSERT INTO reserva (id_huesped, id_habitacion, id_usuario, fecha_checkin, fecha_checkout, "
                + "adelanto, monto_total, estado, canal, id_empresa) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getIdHuesped());
            ps.setInt(2, r.getIdHabitacion());
            ps.setInt(3, r.getIdUsuario());
            ps.setDate(4, Date.valueOf(r.getFechaCheckin()));
            ps.setDate(5, Date.valueOf(r.getFechaCheckout()));
            ps.setBigDecimal(6, r.getAdelanto());
            ps.setBigDecimal(7, r.getMontoTotal());
            ps.setString(8, r.getEstado());
            ps.setString(9, r.getCanal());
            if (r.getIdEmpresa() == null) {
                ps.setNull(10, Types.INTEGER);
            } else {
                ps.setInt(10, r.getIdEmpresa());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
