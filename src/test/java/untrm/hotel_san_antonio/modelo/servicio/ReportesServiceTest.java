package untrm.hotel_san_antonio.modelo.servicio;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

/** Reglas de los reportes: solo el administrador y fechas válidas (se rechaza antes de consultar la base). */
class ReportesServiceTest {

    private final ReportesService reportes = new ReportesService();
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void entrarComoAdministrador() {
        SesionActual.iniciar(new Usuario(1, "Admin", "General", "admin", "ADMINISTRADOR", true));
    }

    @AfterEach
    void cerrarSesion() {
        SesionActual.cerrar();
    }

    @Test
    void recepcionNoPuedeVerReportes() {
        SesionActual.iniciar(new Usuario(2, "Recep", "Hotel", "recepcion", "RECEPCIONISTA", true));
        assertThrows(SecurityException.class, () -> reportes.ingresos(hoy.minusDays(7), hoy, null, null, "Día"));
        assertThrows(SecurityException.class, () -> reportes.ventas(hoy.minusDays(7), hoy, null, null, "Día"));
        assertThrows(SecurityException.class, () -> reportes.ocupacion(hoy.minusDays(7), hoy, null, "Día"));
    }

    @Test
    void lasFechasSonObligatorias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> reportes.ingresos(null, hoy, null, null, "Día"));
        assertEquals("Indique las fechas desde y hasta.", error.getMessage());
    }

    @Test
    void laFechaFinalNoPuedeSerAnteriorALaInicial() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> reportes.ventas(hoy, hoy.minusDays(2), null, null, "Día"));
        assertEquals("La fecha final no puede ser anterior a la inicial.", error.getMessage());
    }

    @Test
    void elPeriodoNoPuedeSuperar3660Dias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> reportes.ocupacion(hoy.minusDays(5000), hoy, null, "Mes"));
        assertEquals("El período no puede superar 3660 días.", error.getMessage());
    }
}
