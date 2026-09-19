package untrm.hotel_san_antonio.util;

import untrm.hotel_san_antonio.modelo.Usuario;

/**
 * Guarda quien inicio sesion, para que el Dashboard y los demas
 * controllers sepan que puede ver/hacer segun su rol.
 */
public class SesionActual {

    private static Usuario usuario;

    public static void iniciar(Usuario usuarioLogueado) {
        usuario = usuarioLogueado;
    }

    public static Usuario getUsuario() {
        return usuario;
    }

    public static boolean esAdministrador() {
        return usuario != null && "ADMINISTRADOR".equals(usuario.getRol());
    }

    public static void cerrar() {
        usuario = null;
    }
}
