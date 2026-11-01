package untrm.hotel_san_antonio.modelo.servicio;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import untrm.hotel_san_antonio.modelo.CierreCaja;
import untrm.hotel_san_antonio.modelo.EntradaCierre;
import untrm.hotel_san_antonio.modelo.MovimientoLibro;
import untrm.hotel_san_antonio.modelo.ResumenCierre;
import untrm.hotel_san_antonio.modelo.TotalesCaja;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;
import untrm.hotel_san_antonio.modelo.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.modelo.dao.CajaDAO;
import untrm.hotel_san_antonio.modelo.dao.CierreCajaDAO;
import untrm.hotel_san_antonio.modelo.seguridad.Permisos;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

/**
 * Caja: el libro con todo el dinero que entra y sale, los movimientos manuales y el cierre por turno.
 * Cada cierre cuenta el efectivo que entró y salió desde el cierre confirmado anterior (sea de la mañana, tarde
 * o noche), así un turno que cruza la medianoche cuenta completo y ningún cobro se cuenta dos veces. El sistema
 * calcula los importes: quien cierra solo cuenta el efectivo. La caja chica es el efectivo fijo con el que
 * empieza cada turno: lo que queda en la caja al cerrar es la caja chica del turno siguiente.
 */
public class CajaService {

    /** Cuántos renglones trae como máximo la lista; si hay más, la pantalla avisa y los totales siguen completos. */
    public static final int LIMITE = CajaDAO.LIMITE;

    private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("99999999.99");

    private final CajaDAO caja = new CajaDAO();
    private final CierreCajaDAO cierres = new CierreCajaDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    // ---------------------------------------------------------------- libro de caja

