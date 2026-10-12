package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Cierre de caja por turno. Cada cierre cuenta el efectivo que entro y salio desde el cierre confirmado
 * anterior (sea de la mañana, tarde o noche), asi un turno que cruza la medianoche cuenta completo y
 * ningun cobro se cuenta dos veces. El sistema calcula los importes: quien cierra solo cuenta el efectivo.
 * La caja chica es el efectivo fijo con el que empieza cada turno: lo que queda en la caja al cerrar es la
 * caja chica del turno siguiente.
 */
public class CierreCajaDAO {

    /** Lo que el sistema calcula para el turno que se va a cerrar. */
    public record Resumen(BigDecimal fondoInicial, BigDecimal cobros, BigDecimal otros, BigDecimal salidas,
                          LocalDateTime desde, LocalDateTime hasta, boolean hayCierrePrevio) {
        public BigDecimal esperado() {
            return fondoInicial.add(cobros).add(otros).subtract(salidas);
        }
    }

    /** Lo que muestra la pantalla al cerrar: los importes que vio quien cierra y lo que conto. */
    public record Entrada(String turno, BigDecimal fondoInicial, BigDecimal cobros, BigDecimal otros,
                          BigDecimal salidas, BigDecimal contado, BigDecimal fondoSiguiente, String observacion) { }

