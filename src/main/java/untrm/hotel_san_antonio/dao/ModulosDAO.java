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
    public record CierreDatos(LocalDate fecha, String turno, BigDecimal fondo, BigDecimal cobros,
                              BigDecimal otros, BigDecimal salidas, BigDecimal esperado,
                              BigDecimal contado, BigDecimal entregado, BigDecimal siguiente,
                              String observacion) { }
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

    /** Componentes de efectivo para el cierre de una fecha y turno. */
    public BigDecimal[] resumenEfectivo(LocalDate fecha, String turno) throws SQLException {
        if (fecha == null) throw new IllegalArgumentException("Seleccione la fecha del cierre.");
        String sql = "SELECT "
                + "COALESCE((SELECT fondo_siguiente FROM cierre_caja c WHERE c.estado='CONFIRMADO' "
                + "AND (c.fecha<? OR (c.fecha=? AND c.turno<>?)) ORDER BY c.fecha DESC,c.id_cierre DESC LIMIT 1),0),"
                + "COALESCE((SELECT SUM(monto) FROM pago WHERE metodo_pago='EFECTIVO' AND COALESCE(fecha_evento, DATE(fecha_pago))=?),0) + "
                + "COALESCE((SELECT SUM(total) FROM venta_tienda WHERE id_habitacion IS NULL AND metodo_pago='EFECTIVO' "
                + "AND COALESCE(fecha_evento, DATE(fecha_venta))=?),0),"
                + "COALESCE((SELECT SUM(monto) FROM movimiento_caja WHERE tipo='INGRESO' "
                + "AND metodo_pago='EFECTIVO' AND DATE(fecha)=?),0),"
                + "COALESCE((SELECT SUM(monto) FROM movimiento_caja WHERE tipo='EGRESO' "
                + "AND metodo_pago='EFECTIVO' AND DATE(fecha)=?),0) + "
                + "COALESCE((SELECT SUM(monto) FROM gasto WHERE activo=1 AND metodo_pago='EFECTIVO' AND fecha=?),0)";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha)); ps.setDate(2, Date.valueOf(fecha));
            ps.setString(3, turno == null ? "" : turno.trim());
            for (int i = 4; i <= 8; i++) ps.setDate(i, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4)};
            }
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

    public void guardarArqueo(String turno, BigDecimal esperado, BigDecimal contado,
                              String observacion) throws SQLException {
        admin();
        if (turno == null || turno.isBlank() || turno.length() > 50)
            throw new IllegalArgumentException("Indique un turno de hasta 50 caracteres.");
        validarImporte(esperado, "El efectivo esperado");
        validarImporte(contado, "El efectivo contado");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO arqueo_caja(id_usuario,turno,efectivo_esperado,efectivo_contado,diferencia,observacion) "
                    + "VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, usuario()); ps.setString(2, turno.trim()); ps.setBigDecimal(3, esperado);
                ps.setBigDecimal(4, contado); ps.setBigDecimal(5, contado.subtract(esperado));
                ps.setString(6, observacion); ps.executeUpdate();
                auditoria.registrar(con, usuario(), "CAJA_ARQUEO", "arqueo_caja", claveGenerada(ps),
                        "Diferencia: " + contado.subtract(esperado));
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void guardarCierre(Integer id, CierreDatos datos, boolean confirmar) throws SQLException {
        admin();
        if (datos.fecha() == null || datos.fecha().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("La fecha del cierre no puede ser futura.");
        if (datos.turno() == null || datos.turno().isBlank() || datos.turno().length() > 50)
            throw new IllegalArgumentException("Indique un turno de hasta 50 caracteres.");
        for (BigDecimal valor : List.of(datos.fondo(), datos.cobros(), datos.otros(), datos.salidas(),
                datos.esperado(), datos.contado(), datos.entregado(), datos.siguiente())) {
            validarImporte(valor, "Los importes del cierre");
        }
        BigDecimal calculado = datos.fondo().add(datos.cobros()).add(datos.otros()).subtract(datos.salidas());
        if (calculado.compareTo(datos.esperado()) != 0)
            throw new IllegalArgumentException("El efectivo esperado no coincide con fondo + cobros + ingresos - salidas.");
        if (datos.entregado().add(datos.siguiente()).compareTo(datos.contado()) != 0)
            throw new IllegalArgumentException("Efectivo entregado + fondo siguiente debe igualar el contado.");
        if (datos.observacion() != null && datos.observacion().length() > 500)
            throw new IllegalArgumentException("La observación del cierre supera 500 caracteres.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (id == null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id_cierre,estado FROM cierre_caja WHERE fecha=? AND turno=? FOR UPDATE")) {
                        ps.setDate(1, Date.valueOf(datos.fecha()));
                        ps.setString(2, datos.turno().trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                if ("CONFIRMADO".equals(rs.getString(2)))
                                    throw new IllegalStateException("El cierre de ese día y turno ya está confirmado.");
                                id = rs.getInt(1);
                            }
                        }
                    }
                }
                if (id != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT estado FROM cierre_caja WHERE id_cierre=? FOR UPDATE")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalStateException("El cierre ya no existe.");
                            if ("CONFIRMADO".equals(rs.getString(1)))
                                throw new IllegalStateException("Un cierre confirmado no se puede modificar.");
                        }
                    }
                }
                String sql = id == null
                        ? "INSERT INTO cierre_caja(id_usuario,fecha,turno,fondo_inicial,cobros_efectivo,otros_ingresos,"
                          + "salidas_efectivo,efectivo_esperado,efectivo_contado,diferencia,efectivo_entregado,"
                          + "fondo_siguiente,observacion,estado) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        : "UPDATE cierre_caja SET id_usuario=?,fecha=?,turno=?,fondo_inicial=?,cobros_efectivo=?,"
                          + "otros_ingresos=?,salidas_efectivo=?,efectivo_esperado=?,efectivo_contado=?,diferencia=?,"
                          + "efectivo_entregado=?,fondo_siguiente=?,observacion=?,estado=? WHERE id_cierre=?";
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, usuario()); ps.setDate(2, Date.valueOf(datos.fecha()));
                    ps.setString(3, datos.turno().trim()); ps.setBigDecimal(4, datos.fondo());
                    ps.setBigDecimal(5, datos.cobros()); ps.setBigDecimal(6, datos.otros());
                    ps.setBigDecimal(7, datos.salidas()); ps.setBigDecimal(8, datos.esperado());
                    ps.setBigDecimal(9, datos.contado());
                    ps.setBigDecimal(10, datos.contado().subtract(datos.esperado()));
                    ps.setBigDecimal(11, datos.entregado()); ps.setBigDecimal(12, datos.siguiente());
                    ps.setString(13, datos.observacion());
                    ps.setString(14, confirmar ? "CONFIRMADO" : "BORRADOR");
                    if (id != null) ps.setInt(15, id);
                    if (ps.executeUpdate() != 1) throw new SQLException("No se guardó el cierre.");
                    int afectado = id == null ? claveGenerada(ps) : id;
                    auditoria.registrar(con, usuario(), confirmar ? "CAJA_CIERRE" : "CAJA_BORRADOR",
                            "cierre_caja", afectado, datos.fecha() + " / " + datos.turno());
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }
}
