package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/** Consultas y cambios de las pantallas administrativas antiguas. */
public class ModulosDAO {
    public record Registro(int id, List<String> celdas, LocalDate fecha) { }
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    private int usuario() {
        return SesionActual.getUsuario().getIdUsuario();
    }

    private void admin() {
        Permisos.requerir("ADMINISTRADOR");
    }

    private void validarImporte(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0 || valor.scale() > 2
                || valor.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException(campo + " debe ser un importe no negativo con hasta dos decimales.");
        }
    }

    private static final class OcupacionDia {
        int disponibles;
        final Set<Integer> habitaciones = new HashSet<>();
        int reservas;
        int huespedes;
    }

    /** Cuenta todas las noches del período, incluidas las de ocupación cero. */
    public List<Registro> ocupacionDiaria(LocalDate desde, LocalDate hasta) throws SQLException {
        LocalDate inicio = desde == null ? LocalDate.now().minusDays(29) : desde;
        LocalDate fin = hasta == null ? LocalDate.now() : hasta;
        long noches = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (noches <= 0 || noches > 3660)
            throw new IllegalArgumentException("El período de ocupación debe tener entre 1 y 3660 días.");
        TreeMap<LocalDate, Map<String, OcupacionDia>> porFecha = new TreeMap<>();
        try (Connection con = ConexionBD.conectar()) {
            Map<String, Integer> inventario = new TreeMap<>();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT t.nombre,COUNT(h.id_habitacion) FROM tipo_habitacion t "
                    + "LEFT JOIN habitacion h ON h.id_tipo=t.id_tipo GROUP BY t.id_tipo,t.nombre")) {
                while (rs.next()) if (rs.getInt(2) > 0) inventario.put(rs.getString(1), rs.getInt(2));
            }
            for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
                Map<String, OcupacionDia> tipos = new TreeMap<>();
                for (var tipo : inventario.entrySet()) {
                    OcupacionDia dia = new OcupacionDia();
                    dia.disponibles = tipo.getValue();
                    tipos.put(tipo.getKey(), dia);
                }
                porFecha.put(fecha, tipos);
            }
            String sql = "SELECT r.fecha_checkin,r.fecha_checkout,r.num_huespedes,h.id_habitacion,t.nombre "
                    + "FROM reserva r JOIN habitacion h ON h.id_habitacion=r.id_habitacion "
                    + "JOIN tipo_habitacion t ON t.id_tipo=h.id_tipo "
                    + "WHERE r.estado NOT IN ('CANCELADA','PENDIENTE') "
                    + "AND r.fecha_checkin<=? AND r.fecha_checkout>?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(fin));
                ps.setDate(2, Date.valueOf(inicio));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate ingreso = rs.getDate(1).toLocalDate();
                        LocalDate salida = rs.getDate(2).toLocalDate();
                        for (LocalDate fecha = ingreso.isBefore(inicio) ? inicio : ingreso;
                             fecha.isBefore(salida) && !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
                            OcupacionDia dia = porFecha.get(fecha).get(rs.getString(5));
                            dia.habitaciones.add(rs.getInt(4));
                            dia.reservas++;
                            dia.huespedes += rs.getInt(3);
                        }
                    }
                }
            }
        }
        List<Registro> resultado = new ArrayList<>();
        for (var fecha : porFecha.descendingMap().entrySet()) {
            for (var tipo : fecha.getValue().entrySet()) {
                OcupacionDia dia = tipo.getValue();
                int ocupadas = dia.habitaciones.size();
                String porcentaje = dia.disponibles == 0 ? "0.0" : BigDecimal.valueOf(ocupadas * 100L)
                        .divide(BigDecimal.valueOf(dia.disponibles), 1, RoundingMode.HALF_UP).toPlainString();
                resultado.add(new Registro(0, List.of(fecha.getKey().toString(), tipo.getKey(),
                        String.valueOf(dia.disponibles), String.valueOf(ocupadas), porcentaje,
                        String.valueOf(dia.reservas), String.valueOf(dia.huespedes)), fecha.getKey()));
            }
        }
        return resultado;
    }

    public List<String> categorias() throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM categoria ORDER BY nombre")) {
            while (rs.next()) lista.add(rs.getString(1));
        }
        return lista;
    }

    public List<String> tiposHabitacion() throws SQLException {
        List<String> tipos = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM tipo_habitacion ORDER BY nombre")) {
            while (rs.next()) tipos.add(rs.getString(1));
        }
        return tipos;
    }

    public List<String> responsables() throws SQLException {
        List<String> nombres = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONCAT(nombre,' ',apellido) FROM usuario ORDER BY nombre,apellido")) {
            while (rs.next()) nombres.add(rs.getString(1));
        }
        return nombres;
    }

    public BigDecimal efectivoHoy() throws SQLException {
        String sql = "SELECT COALESCE((SELECT SUM(monto) FROM pago WHERE metodo_pago='EFECTIVO' "
                + "AND COALESCE(fecha_evento, DATE(fecha_pago))=CURDATE()),0) + COALESCE((SELECT SUM(total) FROM venta_tienda WHERE id_habitacion IS NULL "
                + "AND metodo_pago='EFECTIVO' AND COALESCE(fecha_evento, DATE(fecha_venta))=CURDATE()),0) + COALESCE((SELECT SUM(monto) FROM movimiento_caja "
                + "WHERE tipo='INGRESO' AND metodo_pago='EFECTIVO' AND DATE(fecha)=CURDATE()),0) "
                + "- COALESCE((SELECT SUM(monto) FROM gasto WHERE activo=1 AND metodo_pago='EFECTIVO' "
                + "AND fecha=CURDATE()),0) - COALESCE((SELECT SUM(monto) FROM movimiento_caja "
                + "WHERE tipo='EGRESO' AND metodo_pago='EFECTIVO' AND DATE(fecha)=CURDATE()),0)";
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next(); return rs.getBigDecimal(1);
        }
    }

    private int claveGenerada(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) throw new SQLException("No se obtuvo el identificador generado.");
            return rs.getInt(1);
        }
    }

    public void guardarMovimientoCaja(String tipo, String concepto, String metodo,
                                      BigDecimal monto, String referencia, String observacion,
                                      String turno) throws SQLException {
        admin();
        if (!List.of("INGRESO", "EGRESO").contains(tipo))
            throw new IllegalArgumentException("Seleccione ingreso o egreso.");
        if (concepto == null || concepto.isBlank() || concepto.length() > 150)
            throw new IllegalArgumentException("Indique un concepto de hasta 150 caracteres.");
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE").contains(metodo))
            throw new IllegalArgumentException("Seleccione un método de pago válido.");
        validarImporte(monto, "El monto");
        if (monto.signum() == 0) throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        if (turno == null || turno.isBlank() || turno.length() > 50)
            throw new IllegalArgumentException("Indique el turno o caja (hasta 50 caracteres).");
        String nota = "Turno/caja: " + turno.trim()
                + (observacion == null || observacion.isBlank() ? "" : "\n" + observacion.trim());
        if (nota.length() > 500) throw new IllegalArgumentException("La observación supera 500 caracteres.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO movimiento_caja(id_usuario,tipo,concepto,metodo_pago,monto,referencia,observacion) "
                    + "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, usuario()); ps.setString(2, tipo); ps.setString(3, concepto.trim());
                ps.setString(4, metodo); ps.setBigDecimal(5, monto);
                ps.setString(6, referencia); ps.setString(7, nota);
                ps.executeUpdate();
                auditoria.registrar(con, usuario(), "CAJA_MOVIMIENTO", "movimiento_caja",
                        claveGenerada(ps), tipo + " " + monto + " " + concepto.trim());
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }
}
