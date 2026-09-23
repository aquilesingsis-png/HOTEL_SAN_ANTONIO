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
import java.util.ArrayList;
import java.util.List;

public class ReservaDAO {

    /** Columnas que se traen siempre que se necesita mostrar una reserva (con el huesped y la habitacion). */
    private static final String SELECT_BASE =
            "SELECT r.*, "
            + "hu.tipo_documento AS hu_tipo_doc, hu.num_documento AS hu_num_doc, "
            + "hu.nombres AS hu_nombres, hu.apellidos AS hu_apellidos, "
            + "hu.telefono AS hu_telefono, hu.email AS hu_email, "
            + "h.numero AS hab_numero, h.piso AS hab_piso, t.nombre AS hab_tipo "
            + "FROM reserva r "
            + "JOIN huesped hu ON hu.id_huesped = r.id_huesped "
            + "JOIN habitacion h ON h.id_habitacion = r.id_habitacion "
            + "JOIN tipo_habitacion t ON t.id_tipo = h.id_tipo ";

    private Reserva mapearFila(ResultSet rs) throws SQLException {
        Reserva r = new Reserva();
        r.setIdReserva(rs.getInt("id_reserva"));
        r.setIdHuesped(rs.getInt("id_huesped"));
        r.setIdHabitacion(rs.getInt("id_habitacion"));
        r.setIdUsuario(rs.getInt("id_usuario"));
        java.sql.Timestamp fechaReserva = rs.getTimestamp("fecha_reserva");
        if (fechaReserva != null) {
            r.setFechaReserva(fechaReserva.toLocalDateTime());
        }
        r.setFechaCheckin(rs.getDate("fecha_checkin").toLocalDate());
        r.setFechaCheckout(rs.getDate("fecha_checkout").toLocalDate());
        r.setAdelanto(rs.getBigDecimal("adelanto"));
        r.setMontoTotal(rs.getBigDecimal("monto_total"));
        r.setEstado(rs.getString("estado"));
        r.setCanal(rs.getString("canal"));
        int idEmpresa = rs.getInt("id_empresa");
        r.setIdEmpresa(rs.wasNull() ? null : idEmpresa);
        r.setMotivoCancelacion(rs.getString("motivo_cancelacion"));
        r.setDetalleCancelacion(rs.getString("detalle_cancelacion"));
        r.setNumHuespedes(rs.getInt("num_huespedes"));
        java.sql.Time horaCheckin = rs.getTime("hora_checkin");
        if (horaCheckin != null) {
            r.setHoraCheckin(horaCheckin.toLocalTime());
        }

        r.setTipoDocumentoHuesped(rs.getString("hu_tipo_doc"));
        r.setNumDocumentoHuesped(rs.getString("hu_num_doc"));
        r.setNombreHuesped((rs.getString("hu_nombres") + " " + rs.getString("hu_apellidos")).trim());
        r.setTelefonoHuesped(rs.getString("hu_telefono"));
        r.setEmailHuesped(rs.getString("hu_email"));
        r.setNumeroHabitacion(rs.getString("hab_numero"));
        r.setPisoHabitacion(rs.getInt("hab_piso"));
        r.setNombreTipoHabitacion(rs.getString("hab_tipo"));
        return r;
    }

    /**
     * Busca reservas para la lista de "Reservas programadas".
     * @param texto     filtra por codigo (numero de reserva), nombre/apellido, documento o numero de habitacion; null o vacio = sin filtro
     * @param estado    ESTADO exacto, "POR_LLEGAR" (confirmada con ingreso hoy o despues) o null/vacio para todas
     * @param desde     fecha de ingreso minima, o null
     * @param hasta     fecha de ingreso maxima, o null
     */
    public List<Reserva> buscar(Connection con, String texto, String estado, LocalDate desde, LocalDate hasta) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1 ");
        List<Object> parametros = new ArrayList<>();

        if (texto != null && !texto.isBlank()) {
            String comodin = "%" + texto.trim() + "%";
            sql.append("AND (CONCAT('R-', LPAD(r.id_reserva, 4, '0')) LIKE ? "
                    + "OR CONCAT(hu.nombres, ' ', hu.apellidos) LIKE ? "
                    + "OR hu.num_documento LIKE ? OR h.numero LIKE ?) ");
            parametros.add(comodin);
            parametros.add(comodin);
            parametros.add(comodin);
            parametros.add(comodin);
        }
        if ("POR_LLEGAR".equals(estado)) {
            sql.append("AND r.estado = 'CONFIRMADA' AND r.fecha_checkin >= CURDATE() ");
        } else if (estado != null && !estado.isBlank()) {
            sql.append("AND r.estado = ? ");
            parametros.add(estado);
        }
        if (desde != null) {
            sql.append("AND r.fecha_checkin >= ? ");
            parametros.add(Date.valueOf(desde));
        }
        if (hasta != null) {
            sql.append("AND r.fecha_checkin <= ? ");
            parametros.add(Date.valueOf(hasta));
        }
        sql.append("ORDER BY r.fecha_checkin DESC, r.id_reserva DESC");

