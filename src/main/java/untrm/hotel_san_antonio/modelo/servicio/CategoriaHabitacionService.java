package untrm.hotel_san_antonio.modelo.servicio;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import untrm.hotel_san_antonio.modelo.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.modelo.dao.HabitacionDAO;
import untrm.hotel_san_antonio.modelo.TipoHabitacion;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;
import untrm.hotel_san_antonio.modelo.seguridad.Permisos;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

public class CategoriaHabitacionService {
    private final HabitacionDAO habitaciones = new HabitacionDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    public List<TipoHabitacion> listar() throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        try (Connection con = ConexionBD.conectar()) { return habitaciones.listarTipos(con); }
    }

    public void guardarNivel(int idTipo, int nivel) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        if (nivel < 1 || nivel > 100) throw new IllegalArgumentException("El nivel debe estar entre 1 y 100.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Integer anterior = habitaciones.nivelCategoria(con, idTipo);
                if (anterior != null && anterior == nivel) return;
                habitaciones.actualizarNivelCategoria(con, idTipo, nivel);
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        "TIPO_NIVEL", "tipo_habitacion", idTipo,
                        String.valueOf(anterior) + " → " + nivel);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback(); throw error;
            }
        }
    }
}