    /** Los últimos cierres: el Administrador ve todos; recepción, solo los suyos. */
    public List<CierreCaja> listarCierres() throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        return caja.listarCierres(SesionActual.esAdministrador() ? null : SesionActual.getUsuario().getIdUsuario());
    }

    public List<MovimientoLibro> listar(LocalDate desde, LocalDate hasta, String tipo, String metodo,
                                        String origen, String texto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        return caja.listarMovimientos(desde, hasta, tipo, metodo, origen, texto);
    }

    public TotalesCaja totales(LocalDate desde, LocalDate hasta, String tipo, String metodo,
                               String origen, String texto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        return caja.totales(desde, hasta, tipo, metodo, origen, texto);
    }

    /** Un ingreso o egreso de caja escrito a mano (solo el Administrador). */
    public void guardarMovimientoCaja(String tipo, String concepto, String metodo,
                                      BigDecimal monto, String referencia, String observacion,
                                      String turno) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        if (!List.of("INGRESO", "EGRESO").contains(tipo))
            throw new IllegalArgumentException("Seleccione ingreso o egreso.");
        if (concepto == null || concepto.isBlank() || concepto.length() > 150)
            throw new IllegalArgumentException("Indique un concepto de hasta 150 caracteres.");
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE").contains(metodo))
            throw new IllegalArgumentException("Seleccione un método de pago válido.");
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2 || monto.compareTo(IMPORTE_MAXIMO) > 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero, con hasta dos decimales.");
        }
        if (turno == null || turno.isBlank() || turno.length() > 50)
            throw new IllegalArgumentException("Indique el turno o caja (hasta 50 caracteres).");
        String nota = "Turno/caja: " + turno.trim()
                + (observacion == null || observacion.isBlank() ? "" : "\n" + observacion.trim());
        if (nota.length() > 500) throw new IllegalArgumentException("La observación supera 500 caracteres.");
        int usuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                int id = caja.insertarMovimiento(con, usuario, tipo, concepto.trim(), metodo, monto, referencia, nota);
                auditoria.registrar(con, usuario, "CAJA_MOVIMIENTO", "movimiento_caja", id,
                        tipo + " " + monto + " " + concepto.trim());
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    // ---------------------------------------------------------------- cierre de caja

    /** Importes del turno que se está por cerrar, hasta este momento. */
    public ResumenCierre resumenCierre() throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            return calcular(con);
        }
    }

    private ResumenCierre calcular(Connection con) throws SQLException {
        LocalDateTime hasta = cierres.ahora(con);
        LocalDateTime desde = hasta.toLocalDate().atStartOfDay();
        BigDecimal fondo = BigDecimal.ZERO;
        boolean previo = false;
        CierreCajaDAO.CierrePrevio ultimo = cierres.ultimoConfirmado(con);
        if (ultimo != null) {
            fondo = ultimo.fondoSiguiente();
            desde = ultimo.cuando();
            previo = true;
        }
        return new ResumenCierre(fondo, cierres.cobrosEfectivo(con, desde, hasta),
                cierres.otrosIngresosEfectivo(con, desde, hasta), cierres.salidasEfectivo(con, desde, hasta),
                desde, hasta, previo);
    }

    private void validarImporte(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0 || valor.scale() > 2 || valor.compareTo(IMPORTE_MAXIMO) > 0) {
            throw new IllegalArgumentException(campo + " debe ser un importe no negativo con hasta dos decimales.");
        }
    }

    /**
     * Guarda el cierre del turno como borrador o lo confirma. Al guardar, los importes se vuelven a calcular
     * aquí desde la base; al confirmar, si cambiaron mientras se contaba (alguien cobró o pagó algo en ese
     * momento), se rechaza para que se revisen. Un cierre confirmado ya no se puede modificar.
     *
     * @return el número de cierre
     */
    public int guardarCierre(EntradaCierre e, boolean confirmar) throws SQLException {
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
        int usuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            boolean turnoTomado = false;
            try {
                // dos personas no pueden cerrar a la vez: el segundo espera y calcula desde el cierre del primero
                bloquearCierres(con);
                turnoTomado = true;
                ResumenCierre r = calcular(con);
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

                CierreCajaDAO.CierreExistente existente = cierres.buscarBloqueando(con, fecha, turno);
                if (existente != null && "CONFIRMADO".equals(existente.estado())) {
                    throw new IllegalStateException("El cierre del turno «" + turno + "» de hoy ya está confirmado.");
                }
                CierreCajaDAO.Registro registro = new CierreCajaDAO.Registro(usuario, fecha, turno, fondo, r.cobros(),
                        r.otros(), r.salidas(), esperado, e.contado(), e.contado().subtract(esperado),
                        e.contado().subtract(e.fondoSiguiente()), e.fondoSiguiente(), e.observacion(),
                        confirmar ? "CONFIRMADO" : "BORRADOR", confirmar ? r.hasta() : null);
                int id;
                if (existente == null) {
                    id = cierres.insertar(con, registro);
                } else {
                    id = existente.id();
                    cierres.actualizar(con, id, registro);
                }
                auditoria.registrar(con, usuario, confirmar ? "CAJA_CIERRE" : "CAJA_BORRADOR", "cierre_caja", id,
                        fecha + " / " + turno + " · esperado S/ " + esperado.setScale(2) + " · contado S/ "
                        + e.contado().setScale(2) + " · diferencia S/ " + e.contado().subtract(esperado).setScale(2));
                con.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            } finally {
                if (turnoTomado) {
                    liberarCierres(con);
                }
            }
        }
    }

    /**
     * Toma el turno de los cierres: mientras se tiene, nadie confirma un cierre. Lo usa quien anula una venta
     * cobrada, para que el cierre no cuente una venta que se está anulando. Se suelta con liberarCierres.
     */
    public void bloquearCierres(Connection con) throws SQLException {
        if (!cierres.tomarTurno(con)) {
            throw new IllegalStateException("Otra persona está cerrando la caja. Intente de nuevo en unos segundos.");
        }
    }

    public void liberarCierres(Connection con) {
        cierres.soltarTurno(con);
    }

    /** Hora del último cierre confirmado: lo cobrado antes de esa hora ya se contó en un cierre. Null si no hay. */
    public LocalDateTime ultimaConfirmacion(Connection con) throws SQLException {
        return cierres.ultimaConfirmacion(con);
    }
}
