package untrm.hotel_san_antonio.controlador;

import java.sql.SQLException;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.dao.AlmacenDAO;
import untrm.hotel_san_antonio.dao.AlmacenDAO.Item;
import untrm.hotel_san_antonio.util.Alertas;

/** Ventana para registrar una entrada, una salida o un conteo físico de un producto. */
public class StockFormController {

    private static final String ENTRADA = "Entrada (llegan unidades)";
    private static final String SALIDA = "Salida (se retiran unidades)";
    private static final String AJUSTE = "Ajuste por conteo (existencia real)";

    @FXML private Label lblProducto;
    @FXML private Label lblStockActual;
    @FXML private ChoiceBox<String> cmbTipo;
    @FXML private TextField txtCantidad;
    @FXML private Label lblAyuda;
    @FXML private Label lblResultado;
    @FXML private ChoiceBox<String> cmbMotivo;
    @FXML private TextField txtReferencia;
    @FXML private TextArea txtObservacion;
    @FXML private Label lblMensaje;

    private final AlmacenDAO dao = new AlmacenDAO();
    private Item producto;
    private Runnable alGuardar;

    @FXML
    public void initialize() {
        cmbTipo.getItems().setAll(ENTRADA, SALIDA, AJUSTE);
        cmbTipo.setValue(ENTRADA);
        cmbMotivo.getItems().setAll("Compra", "Consumo", "Merma", "Corrección");
        cmbMotivo.setValue("Compra");
        cmbTipo.valueProperty().addListener((o, antes, ahora) -> recalcular());
        txtCantidad.textProperty().addListener((o, antes, ahora) -> recalcular());
        recalcular();
    }

    public void iniciar(Item producto, Runnable alGuardar) {
        this.producto = producto;
        this.alGuardar = alGuardar;
        lblProducto.setText(producto.codigo() + " · " + producto.nombre());
        lblStockActual.setText(producto.stock() + " unidades");
        recalcular();
    }

    private String tipo() {
        return switch (cmbTipo.getValue()) {
            case SALIDA -> "SALIDA";
            case AJUSTE -> "AJUSTE";
            default -> "ENTRADA";
        };
    }

    /** Muestra cómo quedaría el stock antes de guardar. */
    private void recalcular() {
        lblAyuda.setText(AJUSTE.equals(cmbTipo.getValue())
                ? "Escribe las unidades que contaste físicamente: ese será el nuevo stock."
                : "Escribe cuántas unidades " + (SALIDA.equals(cmbTipo.getValue()) ? "salen." : "entran."));
        if (producto == null) {
            return;
        }
        int cantidad;
        try {
            cantidad = Integer.parseInt(txtCantidad.getText().trim());
        } catch (NumberFormatException error) {
            lblResultado.setText("—");
            return;
        }
        int resultado = switch (tipo()) {
            case "AJUSTE" -> cantidad;
            case "SALIDA" -> producto.stock() - cantidad;
            default -> producto.stock() + cantidad;
        };
        lblResultado.setText(resultado < 0 ? "Stock insuficiente" : resultado + " unidades");
    }

    @FXML
    private void guardar() {
        lblMensaje.setText("");
        int cantidad;
        try {
            cantidad = Integer.parseInt(txtCantidad.getText().trim());
        } catch (NumberFormatException error) {
            lblMensaje.setText("La cantidad debe ser un número entero.");
            return;
        }
        try {
            dao.moverStock(producto.id(), tipo(), cantidad, cmbMotivo.getValue(), txtReferencia.getText(),
                    txtObservacion.getText());
            if (alGuardar != null) {
                alGuardar.run();
            }
            cerrar();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | ArithmeticException error) {
            lblMensaje.setText(error.getMessage() == null ? "La cantidad es demasiado grande." : error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo registrar el movimiento.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
