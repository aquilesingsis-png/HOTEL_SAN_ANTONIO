package untrm.hotel_san_antonio.servicio;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/** Limpieza ve solo las habitaciones en limpieza y las marca disponibles cuando ya están limpias. */
public class LimpiezaService {
    private final HabitacionDAO habitaciones = new HabitacionDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    /** Habitaciones que dejó el check-out y todavía no se limpian. */
    public List<Habitacion> listarEnLimpieza() throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "LIMPIEZA");
        return habitaciones.listar().stream().filter(h -> "LIMPIEZA".equals(h.getEstado())).toList();
    }

    public void marcarDisponible(int idHabitacion) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "LIMPIEZA");
        String nuevoEstado = "DISPONIBLE";
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                String actual = habitaciones.bloquearYObtenerEstado(con, idHabitacion);
                if (actual == null) throw new IllegalArgumentException("La habitación no existe.");
                if (!"LIMPIEZA".equals(actual)) {
                    throw new IllegalStateException("La habitación ya no está en limpieza.");
                }
                if (actual.equals(nuevoEstado)) return;
                if (habitaciones.tieneCheckinActivo(con, idHabitacion))
                    throw new IllegalStateException("Hay una estadía activa en esta habitación.");
                habitaciones.cambiarEstado(con, idHabitacion, nuevoEstado);
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(), "LIMPIEZA_ESTADO",
                        "habitacion", idHabitacion, actual + " → " + nuevoEstado);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }
}
