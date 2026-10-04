package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.TipoCambio;
import untrm.hotel_san_antonio.servicio.DecolectaTipoCambioService;
import untrm.hotel_san_antonio.util.ConversorDolar;

/**
 * Ventana pequeña del tipo de cambio: se escribe un monto en dólares o en soles y el otro campo muestra su
 * equivalente al instante. Los dólares se pasan a soles con el precio de Compra y los soles a dólares con el de Venta.
 */
public class ConversorDolarController {

    @FXML private Label lblTasa;
    @FXML private Label lblNota;
    @FXML private TextField txtDolares;
    @FXML private TextField txtSoles;
    @FXML private Label lblMensaje;

    private TipoCambio tipoCambio;
    private boolean escribiendo;

    @FXML
    public void initialize() {
        txtDolares.textProperty().addListener((obs, antes, ahora) -> convertir(true));
        txtSoles.textProperty().addListener((obs, antes, ahora) -> convertir(false));
        TipoCambio actual = DecolectaTipoCambioService.vigente();
        if (actual != null) {
            usar(actual);
            return;
        }
        lblTasa.setText("Consultando el tipo de cambio…");
        Task<TipoCambio> tarea = DecolectaTipoCambioService.consultarHoy();
        tarea.setOnSucceeded(e -> usar(tarea.getValue()));
        tarea.setOnFailed(e -> {
            if (DecolectaTipoCambioService.ultimoConocido() != null) {
                usar(DecolectaTipoCambioService.ultimoConocido());
                return;
            }
            lblTasa.setText("No se pudo consultar el tipo de cambio.");
            lblMensaje.setText("Revise la conexión a internet y la clave de la API en config.properties.");
        });
        Thread hilo = new Thread(tarea, "tipo-cambio-conversor");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void usar(TipoCambio tc) {
        tipoCambio = tc;
        String fecha = tc.getFecha();
        try {
            fecha = LocalDate.parse(tc.getFecha()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (RuntimeException ignorado) {
            // si la API manda otro formato de fecha, se muestra tal cual
        }
        lblTasa.setText(String.format(Locale.US, "1 US$ = S/ %.3f  (Compra)   ·   S/ %.3f  (Venta)",
                tc.getCompra(), tc.getVenta()));
        lblNota.setText("Tipo de cambio SUNAT del " + fecha + ". Dólares a soles usa el precio de Compra; "
                + "soles a dólares usa el de Venta.");
        lblMensaje.setText("");
        convertir(!txtDolares.getText().isBlank());
    }

    /** Recalcula el campo contrario al que se está escribiendo. */
    private void convertir(boolean desdeDolares) {
        if (escribiendo || tipoCambio == null) {
            return;
        }
        TextField origen = desdeDolares ? txtDolares : txtSoles;
        TextField destino = desdeDolares ? txtSoles : txtDolares;
        escribiendo = true;
        try {
            if (origen.getText().isBlank()) {
                destino.clear();
                lblMensaje.setText("");
                return;
            }
            BigDecimal valor = ConversorDolar.leer(origen.getText());
            if (valor == null) {
                destino.clear();
                lblMensaje.setText("Escriba un número válido, por ejemplo 150 o 150.50.");
                return;
            }
            lblMensaje.setText("");
            BigDecimal resultado = desdeDolares
                    ? ConversorDolar.dolaresASoles(valor, tipoCambio.getCompra())
                    : ConversorDolar.solesADolares(valor, tipoCambio.getVenta());
            destino.setText(resultado.toPlainString());
        } finally {
            escribiendo = false;
        }
    }

    @FXML
    private void limpiar() {
        escribiendo = true;
        txtDolares.clear();
        txtSoles.clear();
        escribiendo = false;
        lblMensaje.setText("");
        txtDolares.requestFocus();
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
