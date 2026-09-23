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
        try (Connection con = ConexionBD.conectar()) {
            return listar(con);
        }
    }

    public List<Habitacion> listar(Connection con) throws SQLException {
        String sql = "SELECT h.id_habitacion, h.numero, h.id_tipo, h.piso, h.estado, h.motivo_mantenimiento, "
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
        try (PreparedStatement ps = con.prepareStatement(sql);
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
                h.setMotivoMantenimiento(rs.getString("motivo_mantenimiento"));
                lista.add(h);
            }
        }
        return lista;
    }

    /**
     * Habitaciones que NO estan en mantenimiento y no tienen ninguna reserva vigente que se
     * cruce con [ingreso, salida). Se usa para elegir habitacion en una reserva nueva.
     */
    public List<Habitacion> buscarDisponibles(Connection con, java.time.LocalDate ingreso, java.time.LocalDate salida,
                                               Integer piso, String tipoNombre) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT h.id_habitacion, h.numero, h.id_tipo, h.piso, h.estado, "
                + "t.nombre AS tipo_nombre, t.capacidad, t.precio_base "
                + "FROM habitacion h JOIN tipo_habitacion t ON t.id_tipo = h.id_tipo "
                + "WHERE h.estado <> 'MANTENIMIENTO' "
                + "AND NOT EXISTS (SELECT 1 FROM reserva r WHERE r.id_habitacion = h.id_habitacion "
                + "  AND r.estado IN ('PENDIENTE','CONFIRMADA','CHECKIN') "
                + "  AND r.fecha_checkin < ? AND r.fecha_checkout > ?) ");
        List<Object> parametros = new ArrayList<>();
        parametros.add(java.sql.Date.valueOf(salida));
        parametros.add(java.sql.Date.valueOf(ingreso));
        if (piso != null) {
            sql.append("AND h.piso = ? ");
            parametros.add(piso);
        }
        if (tipoNombre != null && !tipoNombre.isBlank()) {
            sql.append("AND t.nombre = ? ");
            parametros.add(tipoNombre);
        }
        sql.append("ORDER BY h.piso, h.numero");

        List<Habitacion> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
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
                    lista.add(h);
                }
            }
        }
        return lista;
    }

    /** Busca una habitacion por su numero (ej. "207"), o null si no existe. */
    public Habitacion buscarPorNumero(Connection con, String numero) throws SQLException {
        String sql = "SELECT h.id_habitacion, h.numero, h.id_tipo, h.piso, h.estado, "
                + "t.nombre AS tipo_nombre, t.capacidad, t.precio_base "
                + "FROM habitacion h JOIN tipo_habitacion t ON t.id_tipo = h.id_tipo WHERE h.numero = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
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
                return h;
            }
        }
    }

    /** Los nombres de tipo de habitacion en uso (para el filtro de tipos). */
    public List<String> listarNombresTipo(Connection con) throws SQLException {
        List<String> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT nombre FROM tipo_habitacion ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        }
        return lista;
    }

    /** Los pisos en uso (para el filtro de pisos). */
    public List<Integer> listarPisos(Connection con) throws SQLException {
        List<Integer> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT DISTINCT piso FROM habitacion ORDER BY piso");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(rs.getInt(1));
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
     * Al salir de MANTENIMIENTO (o entrar sin dar motivo) se borra el motivo anterior.
     */
    public boolean cambiarEstadoSiEs(int idHabitacion, String estadoEsperado, String nuevoEstado) throws SQLException {
        return cambiarEstadoSiEs(idHabitacion, estadoEsperado, nuevoEstado, null);
    }

    /** Igual que cambiarEstadoSiEs, pero dejando registrado el motivo (ej. al entrar a MANTENIMIENTO). */
    public boolean cambiarEstadoSiEs(int idHabitacion, String estadoEsperado, String nuevoEstado, String motivoMantenimiento) throws SQLException {
        String sql = "UPDATE habitacion SET estado = ?, motivo_mantenimiento = ? WHERE id_habitacion = ? AND estado = ?";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setString(2, motivoMantenimiento);
            ps.setInt(3, idHabitacion);
            ps.setString(4, estadoEsperado);
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
