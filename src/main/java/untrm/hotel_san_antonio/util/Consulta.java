package untrm.hotel_san_antonio.util;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;

/**
 * Una consulta a la base que se hace en segundo plano, para que la ventana no se congele mientras MySQL responde.
 * Cada pantalla guarda una Consulta por cada lista que carga: si se lanza otra antes de que termine la anterior
 * (por ejemplo al escribir en un buscador), solo cuenta el resultado de la última.
 *
 * @param <T> lo que devuelve la consulta
 */
public final class Consulta<T> {

    private int ultima;

    /**
     * @param ocupado    nodo de la pantalla que muestra el cursor de espera mientras se consulta (puede ser null)
     * @param trabajo    la consulta; corre fuera del hilo de la interfaz, así que no debe tocar controles
     * @param alTerminar recibe el resultado, ya en el hilo de la interfaz
     * @param alFallar   recibe el error, ya en el hilo de la interfaz
     */
    public void lanzar(Node ocupado, Callable<T> trabajo, Consumer<T> alTerminar, Consumer<Throwable> alFallar) {
        final int numero = ++ultima;
        if (ocupado != null) {
            ocupado.setCursor(Cursor.WAIT);
        }
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };
        tarea.setOnSucceeded(e -> {
            if (numero != ultima) {
                return; // ya se pidio otra consulta mas reciente
            }
            if (ocupado != null) {
                ocupado.setCursor(Cursor.DEFAULT);
            }
            alTerminar.accept(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            if (numero != ultima) {
                return;
            }
            if (ocupado != null) {
                ocupado.setCursor(Cursor.DEFAULT);
            }
            Registro.fallo("Consulta en segundo plano", tarea.getException());
            alFallar.accept(tarea.getException());
        });
        Thread hilo = new Thread(tarea, "consulta-bd");
        hilo.setDaemon(true);
        hilo.start();
    }
}
