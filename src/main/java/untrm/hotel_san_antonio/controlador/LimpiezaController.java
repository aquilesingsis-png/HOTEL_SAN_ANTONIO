package untrm.hotel_san_antonio.controlador;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.servicio.LimpiezaService;
import untrm.hotel_san_antonio.util.EstiloGlobal;

/**
 * Limpieza: muestra solo las habitaciones que dejó el check-out (estado LIMPIEZA) y se actualiza sola.
 * Lo único que se hace aquí es marcar disponible la habitación que ya se limpió.
 */
public class LimpiezaController {
    private static final int SEGUNDOS_ENTRE_ACTUALIZACIONES = 10;
    private static final String TODOS_LOS_PISOS = "Todos los pisos";

    @FXML private TableView<Habitacion> tabla;
    @FXML private TableColumn<Habitacion, String> colNumero, colTipo, colPiso;
    @FXML private Button btnLista;
    @FXML private Label lblMensaje;
    @FXML private Label lblPendientes;
    @FXML private ChoiceBox<String> cmbPiso;
    private final LimpiezaService servicio = new LimpiezaService();
    private Timeline refresco;
    private List<Habitacion> enLimpieza = List.of();
    private boolean cargandoPisos;

    @FXML private void initialize() {
        colNumero.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNumero()));
        colTipo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTipo().getNombre()));
        colPiso.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getPiso())));
        for (TableColumn<Habitacion, String> columna : List.of(colNumero, colTipo, colPiso)) {
            columna.setStyle("-fx-alignment: CENTER-LEFT;");
        }
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) ->
                btnLista.setDisable(ahora == null));
        EstiloGlobal.restilizarEncabezados(tabla);
        cmbPiso.getItems().setAll(TODOS_LOS_PISOS);
        cmbPiso.setValue(TODOS_LOS_PISOS);
        cmbPiso.valueProperty().addListener((obs, antes, ahora) -> mostrar());

        // la lista se renueva sola mientras la pantalla está abierta
        refresco = new Timeline(new KeyFrame(Duration.seconds(SEGUNDOS_ENTRE_ACTUALIZACIONES), e -> cargar(false)));
        refresco.setCycleCount(Timeline.INDEFINITE);
        tabla.sceneProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                refresco.stop();
            } else {
                refresco.play();
            }
        });
        cargar(true);
    }

    @FXML private void actualizar() {
        cargar(true);
    }

    private void cargar(boolean avisar) {
        Task<List<Habitacion>> tarea = new Task<>() {
            @Override protected List<Habitacion> call() throws Exception { return servicio.listarEnLimpieza(); }
        };
        tarea.setOnSucceeded(e -> {
            enLimpieza = tarea.getValue();
            actualizarPisos();
            mostrar();
            lblPendientes.setText(String.valueOf(enLimpieza.size()));
            if (avisar) lblMensaje.setText("Lista actualizada.");
        });
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudieron cargar las habitaciones."));
        ejecutar(tarea);
    }

    /** El filtro ofrece solo los pisos que tienen habitaciones por limpiar. */
    private void actualizarPisos() {
        String elegido = cmbPiso.getValue();
        List<String> pisos = new java.util.ArrayList<>();
        pisos.add(TODOS_LOS_PISOS);
        enLimpieza.stream().map(Habitacion::getPiso).distinct().sorted().forEach(p -> pisos.add("Piso " + p));
        cargandoPisos = true;
        cmbPiso.getItems().setAll(pisos);
        cmbPiso.setValue(pisos.contains(elegido) ? elegido : TODOS_LOS_PISOS);
        cargandoPisos = false;
    }

    private void mostrar() {
        if (cargandoPisos) return;
        Habitacion elegida = tabla.getSelectionModel().getSelectedItem();
        String piso = cmbPiso.getValue();
        List<Habitacion> visibles = enLimpieza.stream()
                .filter(h -> piso == null || TODOS_LOS_PISOS.equals(piso) || piso.equals("Piso " + h.getPiso()))
                .toList();
        tabla.getItems().setAll(visibles);
        if (elegida != null) {
            for (Habitacion h : visibles) {
                if (h.getIdHabitacion() == elegida.getIdHabitacion()) tabla.getSelectionModel().select(h);
            }
        }
    }

    @FXML private void marcarDisponible() {
        Habitacion seleccionada = tabla.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            lblMensaje.setText("Seleccione la habitación que ya se limpió.");
            return;
        }
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                servicio.marcarDisponible(seleccionada.getIdHabitacion());
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            lblMensaje.setText("Habitación " + seleccionada.getNumero() + " disponible.");
            cargar(false);
        });
        tarea.setOnFailed(e -> {
            lblMensaje.setText(tarea.getException() instanceof IllegalStateException
                    ? tarea.getException().getMessage() : "No se pudo cambiar el estado.");
            cargar(false);
        });
        ejecutar(tarea);
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "limpieza-db");
        hilo.setDaemon(true);
        hilo.start();
    }
}
