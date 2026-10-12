package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.time.LocalDate;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.modelo.Gasto;
import untrm.hotel_san_antonio.servicio.GastoService;
import untrm.hotel_san_antonio.util.Alertas;

public class GastosController {
    @FXML private DatePicker dpFecha, dpDesde, dpHasta;
    @FXML private TextField txtConcepto, txtMonto, txtObservacion;
    @FXML private ChoiceBox<String> cmbCategoria, cmbMetodo;
    @FXML private TextField txtMotivo, txtFiltro;
    @FXML private TableView<Gasto> tabla;
    @FXML private TableColumn<Gasto, String> colFecha, colConcepto, colCategoria, colMonto, colMetodo;
    @FXML private Label lblTotal, lblMensaje;
    @FXML private Button btnDesactivar;
    private final GastoService servicio = new GastoService();
    private int editando;

    @FXML private void initialize() {
        dpFecha.setValue(LocalDate.now());
        cmbCategoria.getItems().setAll(GastoService.CATEGORIAS);
        for (String metodo : GastoService.METODOS) {
            cmbMetodo.getItems().add(nombreMetodo(metodo));
        }
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFecha().toString()));
        colConcepto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConcepto()));
        colCategoria.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoria()));
        colMonto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getMonto().toPlainString()));
        colMetodo.setCellValueFactory(d -> new SimpleStringProperty(nombreMetodo(d.getValue().getMetodoPago())));
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            btnDesactivar.setDisable(ahora == null);
            if (ahora != null) cargarFormulario(ahora);
        });
        filtrar();
    }

    @FXML private void filtrar() {
        Task<java.util.List<Gasto>> tarea = new Task<>() {
            @Override protected java.util.List<Gasto> call() throws Exception {
                return servicio.listar(dpDesde.getValue(), dpHasta.getValue(), txtFiltro.getText());
            }
        };
        tarea.setOnSucceeded(e -> {
            tabla.getItems().setAll(tarea.getValue());
            BigDecimal total = tarea.getValue().stream().map(Gasto::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblTotal.setText("Total filtrado: S/ " + total);
            lblMensaje.setText(tarea.getValue().size() + " gasto(s) encontrado(s).");
        });
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudieron cargar los gastos."));
        ejecutar(tarea);
    }

    @FXML private void guardar() {
        Gasto gasto;
        try {
            gasto = leerFormulario();
        } catch (RuntimeException error) {
            lblMensaje.setText(error.getMessage());
            return;
        }
        Task<Integer> tarea = new Task<>() {
            @Override protected Integer call() throws Exception { return servicio.guardar(gasto); }
        };
        tarea.setOnSucceeded(e -> {
            limpiar();
            filtrar();
        });
        tarea.setOnFailed(e -> {
            Throwable error = tarea.getException();
            lblMensaje.setText(error instanceof IllegalArgumentException || error instanceof IllegalStateException
                    ? error.getMessage() : "No se pudo guardar el gasto.");
        });
        ejecutar(tarea);
    }

    @FXML private void desactivar() {
        Gasto seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null || !Alertas.confirmar("Desactivar gasto",
                "¿Desactivar el gasto seleccionado? Quedará en el historial de auditoría.")) return;
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                servicio.desactivar(seleccionado.getIdGasto());
                return null;
            }
        };
        tarea.setOnSucceeded(e -> { limpiar(); filtrar(); });
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudo desactivar el gasto."));
        ejecutar(tarea);
    }

    @FXML private void limpiar() {
        editando = 0;
        tabla.getSelectionModel().clearSelection();
        dpFecha.setValue(LocalDate.now());
        txtConcepto.clear(); txtMonto.clear();
        cmbCategoria.setValue(null); cmbMetodo.setValue(null);
        txtObservacion.clear(); txtMotivo.clear();
    }

    private void cargarFormulario(Gasto g) {
        editando = g.getIdGasto();
        dpFecha.setValue(g.getFecha());
        txtConcepto.setText(g.getConcepto());
        cmbCategoria.setValue(GastoService.CATEGORIAS.contains(g.getCategoria()) ? g.getCategoria() : null);
        txtMonto.setText(g.getMonto().toPlainString());
        String metodo = nombreMetodo(g.getMetodoPago());
        cmbMetodo.setValue(cmbMetodo.getItems().contains(metodo) ? metodo : null);
        txtObservacion.setText(g.getObservacion());
        txtMotivo.setText(g.getMotivoRegistroTardio());
    }

    private Gasto leerFormulario() {
        Gasto g = new Gasto();
        g.setIdGasto(editando);
        g.setFecha(dpFecha.getValue());
        g.setConcepto(txtConcepto.getText().trim());
        g.setCategoria(cmbCategoria.getValue());
        try { g.setMonto(new BigDecimal(txtMonto.getText().trim().replace(',', '.'))); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Ingrese un monto numérico válido."); }
        g.setMetodoPago(cmbMetodo.getValue() == null ? null : cmbMetodo.getValue().toUpperCase(java.util.Locale.ROOT));
        g.setObservacion(txtObservacion.getText().trim());
        g.setMotivoRegistroTardio(txtMotivo.getText().trim());
        return g;
    }

    /** EFECTIVO -> Efectivo (como se ve en pantalla); lo que no es un medio conocido se deja tal cual. */
    private String nombreMetodo(String metodo) {
        if (metodo == null || metodo.isEmpty()) {
            return "";
        }
        return metodo.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + metodo.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "gastos-db");
        hilo.setDaemon(true);
        hilo.start();
    }
}