    private static final String PERMISO_SERIE = "hotel_cierre_caja";
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    /** Importes del turno que se esta por cerrar, hasta este momento. */
    public Resumen resumen() throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            return calcular(con);
        }
    }

    private Resumen calcular(Connection con) throws SQLException {
        LocalDateTime hasta;
        try (PreparedStatement ps = con.prepareStatement("SELECT NOW()"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            hasta = rs.getTimestamp(1).toLocalDateTime();
        }
        LocalDateTime desde = hasta.toLocalDate().atStartOfDay();
        BigDecimal fondo = BigDecimal.ZERO;
        boolean previo = false;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT fondo_siguiente, COALESCE(fecha_confirmacion, fecha_registro) AS cuando FROM cierre_caja "
                + "WHERE estado = 'CONFIRMADO' ORDER BY cuando DESC, id_cierre DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                fondo = rs.getBigDecimal(1);
                desde = rs.getTimestamp(2).toLocalDateTime();
                previo = true;
            }
        }
        Timestamp d = Timestamp.valueOf(desde);
        Timestamp h = Timestamp.valueOf(hasta);
        BigDecimal cobros = suma(con, "SELECT COALESCE((SELECT SUM(monto) FROM pago WHERE metodo_pago = 'EFECTIVO' "
                + "AND fecha_pago >= ? AND fecha_pago < ?), 0) + COALESCE((SELECT SUM(total) FROM venta_tienda "
                + "WHERE id_habitacion IS NULL AND metodo_pago = 'EFECTIVO' AND estado = 'ACTIVA' "
                + "AND fecha_venta >= ? AND fecha_venta < ?), 0)",
                d, h);
        BigDecimal otros = suma(con, "SELECT COALESCE(SUM(monto), 0) FROM movimiento_caja WHERE tipo = 'INGRESO' "
                + "AND metodo_pago = 'EFECTIVO' AND fecha >= ? AND fecha < ?", d, h);
        BigDecimal salidas = suma(con, "SELECT COALESCE((SELECT SUM(monto) FROM movimiento_caja WHERE tipo = 'EGRESO' "
                + "AND metodo_pago = 'EFECTIVO' AND fecha >= ? AND fecha < ?), 0) + COALESCE((SELECT SUM(monto) "
                + "FROM gasto WHERE activo = 1 AND metodo_pago = 'EFECTIVO' AND fecha_registro >= ? "
                + "AND fecha_registro < ?), 0)", d, h);
        return new Resumen(fondo, cobros, otros, salidas, desde, hasta, previo);
    }

    /** Ejecuta una suma cuya consulta recibe el mismo periodo (desde, hasta) una o dos veces. */
    private BigDecimal suma(Connection con, String sql, Timestamp desde, Timestamp hasta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int parametros = ps.getParameterMetaData().getParameterCount();
            for (int i = 1; i <= parametros; i += 2) {
                ps.setTimestamp(i, desde);
                ps.setTimestamp(i + 1, hasta);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private void validarImporte(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0 || valor.scale() > 2
                || valor.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException(campo + " debe ser un importe no negativo con hasta dos decimales.");
        }
    }

    /**
     * Guarda el cierre del turno como borrador o lo confirma. Al guardar, los importes se vuelven a calcular
     * aqui desde la base; al confirmar, si cambiaron mientras se contaba (alguien cobro o pago algo en ese
     * momento), se rechaza para que se revisen. Un cierre confirmado ya no se puede modificar.
     *
     * @return el numero de cierre
     */
    public int guardar(Entrada e, boolean confirmar) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (e.turno() == null || e.turno().isBlank() || e.turno().length() > 50) {
            throw new IllegalArgumentException("Seleccione el turno que se cierra.");
        }
        validarImporte(e.contado(), "El efectivo contado");
        validarImporte(e.fondoSiguiente(), "La caja chica que queda");
        if (e.fondoSiguiente().compareTo(e.contado()) > 0) {
            throw new IllegalArgumentException("La caja chica que queda no puede superar el efectivo contado.");
        }
        if (e.observacion() != null && e.observacion().length() > 500) {
            throw new IllegalArgumentException("La observación del cierre supera 500 caracteres.");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            boolean turnoTomado = false;
            try {
                // dos personas no pueden cerrar a la vez: el segundo espera y calcula desde el cierre del primero
                turnoTomado = tomarTurno(con);
                Resumen r = calcular(con);
                BigDecimal fondo = r.hayCierrePrevio() ? r.fondoInicial() : e.fondoInicial();
                validarImporte(fondo, "La caja chica inicial");
                if (confirmar && (e.fondoInicial().compareTo(fondo) != 0 || e.cobros().compareTo(r.cobros()) != 0
                        || e.otros().compareTo(r.otros()) != 0 || e.salidas().compareTo(r.salidas()) != 0)) {
                    throw new IllegalStateException("Mientras contaba se registraron cobros o pagos nuevos. "
                            + "Los importes ya se actualizaron: revíselos y confirme de nuevo.");
                }
                BigDecimal esperado = fondo.add(r.cobros()).add(r.otros()).subtract(r.salidas());
                LocalDate fecha = r.hasta().toLocalDate();
                String turno = e.turno().trim();

                Integer id = null;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT id_cierre, estado FROM cierre_caja WHERE fecha = ? AND turno = ? FOR UPDATE")) {
                    ps.setDate(1, java.sql.Date.valueOf(fecha));
                    ps.setString(2, turno);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            if ("CONFIRMADO".equals(rs.getString(2))) {
                                throw new IllegalStateException("El cierre del turno «" + turno + "» de hoy ya está confirmado.");
                            }
                            id = rs.getInt(1);
                        }
                    }
                }
                String sql = id == null
                        ? "INSERT INTO cierre_caja(id_usuario, fecha, turno, fondo_inicial, cobros_efectivo, otros_ingresos, "
                          + "salidas_efectivo, efectivo_esperado, efectivo_contado, diferencia, efectivo_entregado, "
                          + "fondo_siguiente, observacion, estado, fecha_confirmacion) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        : "UPDATE cierre_caja SET id_usuario=?, fecha=?, turno=?, fondo_inicial=?, cobros_efectivo=?, "
                          + "otros_ingresos=?, salidas_efectivo=?, efectivo_esperado=?, efectivo_contado=?, diferencia=?, "
                          + "efectivo_entregado=?, fondo_siguiente=?, observacion=?, estado=?, fecha_confirmacion=? "
                          + "WHERE id_cierre=?";
                try (PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, SesionActual.getUsuario().getIdUsuario());
                    ps.setDate(2, java.sql.Date.valueOf(fecha));
                    ps.setString(3, turno);
                    ps.setBigDecimal(4, fondo);
                    ps.setBigDecimal(5, r.cobros());
                    ps.setBigDecimal(6, r.otros());
                    ps.setBigDecimal(7, r.salidas());
                    ps.setBigDecimal(8, esperado);
                    ps.setBigDecimal(9, e.contado());
                    ps.setBigDecimal(10, e.contado().subtract(esperado));
                    ps.setBigDecimal(11, e.contado().subtract(e.fondoSiguiente()));
                    ps.setBigDecimal(12, e.fondoSiguiente());
                    ps.setString(13, e.observacion());
                    ps.setString(14, confirmar ? "CONFIRMADO" : "BORRADOR");
                    ps.setTimestamp(15, confirmar ? Timestamp.valueOf(r.hasta()) : null);
                    if (id != null) {
                        ps.setInt(16, id);
                    }
                    if (ps.executeUpdate() < 1) {
                        throw new SQLException("No se guardó el cierre.");
                    }
                    if (id == null) {
                        try (ResultSet claves = ps.getGeneratedKeys()) {
                            if (!claves.next()) {
                                throw new SQLException("No se obtuvo el número del cierre.");
                            }
                            id = claves.getInt(1);
                        }
                    }
                }
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        confirmar ? "CAJA_CIERRE" : "CAJA_BORRADOR", "cierre_caja", id,
                        fecha + " / " + turno + " · esperado S/ " + esperado.setScale(2) + " · contado S/ "
                        + e.contado().setScale(2) + " · diferencia S/ " + e.contado().subtract(esperado).setScale(2));
                con.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            } finally {
                if (turnoTomado) {
                    soltarTurno(con);
                }
            }
        }
    }

    /** Hora del ultimo cierre confirmado: lo cobrado antes de esa hora ya se conto en un cierre. Null si no hay. */
    public LocalDateTime ultimaConfirmacion(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT MAX(COALESCE(fecha_confirmacion, fecha_registro)) "
                + "FROM cierre_caja WHERE estado = 'CONFIRMADO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            Timestamp cuando = rs.getTimestamp(1);
            return cuando == null ? null : cuando.toLocalDateTime();
        }
    }

    /**
     * Toma el turno de los cierres: mientras se tiene, nadie confirma un cierre. Lo usa quien anula una venta
     * cobrada, para que el cierre no cuente una venta que se esta anulando. Se suelta con liberarCierres.
     */
    public void bloquearCierres(Connection con) throws SQLException {
        tomarTurno(con);
    }

    public void liberarCierres(Connection con) {
        soltarTurno(con);
    }

    private boolean tomarTurno(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            ps.setString(1, PERMISO_SERIE);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new IllegalStateException("Otra persona está cerrando la caja. Intente de nuevo en unos segundos.");
                }
                return true;
            }
        }
    }

    private void soltarTurno(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, PERMISO_SERIE);
            ps.executeQuery().close();
        } catch (SQLException ignorado) {
            // al cerrarse la conexion se suelta igual
        }
    }
}
