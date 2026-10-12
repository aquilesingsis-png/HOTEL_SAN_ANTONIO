package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
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
    @FXML private VBox boxCompra;
    @FXML private TextField txtCosto;
    @FXML private Label lblTotalCompra;
    @FXML private CheckBox chkGasto;
    @FXML private VBox boxMetodoPago;
    @FXML private ChoiceBox<String> cmbMetodoPago;

    private static final java.util.Map<String, String> METODOS = new java.util.LinkedHashMap<>();
    static {
        METODOS.put("Efectivo", "EFECTIVO");
        METODOS.put("Yape", "YAPE");
        METODOS.put("Transferencia", "TRANSFERENCIA");
        METODOS.put("Tarjeta", "TARJETA");
    }

    private final AlmacenDAO dao = new AlmacenDAO();
    private Item producto;
    private Runnable alGuardar;

    @FXML
    public void initialize() {
        cmbTipo.getItems().setAll(ENTRADA, SALIDA, AJUSTE);
        cmbTipo.setValue(ENTRADA);
        cmbMotivo.getItems().setAll("Compra", "Consumo", "Merma", "Corrección");
        cmbMotivo.setValue("Compra");
        cmbMetodoPago.getItems().setAll(METODOS.keySet());
        cmbMetodoPago.setValue("Efectivo");
        txtCosto.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().matches("\\d{0,4}(\\.\\d{0,2})?") ? cambio : null));
        boxMetodoPago.visibleProperty().bind(chkGasto.selectedProperty());
        boxMetodoPago.managedProperty().bind(boxMetodoPago.visibleProperty());
        cmbTipo.valueProperty().addListener((o, antes, ahora) -> recalcular());
        cmbMotivo.valueProperty().addListener((o, antes, ahora) -> recalcular());
        txtCantidad.textProperty().addListener((o, antes, ahora) -> recalcular());
        txtCosto.textProperty().addListener((o, antes, ahora) -> recalcular());
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

    /** Una entrada con motivo «Compra» pide el costo y puede anotarse como gasto. */
    private boolean esCompra() {
        return ENTRADA.equals(cmbTipo.getValue()) && AlmacenDAO.MOTIVO_COMPRA.equals(cmbMotivo.getValue());
    }

    private BigDecimal leerCosto() {
        try {
            return new BigDecimal(txtCosto.getText().trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** Muestra cómo quedaría el stock antes de guardar. */
    private void recalcular() {
        boolean compra = esCompra();
        boxCompra.setVisible(compra);
        boxCompra.setManaged(compra);
        BigDecimal costo = leerCosto();
        try {
            int unidades = Integer.parseInt(txtCantidad.getText().trim());
            lblTotalCompra.setText(costo == null ? "—"
                    : "S/ " + costo.multiply(BigDecimal.valueOf(unidades)).setScale(2, java.math.RoundingMode.HALF_UP));
        } catch (NumberFormatException error) {
            lblTotalCompra.setText("—");
        }
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
            BigDecimal costo = null;
            String metodo = null;
            if (esCompra()) {
                costo = leerCosto();
                if (costo == null) {
                    lblMensaje.setText("Escribe el costo por unidad de la compra (por ejemplo 1.20).");
                    return;
                }
                metodo = chkGasto.isSelected() ? METODOS.get(cmbMetodoPago.getValue()) : null;
            }
            dao.moverStock(producto.id(), tipo(), cantidad, cmbMotivo.getValue(), txtReferencia.getText(),
                    txtObservacion.getText(), costo, metodo);
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
