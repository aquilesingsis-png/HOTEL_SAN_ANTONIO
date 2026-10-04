package untrm.hotel_san_antonio.controlador;

import java.util.HashMap;
import java.util.Map;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;

public class CambiarHabitacionController {
    @FXML private Label lblActual, lblMensaje;
    @FXML private ComboBox<String> cmbNueva;
    @FXML private TextArea txtMotivo;
    @FXML private Button btnGuardar;
    private final ReservaService servicio = new ReservaService();
    private final Map<String, Habitacion> opciones = new HashMap<>();
    private Reserva reserva;
    private Runnable alCambiar;

    public void iniciar(Reserva reserva, Runnable alCambiar) {
        this.reserva = reserva;
        this.alCambiar = alCambiar;
        lblActual.setText(reserva.getCodigo() + " · habitación actual: " + reserva.getNumeroHabitacion()
                + " · total actual S/ " + reserva.getMontoTotal());
        Task<java.util.List<Habitacion>> tarea = new Task<>() {
            @Override protected java.util.List<Habitacion> call() throws Exception {
                return servicio.listarHabitacionesParaCambio(reserva.getIdReserva());
            }
        };
        tarea.setOnSucceeded(e -> {
            opciones.clear();
            cmbNueva.getItems().clear();
            for (Habitacion h : tarea.getValue()) {
                String etiqueta = h.getNumero() + " · " + h.getTipo().getNombre()
                        + " · S/ " + h.getTipo().getPrecioBase() + " por noche";
                opciones.put(etiqueta, h);
                cmbNueva.getItems().add(etiqueta);
            }
            lblMensaje.setText(opciones.isEmpty() ? "No hay habitaciones aptas para estas fechas."
                    : "Seleccione la habitación destino.");
        });
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudieron consultar las habitaciones."));
        ejecutar(tarea);
    }

    @FXML private void guardar() {
        Habitacion nueva = opciones.get(cmbNueva.getValue());
        if (nueva == null) {
            lblMensaje.setText("Seleccione una habitación.");
            return;
        }
        btnGuardar.setDisable(true);
        Task<ReservaService.ResultadoCambio> tarea = new Task<>() {
            @Override protected ReservaService.ResultadoCambio call() throws Exception {
                return servicio.cambiarHabitacion(reserva.getIdReserva(), nueva.getIdHabitacion(), txtMotivo.getText());
            }
        };
        tarea.setOnSucceeded(e -> {
            var resultado = tarea.getValue();
            Alertas.mostrarInfo("Cambio registrado", "Total: S/ " + resultado.total()
                    + "\nPagado: S/ " + resultado.pagado() + "\nSaldo: S/ " + resultado.saldo());
            if (alCambiar != null) alCambiar.run();
            cancelar();
        });
        tarea.setOnFailed(e -> {
            btnGuardar.setDisable(false);
            Throwable error = tarea.getException();
            lblMensaje.setText(error instanceof IllegalArgumentException || error instanceof IllegalStateException
                    ? error.getMessage() : "No se pudo realizar el cambio. Actualice la lista.");
        });
        ejecutar(tarea);
    }

    @FXML private void cancelar() {
        ((Stage) lblActual.getScene().getWindow()).close();
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "cambio-habitacion");
        hilo.setDaemon(true);
        hilo.start();
    }
}
