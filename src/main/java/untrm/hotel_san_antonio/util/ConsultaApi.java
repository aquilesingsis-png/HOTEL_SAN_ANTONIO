package untrm.hotel_san_antonio.util;

import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.Node;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.servicio.ReniecService;
import untrm.hotel_san_antonio.servicio.SunatRucService;

/**
 * Consulta de DNI (RENIEC) y RUC (SUNAT) en segundo plano, igual para todas las pantallas:
 * bloquea el boton mientras espera y avisa si hubo datos, si no los hubo o si fallo la consulta.
 * Las tres respuestas se ejecutan en el hilo de la interfaz.
 */
public final class ConsultaApi {

    private ConsultaApi() {
    }

    /**
     * @param boton        boton de busqueda a deshabilitar mientras dura la consulta (puede ser null)
     * @param alEncontrar  recibe los datos de la persona
     * @param alNoEncontrar la API respondio, pero sin datos para ese DNI
     * @param alFallar     no se pudo consultar (sin Internet, token invalido...); recibe el motivo
     */
    public static void dni(String dni, Node boton, Consumer<Huesped> alEncontrar,
                           Runnable alNoEncontrar, Consumer<String> alFallar) {
        ejecutar(ReniecService.consultarDni(dni), boton, alEncontrar, alNoEncontrar, alFallar);
    }

    public static void ruc(String ruc, Node boton, Consumer<Empresa> alEncontrar,
                           Runnable alNoEncontrar, Consumer<String> alFallar) {
        ejecutar(SunatRucService.consultarRuc(ruc), boton, alEncontrar, alNoEncontrar, alFallar);
    }

    private static <T> void ejecutar(Task<T> tarea, Node boton, Consumer<T> alEncontrar,
                                     Runnable alNoEncontrar, Consumer<String> alFallar) {
        if (boton != null) {
            boton.setDisable(true);
        }
        tarea.setOnSucceeded(e -> {
            if (boton != null) {
                boton.setDisable(false);
            }
            T datos = tarea.getValue();
            if (datos == null) {
                alNoEncontrar.run();
            } else {
                alEncontrar.accept(datos);
            }
        });
        tarea.setOnFailed(e -> {
            if (boton != null) {
                boton.setDisable(false);
            }
            alFallar.accept(causa(tarea.getException()));
        });
        iniciar(tarea);
    }

    /** Arranca la tarea en un hilo que no impide cerrar el programa. */
    public static void iniciar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "consulta-api");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Motivo legible de un fallo de conexion. */
    public static String causa(Throwable error) {
        return error == null || error.getMessage() == null ? "sin conexión" : error.getMessage();
    }
}
