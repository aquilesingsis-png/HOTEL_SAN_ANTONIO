package untrm.hotel_san_antonio.servicio;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.UsuarioDAO;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.PasswordUtil;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.Validador;

public class UsuarioService {
    private final UsuarioDAO usuarios = new UsuarioDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    public List<String> listarRoles() throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        try (Connection con = ConexionBD.conectar()) { return usuarios.listarRoles(con); }
    }

    public List<Usuario> listarUsuarios() throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        try (Connection con = ConexionBD.conectar()) { return usuarios.listar(con); }
    }

    public void asignarRol(int idUsuario, String nuevoRol) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        if (idUsuario == SesionActual.getUsuario().getIdUsuario())
            throw new IllegalArgumentException("No cambie su propio rol durante una sesión activa.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Usuario afectado = usuarios.bloquearPorId(con, idUsuario);
                if (afectado == null) throw new IllegalArgumentException("El usuario no existe.");
                if (!usuarios.listarRoles(con).contains(nuevoRol))
                    throw new IllegalArgumentException("El rol no existe en la base de datos.");
                if (nuevoRol.equals(afectado.getRol())) return;
                if ("ADMINISTRADOR".equals(afectado.getRol())
                        && usuarios.contarAdministradoresActivos(con) <= 1)
                    throw new IllegalStateException("Debe permanecer al menos un administrador activo.");
                usuarios.cambiarRol(con, idUsuario, nuevoRol);
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        "USUARIO_ROL", "usuario", idUsuario, afectado.getRol() + " → " + nuevoRol);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback(); throw error;
            }
        }
    }

    public int crear(String nombre, String apellido, String login, String contrasena,
                     String rol, boolean activo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        if (!Validador.esNombreValido(nombre) || !Validador.esNombreValido(apellido))
            throw new IllegalArgumentException("Nombres y apellidos no son válidos.");
        if (login == null || !login.matches("[A-Za-z0-9._-]{3,30}"))
            throw new IllegalArgumentException("El usuario debe tener 3 a 30 caracteres alfanuméricos.");
        PasswordUtil.validarNueva(contrasena);
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (!usuarios.listarRoles(con).contains(rol))
                    throw new IllegalArgumentException("El rol no existe en la base de datos.");
                Usuario nuevo = new Usuario();
                nuevo.setNombre(nombre.trim()); nuevo.setApellido(apellido.trim());
                nuevo.setUsuario(login.trim()); nuevo.setContrasenaHash(PasswordUtil.hash(contrasena));
                nuevo.setRol(rol); nuevo.setActivo(activo);
                int id = usuarios.insertar(con, nuevo);
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        "USUARIO_CREAR", "usuario", id, "Rol: " + rol);
                con.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }
}
