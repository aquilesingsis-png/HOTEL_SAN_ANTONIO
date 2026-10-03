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

/** Limpieza solo ve habitaciones y cambia entre pendiente y disponible. */
public class LimpiezaService {
    private final HabitacionDAO habitaciones = new HabitacionDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    public List<Habitacion> listar() throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "LIMPIEZA");
        return habitaciones.listar();
    }

    public void cambiarEstado(int idHabitacion, String nuevoEstado) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "LIMPIEZA");
        if (!"LIMPIEZA".equals(nuevoEstado) && !"DISPONIBLE".equals(nuevoEstado)) {
            throw new IllegalArgumentException("Estado de limpieza no permitido.");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                String actual = habitaciones.bloquearYObtenerEstado(con, idHabitacion);
                if (actual == null) throw new IllegalArgumentException("La habitación no existe.");
                if (!("DISPONIBLE".equals(actual) || "LIMPIEZA".equals(actual))) {
                    throw new IllegalStateException("La habitación está ocupada o en mantenimiento.");
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
