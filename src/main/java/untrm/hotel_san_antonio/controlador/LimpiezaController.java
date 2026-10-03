package untrm.hotel_san_antonio.controlador;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.servicio.LimpiezaService;

public class LimpiezaController {
    @FXML private TableView<Habitacion> tabla;
    @FXML private TableColumn<Habitacion, String> colNumero, colTipo, colPiso, colEstado;
    @FXML private Button btnPendiente, btnLista;
    @FXML private Label lblMensaje;
    private final LimpiezaService servicio = new LimpiezaService();

    @FXML private void initialize() {
        colNumero.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colTipo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTipo().getNombre()));
        colPiso.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getPiso())));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEstado()));
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            btnPendiente.setDisable(ahora == null || !"DISPONIBLE".equals(ahora.getEstado()));
            btnLista.setDisable(ahora == null || !"LIMPIEZA".equals(ahora.getEstado()));
        });
        actualizar();
    }

    @FXML private void actualizar() {
        Task<java.util.List<Habitacion>> tarea = new Task<>() {
            @Override protected java.util.List<Habitacion> call() throws Exception { return servicio.listar(); }
        };
        tarea.setOnSucceeded(e -> {
            tabla.getItems().setAll(tarea.getValue());
            lblMensaje.setText("Lista actualizada.");
        });
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudieron cargar las habitaciones."));
        ejecutar(tarea);
    }

    @FXML private void marcarEnLimpieza() { cambiar("LIMPIEZA"); }
    @FXML private void marcarDisponible() { cambiar("DISPONIBLE"); }

    private void cambiar(String estado) {
        Habitacion seleccionada = tabla.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            lblMensaje.setText("Seleccione una habitación antes de cambiar el estado.");
            return;
        }
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                servicio.cambiarEstado(seleccionada.getIdHabitacion(), estado);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> actualizar());
        tarea.setOnFailed(e -> lblMensaje.setText(tarea.getException() instanceof IllegalStateException
                ? tarea.getException().getMessage() : "No se pudo cambiar el estado."));
        ejecutar(tarea);
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "limpieza-db");
        hilo.setDaemon(true);
        hilo.start();
    }
}
