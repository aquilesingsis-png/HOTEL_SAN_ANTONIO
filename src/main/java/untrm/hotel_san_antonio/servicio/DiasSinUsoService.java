package untrm.hotel_san_antonio.servicio;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.DiasSinUsoDAO;
import untrm.hotel_san_antonio.dao.DiasSinUsoDAO.Corte;
import untrm.hotel_san_antonio.dao.DiasSinUsoDAO.ResumenDia;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Días sin uso del sistema. El sistema anota cada día que estuvo en uso (al iniciar sesión y cada 30 minutos).
 * Si al volver faltan días, se abre un corte con 24 horas de plazo para registrar lo que pasó en esos días
 * (ventas del carrito y estadías); el administrador puede reabrir el plazo hasta 2 veces, con motivo.
 */
public class DiasSinUsoService {

    /** Veces que el administrador puede ampliar el plazo de un mismo corte. */
    public static final int MAX_REAPERTURAS = 2;

    /**
     * Situación actual: los días que faltan por registrar (del corte más reciente que aún tiene pendientes)
     * y cuánto queda de plazo.
     */
    public record Estado(int idCorte, List<LocalDate> pendientes, LocalDateTime habilitaHasta,
                         boolean vigente, long minutosRestantes, int reaperturas) {

        public boolean hayPendientes() {
            return !pendientes.isEmpty();
        }

        /** Se puede registrar ahora: hay días pendientes y el plazo no venció. */
        public boolean abierto() {
            return hayPendientes() && vigente;
        }

        /** El plazo venció, quedan días pendientes y el administrador todavía puede ampliarlo. */
        public boolean puedeReabrir() {
            return hayPendientes() && !vigente && reaperturas < MAX_REAPERTURAS;
        }
    }

    private final DiasSinUsoDAO dao = new DiasSinUsoDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    /**
     * Anota que el sistema está en uso hoy y, si faltan días desde la última vez, abre el corte.
     * Se llama al iniciar sesión y cada 30 minutos mientras el programa está abierto.
     */
    public Estado registrarActividad() throws SQLException {
        int usuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                LocalDate hoy = dao.hoy(con);
                LocalDate ultima = dao.ultimaActividad(con);
                if (ultima != null && ultima.isBefore(hoy.minusDays(1))) {
                    LocalDate desde = ultima.plusDays(1);
                    LocalDate hasta = hoy.minusDays(1);
                    int id = dao.crearCorte(con, desde, hasta);
                    if (id > 0) {
                        auditoria.registrar(con, usuario, "CORTE_DETECTADO", "sistema", id,
                                "Días sin uso: " + desde + " a " + hasta);
                    }
                }
                dao.marcarActividad(con, usuario);
                Estado estado = construirEstado(con);
                con.commit();
                return estado;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    public Estado estado() throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            return construirEstado(con);
        }
    }

    /** Lo ya registrado para un día pasado, para mostrarlo en pantalla. */
    public ResumenDia resumenDia(LocalDate dia) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            return dao.resumenDia(con, dia);
        }
    }

    /** Da por terminado el registro de un día: ya no se podrá agregar nada a esa fecha. */
    public void completarDia(LocalDate dia) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        int usuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Integer corte = dao.corteHabilitado(con, dia);
                if (corte == null) throw new IllegalStateException(mensajeNoHabilitado(dia));
                dao.completarDia(con, dia, corte, usuario);
                auditoria.registrar(con, usuario, "DIA_REGULARIZADO", "sistema", corte, "Día completado: " + dia);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    /** El administrador amplía 24 horas el plazo vencido (hasta {@link #MAX_REAPERTURAS} veces). */
    public void reabrir(String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        if (motivo == null || motivo.trim().length() < 10 || motivo.trim().length() > 200)
            throw new IllegalArgumentException("Escriba el motivo de la reapertura (de 10 a 200 caracteres).");
        int usuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Estado estado = construirEstado(con);
                if (!estado.hayPendientes()) throw new IllegalStateException("No hay días pendientes de registrar.");
                if (estado.vigente()) throw new IllegalStateException("El plazo todavía está abierto.");
                if (estado.reaperturas() >= MAX_REAPERTURAS)
                    throw new IllegalStateException("El plazo ya se reabrió " + MAX_REAPERTURAS
                            + " veces: no se puede ampliar más desde el sistema.");
                dao.reabrir(con, estado.idCorte());
                auditoria.registrar(con, usuario, "CORTE_REABRIR", "sistema", estado.idCorte(),
                        "Reapertura " + (estado.reaperturas() + 1) + " de " + MAX_REAPERTURAS + ": " + motivo.trim());
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    /**
     * Para los demás servicios: el día debe estar pendiente en un corte con plazo vigente.
     * Se llama dentro de la transacción que guarda el registro tardío.
     */
    public void requerirDiaPendiente(Connection con, LocalDate dia) throws SQLException {
        if (dia == null || dao.corteHabilitado(con, dia) == null) {
            throw new IllegalStateException(mensajeNoHabilitado(dia));
        }
    }

    private String mensajeNoHabilitado(LocalDate dia) {
        return "El día " + dia + " no está habilitado para registro tardío: solo se pueden registrar los días "
                + "en que el sistema no se usó y mientras dure el plazo.";
    }

    private Estado construirEstado(Connection con) throws SQLException {
        Set<LocalDate> completados = dao.diasCompletados(con);
        for (Corte corte : dao.cortes(con)) {
            List<LocalDate> pendientes = new ArrayList<>();
            for (LocalDate dia = corte.desde(); !dia.isAfter(corte.hasta()); dia = dia.plusDays(1)) {
                if (!completados.contains(dia)) pendientes.add(dia);
            }
            if (!pendientes.isEmpty()) {
                return new Estado(corte.id(), pendientes, corte.habilitaHasta(), corte.vigente(),
                        corte.minutosRestantes(), corte.reaperturas());
            }
        }
        return new Estado(0, List.of(), null, false, 0, 0);
    }
}
