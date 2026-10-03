package untrm.hotel_san_antonio.controlador;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import untrm.hotel_san_antonio.modelo.TipoHabitacion;
import untrm.hotel_san_antonio.servicio.CategoriaHabitacionService;

public class CategoriasHabitacionController {
    @FXML private TableView<TipoHabitacion> tabla;
    @FXML private TableColumn<TipoHabitacion,String> colNombre, colCapacidad, colPrecio, colNivel;
    @FXML private Spinner<Integer> spNivel;
    @FXML private Button btnGuardar;
    @FXML private Label lblEstado;
    private final CategoriaHabitacionService servicio = new CategoriaHabitacionService();

    @FXML private void initialize() {
        colNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombre()));
        colCapacidad.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getCapacidad())));
        colPrecio.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPrecioBase().toPlainString()));
        colNivel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNivelCategoria() == null
                ? "Sin configurar" : d.getValue().getNivelCategoria().toString()));
        spNivel.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            btnGuardar.setDisable(ahora == null);
            if (ahora != null) spNivel.getValueFactory().setValue(
                    ahora.getNivelCategoria() == null ? 1 : ahora.getNivelCategoria());
        });
        actualizar();
    }

    @FXML private void actualizar() {
        Task<java.util.List<TipoHabitacion>> tarea = new Task<>() {
            @Override protected java.util.List<TipoHabitacion> call() throws Exception { return servicio.listar(); }
        };
        tarea.setOnSucceeded(e -> tabla.getItems().setAll(tarea.getValue()));
        tarea.setOnFailed(e -> lblEstado.setText("No se pudieron cargar los tipos de habitación."));
        ejecutar(tarea);
    }

    @FXML private void guardar() {
        TipoHabitacion seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            lblEstado.setText("Seleccione un tipo de habitación antes de guardar.");
            return;
        }
        int nivel = spNivel.getValue();
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                servicio.guardarNivel(seleccionado.getIdTipo(), nivel); return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            lblEstado.setText("Nivel guardado. Los nuevos cambios de habitación usarán este orden.");
            actualizar();
        });
        tarea.setOnFailed(e -> lblEstado.setText("No se pudo guardar el nivel."));
        ejecutar(tarea);
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "categorias-habitacion-db");
        hilo.setDaemon(true); hilo.start();
    }
}
