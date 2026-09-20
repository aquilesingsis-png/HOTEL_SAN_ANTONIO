package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.TipoHabitacion;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HabitacionDAO {

    /** Todas las habitaciones con su tipo y, si estan ocupadas, el huesped actual. */
    public List<Habitacion> listar() throws SQLException {
        String sql = "SELECT h.id_habitacion, h.numero, h.id_tipo, h.piso, h.estado, "
                + "t.nombre AS tipo_nombre, t.capacidad, t.precio_base, "
                + "(SELECT CONCAT(hu.nombres, ' ', hu.apellidos) "
                + "   FROM reserva r JOIN huesped hu ON hu.id_huesped = r.id_huesped "
                + "  WHERE r.id_habitacion = h.id_habitacion AND r.estado = 'CHECKIN' "
                + "  ORDER BY r.fecha_checkin DESC LIMIT 1) AS huesped_actual, "
                + "(SELECT CONCAT(hu.nombres, ' ', hu.apellidos) "
                + "   FROM reserva r JOIN huesped hu ON hu.id_huesped = r.id_huesped "
                + "  WHERE r.id_habitacion = h.id_habitacion AND r.estado = 'CONFIRMADA' "
                + "    AND r.fecha_checkin <= CURDATE() AND r.fecha_checkout > CURDATE() "
                + "  ORDER BY r.fecha_checkin LIMIT 1) AS reserva_hoy "
                + "FROM habitacion h JOIN tipo_habitacion t ON t.id_tipo = h.id_tipo "
                + "ORDER BY h.piso, h.numero";

        List<Habitacion> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Habitacion h = new Habitacion(
                        rs.getInt("id_habitacion"),
                        rs.getString("numero"),
                        rs.getInt("id_tipo"),
                        rs.getInt("piso"),
                        rs.getString("estado"));
                h.setTipo(new TipoHabitacion(
                        rs.getInt("id_tipo"),
                        rs.getString("tipo_nombre"),
                        rs.getInt("capacidad"),
                        rs.getBigDecimal("precio_base")));
                h.setHuespedActual(rs.getString("huesped_actual"));
                h.setReservaHoy(rs.getString("reserva_hoy"));
                lista.add(h);
            }
        }
        return lista;
    }

    public void cambiarEstado(int idHabitacion, String nuevoEstado) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            cambiarEstado(con, idHabitacion, nuevoEstado);
        }
    }

    public String obtenerEstado(Connection con, int idHabitacion) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT estado FROM habitacion WHERE id_habitacion = ?")) {
            ps.setInt(1, idHabitacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Cambia el estado solo si la habitacion sigue en el estado esperado (evita pisar un cambio hecho
     * por otra persona mientras esta pantalla estaba abierta). Devuelve false si el estado ya era otro.
     */
    public boolean cambiarEstadoSiEs(int idHabitacion, String estadoEsperado, String nuevoEstado) throws SQLException {
        String sql = "UPDATE habitacion SET estado = ? WHERE id_habitacion = ? AND estado = ?";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idHabitacion);
            ps.setString(3, estadoEsperado);
            return ps.executeUpdate() > 0;
        }
    }

    public void cambiarEstado(Connection con, int idHabitacion, String nuevoEstado) throws SQLException {
        String sql = "UPDATE habitacion SET estado = ? WHERE id_habitacion = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idHabitacion);
            ps.executeUpdate();
        }
    }
}
