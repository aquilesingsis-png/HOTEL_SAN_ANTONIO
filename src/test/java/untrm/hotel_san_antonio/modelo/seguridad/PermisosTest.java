package untrm.hotel_san_antonio.modelo.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import untrm.hotel_san_antonio.modelo.Usuario;

/** El control de acceso por rol: cada operación vuelve a comprobar quién la pide. */
class PermisosTest {

    private void entrarComo(String rol, boolean activo) {
        SesionActual.iniciar(new Usuario(1, "Prueba", "Rol", "usuario", rol, activo));
    }

    @AfterEach
    void cerrarSesion() {
        SesionActual.cerrar();
    }

    @Test
    void sinSesionSeRechaza() {
        SesionActual.cerrar();
        SecurityException error = assertThrows(SecurityException.class, () -> Permisos.requerir("ADMINISTRADOR"));
        assertEquals("Inicie sesión nuevamente.", error.getMessage());
    }

    @Test
    void unUsuarioInactivoSeRechazaAunqueTengaElRol() {
        entrarComo("ADMINISTRADOR", false);
        assertThrows(SecurityException.class, () -> Permisos.requerir("ADMINISTRADOR"));
    }

    @Test
    void elAdministradorPasaDondeSeExigeAdministrador() {
        entrarComo("ADMINISTRADOR", true);
        assertDoesNotThrow(() -> Permisos.requerir("ADMINISTRADOR"));
        assertTrue(SesionActual.esAdministrador());
    }

    @Test
    void recepcionNoPuedeHacerLoQueEsSoloDelAdministrador() {
        entrarComo("RECEPCIONISTA", true);
        assertThrows(SecurityException.class, () -> Permisos.requerir("ADMINISTRADOR"));
        assertFalse(SesionActual.esAdministrador());
    }

    @Test
    void recepcionPasaDondeSeLePermite() {
        entrarComo("RECEPCIONISTA", true);
        assertDoesNotThrow(() -> Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA"));
    }

    @Test
    void limpiezaNoEntraAZonasDeCaja() {
        entrarComo("LIMPIEZA", true);
        assertThrows(SecurityException.class, () -> Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA"));
    }
}
