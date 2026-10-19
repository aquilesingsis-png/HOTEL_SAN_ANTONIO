package untrm.hotel_san_antonio.servicio;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Cambios de estado de una habitacion hechos desde recepcion: dejarla disponible cuando ya se limpio o
 * termino el mantenimiento, ponerla en mantenimiento y corregir una habitacion que figura ocupada sin
 * estadia. Cada cambio queda en la auditoria.
 */
public class HabitacionService {

    /** Cambios permitidos: estado actual -> estados a los que puede pasar. */
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "DISPONIBLE", Set.of("MANTENIMIENTO"),
            "LIMPIEZA", Set.of("DISPONIBLE"),
            "MANTENIMIENTO", Set.of("DISPONIBLE"),
            "OCUPADA", Set.of("LIMPIEZA"));

    private final HabitacionDAO habitaciones = new HabitacionDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    /**
     * Cambia el estado solo si la habitacion sigue en el estado esperado (evita pisar un cambio hecho por
     * otra persona mientras la pantalla estaba abierta) y si no tiene una estadia en curso.
     *
     * @param motivo obligatorio al pasar a MANTENIMIENTO; en los demas cambios se ignora
     * @return false si el estado ya era otro
     */
    public boolean cambiarEstado(int idHabitacion, String esperado, String nuevo, String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (esperado == null || nuevo == null || !TRANSICIONES.getOrDefault(esperado, Set.of()).contains(nuevo)) {
            throw new IllegalArgumentException("Ese cambio de estado no está permitido.");
        }
        String motivoLimpio = motivo == null ? null : motivo.trim();
        if ("MANTENIMIENTO".equals(nuevo)
                && (motivoLimpio == null || motivoLimpio.isEmpty() || motivoLimpio.length() > 150)) {
            throw new IllegalArgumentException("Indique el motivo del mantenimiento (máximo 150 caracteres).");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                String actual = habitaciones.bloquearYObtenerEstado(con, idHabitacion);
                if (actual == null) {
                    throw new IllegalArgumentException("La habitación no existe.");
                }
                if (!actual.equals(esperado)) {
                    con.rollback();
                    return false;
                }
                if (habitaciones.tieneCheckinActivo(con, idHabitacion)) {
                    throw new IllegalStateException("Hay una estadía activa en esta habitación.");
                }
                habitaciones.cambiarEstado(con, idHabitacion, nuevo,
                        "MANTENIMIENTO".equals(nuevo) ? motivoLimpio : null);
                Habitacion h = habitaciones.buscarPorId(con, idHabitacion);
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(), "HABITACION_ESTADO",
                        "habitacion", idHabitacion, "hab. " + (h == null ? idHabitacion : h.getNumero()) + ": "
                        + esperado + " → " + nuevo
                        + ("MANTENIMIENTO".equals(nuevo) ? " · " + motivoLimpio : ""));
                con.commit();
                return true;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }
}