        List<Reserva> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearFila(rs));
                }
            }
        }
        return lista;
    }

    /** Busca UNA reserva por codigo, nombre, documento o numero de habitacion (para Confirmar/Cancelar). */
    public Reserva buscarUno(Connection con, String texto) throws SQLException {
        Integer idExacto = extraerIdCodigo(texto);
        if (idExacto != null) {
            try (PreparedStatement ps = con.prepareStatement(SELECT_BASE + "WHERE r.id_reserva = ?")) {
                ps.setInt(1, idExacto);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapearFila(rs);
                    }
                }
            }
        }
        List<Reserva> resultado = buscar(con, texto, null, null, null);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    /** Si "texto" es un codigo de reserva ("R-0005", "R5", "5", "#5"), devuelve el id; si no, null. */
    private Integer extraerIdCodigo(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replaceAll("(?i)^R-?", "").replace("#", "");
        return limpio.matches("\\d+") ? Integer.valueOf(limpio) : null;
    }

    /** Reservas CONFIRMADA o CHECKIN que se cruzan con el rango [desde, hasta], para el calendario de ocupacion. */
    public List<Reserva> listarEnRango(Connection con, LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT id_habitacion, fecha_checkin, fecha_checkout, estado FROM reserva "
                + "WHERE estado IN ('CONFIRMADA','CHECKIN') AND fecha_checkin <= ? AND fecha_checkout > ?";
        List<Reserva> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(hasta));
            ps.setDate(2, Date.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reserva r = new Reserva();
                    r.setIdHabitacion(rs.getInt("id_habitacion"));
                    r.setFechaCheckin(rs.getDate("fecha_checkin").toLocalDate());
                    r.setFechaCheckout(rs.getDate("fecha_checkout").toLocalDate());
                    r.setEstado(rs.getString("estado"));
                    lista.add(r);
                }
            }
        }
        return lista;
    }

    /** Cuantos huespedes hay hospedados ahora mismo (suma de num_huespedes de las reservas en CHECKIN). */
    public int sumarHuespedesActivos(Connection con) throws SQLException {
        String sql = "SELECT COALESCE(SUM(num_huespedes), 0) FROM reserva WHERE estado = 'CHECKIN'";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Cuantas habitaciones (y cuantos huespedes) tenian una estadia en curso en una fecha pasada,
     * para comparar la ocupacion de hoy contra la de ayer en el Dashboard.
     */
    public int contarOcupadasEnFecha(Connection con, LocalDate fecha) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT id_habitacion) FROM reserva "
                + "WHERE estado IN ('CHECKIN','FINALIZADA') AND fecha_checkin <= ? AND fecha_checkout > ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            ps.setDate(2, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int sumarHuespedesEnFecha(Connection con, LocalDate fecha) throws SQLException {
        String sql = "SELECT COALESCE(SUM(num_huespedes), 0) FROM reserva "
                + "WHERE estado IN ('CHECKIN','FINALIZADA') AND fecha_checkin <= ? AND fecha_checkout > ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            ps.setDate(2, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Reservas cuyo ingreso fue exactamente esa fecha (para comparar las llegadas de hoy contra las de ayer). */
    public int contarLlegadasEnFecha(Connection con, LocalDate fecha) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reserva WHERE fecha_checkin = ? "
                + "AND estado IN ('CONFIRMADA','CHECKIN','FINALIZADA')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Reservas que llegan hoy (confirmadas o que ya hicieron check-in hoy), para el Dashboard. */
    public List<Reserva> listarLlegadasHoy(Connection con) throws SQLException {
        String sql = SELECT_BASE + "WHERE r.fecha_checkin = CURDATE() AND r.estado IN ('CONFIRMADA','CHECKIN') "
                + "ORDER BY r.estado, hu.apellidos";
        List<Reserva> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearFila(rs));
            }
        }
        return lista;
    }

    /** Pasa una reserva PENDIENTE a CANCELADA (o CONFIRMADA a CANCELADA), guardando el motivo. */
    public void cancelar(Connection con, int idReserva, String motivo, String detalle) throws SQLException {
        String sql = "UPDATE reserva SET estado = 'CANCELADA', motivo_cancelacion = ?, detalle_cancelacion = ? "
                + "WHERE id_reserva = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, motivo);
            ps.setString(2, detalle);
            ps.setInt(3, idReserva);
            ps.executeUpdate();
        }
    }

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
                + "adelanto, monto_total, estado, canal, id_empresa, num_huespedes, hora_checkin) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setInt(11, r.getNumHuespedes() <= 0 ? 1 : r.getNumHuespedes());
            if (r.getHoraCheckin() == null) {
                ps.setNull(12, Types.TIME);
            } else {
                ps.setTime(12, java.sql.Time.valueOf(r.getHoraCheckin()));
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
