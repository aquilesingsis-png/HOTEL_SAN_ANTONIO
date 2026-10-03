package untrm.hotel_san_antonio.util;

import java.io.Console;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.UsuarioDAO;
import untrm.hotel_san_antonio.modelo.Usuario;

/** Inicialización interactiva de una base nueva, sin credenciales de ejemplo. */
public final class BootstrapAdmin {
    private BootstrapAdmin() {}

    public static void main(String[] args) throws Exception {
        Console consola = System.console();
        if (consola == null) throw new IllegalStateException("Ejecute BootstrapAdmin desde una terminal interactiva.");
        String nombre = consola.readLine("Nombres del administrador: ");
        String apellido = consola.readLine("Apellidos: ");
        String login = consola.readLine("Usuario (3-30 letras/números/._-): ");
        char[] clave = consola.readPassword("Contraseña (12-128 caracteres, letras y números): ");
        char[] confirmacion = consola.readPassword("Repita la contraseña: ");
        try {
            if (!Arrays.equals(clave, confirmacion))
                throw new IllegalArgumentException("Las contraseñas no coinciden.");
            if (!Validador.esNombreValido(nombre) || !Validador.esNombreValido(apellido)
                    || login == null || !login.matches("[A-Za-z0-9._-]{3,30}"))
                throw new IllegalArgumentException("Los datos del administrador no son válidos.");
            String textoClave = new String(clave);
            PasswordUtil.validarNueva(textoClave);
            try (Connection con = ConexionBD.conectar()) {
                con.setAutoCommit(false);
                try {
                    UsuarioDAO usuarios = new UsuarioDAO();
                    if (usuarios.hayUsuarios(con))
                        throw new IllegalStateException("Ya existen usuarios; la inicialización está deshabilitada.");
                    Usuario admin = new Usuario();
                    admin.setNombre(nombre.trim()); admin.setApellido(apellido.trim());
                    admin.setUsuario(login.trim()); admin.setContrasenaHash(PasswordUtil.hash(textoClave));
                    admin.setRol("ADMINISTRADOR"); admin.setActivo(true);
                    int id = usuarios.insertar(con, admin);
                    new AuditoriaDAO().registrar(con, id, "BOOTSTRAP_ADMIN", "usuario", id, null);
                    con.commit();
                    consola.printf("Administrador creado con ID %d.%n", id);
                } catch (SQLException | RuntimeException error) {
                    con.rollback(); throw error;
                }
            }
        } finally {
            Arrays.fill(clave, '\0');
            Arrays.fill(confirmacion, '\0');
        }
    }
}
