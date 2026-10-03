package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.dao.ModulosDAO;
import untrm.hotel_san_antonio.util.Alertas;

/** Ventana para registrar a mano un ingreso o un egreso de caja (solo el Administrador). */
public class MovimientoCajaFormController {

    @FXML private ChoiceBox<String> cmbTipo;
    @FXML private TextField txtConcepto;
    @FXML private ChoiceBox<String> cmbMetodo;
    @FXML private TextField txtMonto;
    @FXML private TextField txtReferencia;
    @FXML private TextField txtTurno;
    @FXML private TextArea txtObservacion;
    @FXML private Label lblMensaje;

    private final ModulosDAO dao = new ModulosDAO();
    private Runnable alGuardar;

    @FXML
    public void initialize() {
        cmbTipo.getItems().setAll("Ingreso", "Egreso");
        cmbTipo.setValue("Egreso");
        cmbMetodo.getItems().setAll("Efectivo", "Yape", "Transferencia", "Tarjeta");
        cmbMetodo.setValue("Efectivo");
        txtTurno.setText("Caja principal");
    }

    /** @param alGuardar se ejecuta al guardar, para que la lista de Caja se actualice */
    public void iniciar(Runnable alGuardar) {
        this.alGuardar = alGuardar;
    }

    @FXML
    private void guardar() {
        BigDecimal monto;
        try {
            monto = new BigDecimal(txtMonto.getText().trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            lblMensaje.setText("Ingrese un monto válido.");
            return;
        }
        try {
            dao.guardarMovimientoCaja(cmbTipo.getValue().toUpperCase(Locale.ROOT), txtConcepto.getText(),
                    cmbMetodo.getValue().toUpperCase(Locale.ROOT), monto, txtReferencia.getText().trim(),
                    txtObservacion.getText(), txtTurno.getText());
            Alertas.mostrarInfo("Movimiento registrado", "El movimiento se guardó en la caja.");
            if (alGuardar != null) {
                alGuardar.run();
            }
            cerrar();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar el movimiento.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
