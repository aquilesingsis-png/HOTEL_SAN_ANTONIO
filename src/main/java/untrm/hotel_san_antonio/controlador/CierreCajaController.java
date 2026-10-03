package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import untrm.hotel_san_antonio.dao.CajaDAO;
import untrm.hotel_san_antonio.dao.CajaDAO.Cierre;
import untrm.hotel_san_antonio.dao.ModulosDAO;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Cierre de caja con el arqueo adentro: se cuenta el efectivo por billetes y monedas, el contado y la
 * diferencia salen solos, y al confirmar se guarda el cierre y tambien el arqueo del turno.
 */
public class CierreCajaController {

    private static final String[][] BILLETES = {{"Billetes de S/ 200", "200"}, {"Billetes de S/ 100", "100"},
        {"Billetes de S/ 50", "50"}, {"Billetes de S/ 20", "20"}, {"Billetes de S/ 10", "10"}};
    private static final String[][] MONEDAS = {{"Monedas de S/ 5.00", "5"}, {"Monedas de S/ 2.00", "2"},
        {"Monedas de S/ 1.00", "1"}, {"Monedas de S/ 0.50", "0.50"}, {"Monedas de S/ 0.20", "0.20"},
        {"Monedas de S/ 0.10", "0.10"}};
    private static final String ESTILO_SPINNER = "-fx-background-color: white; -fx-border-color: #D9D1C5; "
            + "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-pref-height: 34; -fx-min-height: 34;";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private DatePicker dpFecha;
    @FXML private TextField txtTurno;
    @FXML private TextField txtHoraCierre;
    @FXML private Label lblResponsable;

    @FXML private TextField txtFondoInicial;
    @FXML private TextField txtCobros;
    @FXML private TextField txtOtros;
    @FXML private TextField txtSalidas;
    @FXML private Label lblEsperado;

    @FXML private GridPane gridBilletes;
    @FXML private GridPane gridMonedas;
    @FXML private Label lblTotalBilletes;
    @FXML private Label lblTotalMonedas;
    @FXML private Label lblContado;

    @FXML private Label lblResumenEsperado;
    @FXML private Label lblResumenContado;
    @FXML private Label lblDiferencia;
    @FXML private Label lblDiferenciaTexto;
    @FXML private TextField txtFondoSiguiente;
    @FXML private Label lblEntregado;
    @FXML private TextArea txtObservacion;
    @FXML private CheckBox chkRevisado;
    @FXML private Label lblMensaje;

    @FXML private TableView<Cierre> tablaCierres;
    @FXML private TableColumn<Cierre, String> colNumero;
    @FXML private TableColumn<Cierre, String> colFecha;
    @FXML private TableColumn<Cierre, String> colTurno;
    @FXML private TableColumn<Cierre, String> colResponsable;
    @FXML private TableColumn<Cierre, String> colEsperado;
    @FXML private TableColumn<Cierre, String> colContado;
    @FXML private TableColumn<Cierre, String> colDiferencia;
    @FXML private TableColumn<Cierre, String> colEstado;

    private final ModulosDAO dao = new ModulosDAO();
    private final CajaDAO cajaDAO = new CajaDAO();
    private final List<Spinner<Integer>> spinnersBilletes = new ArrayList<>();
    private final List<Spinner<Integer>> spinnersMonedas = new ArrayList<>();
    private final List<Label> subtotalesBilletes = new ArrayList<>();
    private final List<Label> subtotalesMonedas = new ArrayList<>();
    private boolean cargando;

    @FXML
    public void initialize() {
        dpFecha.setValue(LocalDate.now());
        dpFecha.setEditable(false);
        txtTurno.setText("Caja principal");
        txtHoraCierre.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        lblResponsable.setText(SesionActual.getUsuario().getNombreCompleto());
        txtFondoSiguiente.setText("0.00");

        armarConteo(gridBilletes, BILLETES, spinnersBilletes, subtotalesBilletes);
        armarConteo(gridMonedas, MONEDAS, spinnersMonedas, subtotalesMonedas);
        for (TextField campo : List.of(txtFondoInicial, txtCobros, txtOtros, txtSalidas, txtFondoSiguiente)) {
            campo.textProperty().addListener((obs, antes, ahora) -> recalcular());
        }
        dpFecha.valueProperty().addListener((obs, antes, ahora) -> cargarTurno());
        txtTurno.focusedProperty().addListener((obs, antes, enfocado) -> {
            if (!enfocado) {
                cargarTurno();
            }
        });

        colNumero.setCellValueFactory(d -> new SimpleStringProperty("C-" + String.format("%04d", d.getValue().id())));
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fecha().format(FORMATO_FECHA)));
        colTurno.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().turno()));
        colResponsable.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().responsable()));
        colEsperado.setCellValueFactory(d -> new SimpleStringProperty(dinero(d.getValue().esperado())));
        colContado.setCellValueFactory(d -> new SimpleStringProperty(dinero(d.getValue().contado())));
        colDiferencia.setCellValueFactory(d -> new SimpleStringProperty(dinero(d.getValue().diferencia())));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(
                "CONFIRMADO".equals(d.getValue().estado()) ? "Confirmado" : "Borrador"));

        cargarTurno();
        cargarHistorial();
    }

    // ------------------------------------------------------------------ conteo

    private void armarConteo(GridPane grid, String[][] denominaciones, List<Spinner<Integer>> spinners,
                             List<Label> subtotales) {
        for (int i = 0; i < denominaciones.length; i++) {
            Label nombre = new Label(denominaciones[i][0]);
            Spinner<Integer> spinner = new Spinner<>();
            spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99999, 0));
            spinner.setEditable(true);
            spinner.setPrefWidth(110);
            spinner.setStyle(ESTILO_SPINNER);
            Label subtotal = new Label("S/ 0.00");
            subtotal.setStyle("-fx-text-fill: #5A3516; -fx-font-weight: bold;");
            subtotal.setMinWidth(90);
            subtotal.setAlignment(Pos.CENTER_RIGHT);
            // al escribir a mano tambien se recalcula
            spinner.getEditor().textProperty().addListener((obs, antes, ahora) -> {
                try {
                    spinner.getValueFactory().setValue(ahora.isBlank() ? 0 : Integer.parseInt(ahora.trim()));
                } catch (NumberFormatException error) {
                    // se ignora hasta que el texto sea un numero
                }
            });
            spinner.valueProperty().addListener((obs, antes, ahora) -> recalcular());
            grid.add(nombre, 0, i);
            grid.add(spinner, 1, i);
            grid.add(subtotal, 2, i);
            spinners.add(spinner);
            subtotales.add(subtotal);
        }
    }

    private BigDecimal sumarConteo(String[][] denominaciones, List<Spinner<Integer>> spinners, List<Label> subtotales) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < denominaciones.length; i++) {
            Integer cantidad = spinners.get(i).getValue();
            BigDecimal subtotal = new BigDecimal(denominaciones[i][1])
                    .multiply(BigDecimal.valueOf(cantidad == null ? 0 : cantidad));
            subtotales.get(i).setText(dinero(subtotal));
            total = total.add(subtotal);
        }
        return total;
    }

    // ------------------------------------------------------------------- calculos

    private void recalcular() {
        if (cargando) {
            return;
        }
        BigDecimal esperado = importe(txtFondoInicial).add(importe(txtCobros)).add(importe(txtOtros))
                .subtract(importe(txtSalidas));
        BigDecimal billetes = sumarConteo(BILLETES, spinnersBilletes, subtotalesBilletes);
        BigDecimal monedas = sumarConteo(MONEDAS, spinnersMonedas, subtotalesMonedas);
        BigDecimal contado = billetes.add(monedas);
        BigDecimal diferencia = contado.subtract(esperado);
        BigDecimal entregado = contado.subtract(importe(txtFondoSiguiente));

        lblEsperado.setText(dinero(esperado));
        lblTotalBilletes.setText(dinero(billetes));
        lblTotalMonedas.setText(dinero(monedas));
        lblContado.setText(dinero(contado));
        lblResumenEsperado.setText(dinero(esperado));
        lblResumenContado.setText(dinero(contado));
        lblDiferencia.setText(dinero(diferencia));
        lblEntregado.setText(dinero(entregado));
        if (diferencia.signum() == 0) {
            lblDiferenciaTexto.setText("La caja cuadra");
            lblDiferenciaTexto.setStyle("-fx-text-fill: #1F7A3E; -fx-font-weight: bold;");
        } else if (diferencia.signum() > 0) {
            lblDiferenciaTexto.setText("Sobra efectivo");
            lblDiferenciaTexto.setStyle("-fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
        } else {
            lblDiferenciaTexto.setText("Falta efectivo");
            lblDiferenciaTexto.setStyle("-fx-text-fill: #B3261E; -fx-font-weight: bold;");
        }
    }

    private BigDecimal importe(TextField campo) {
        String valor = campo.getText() == null ? "" : campo.getText().replace("S/", "").replace(',', '.').trim();
        try {
            return valor.isBlank() ? BigDecimal.ZERO : new BigDecimal(valor);
        } catch (NumberFormatException error) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal contadoActual() {
        return sumarConteo(BILLETES, spinnersBilletes, subtotalesBilletes)
                .add(sumarConteo(MONEDAS, spinnersMonedas, subtotalesMonedas));
    }

    // -------------------------------------------------------------------- datos

    /** Trae de la base el fondo inicial, los cobros, los otros ingresos y las salidas en efectivo del turno. */
    @FXML
    private void cargarTurno() {
        if (dpFecha.getValue() == null) {
            return;
        }
        try {
            BigDecimal[] valores = dao.resumenEfectivo(dpFecha.getValue(), txtTurno.getText());
            cargando = true;
            txtFondoInicial.setText(valores[0].toPlainString());
            txtCobros.setText(valores[1].toPlainString());
            txtOtros.setText(valores[2].toPlainString());
            txtSalidas.setText(valores[3].toPlainString());
            cargando = false;
            recalcular();
            lblMensaje.setText("");
        } catch (SQLException | IllegalArgumentException | SecurityException error) {
            cargando = false;
            lblMensaje.setText("No se pudieron cargar los datos del turno: " + error.getMessage());
        }
    }

    private void cargarHistorial() {
        try {
            tablaCierres.getItems().setAll(cajaDAO.listarCierres());
        } catch (SQLException | SecurityException error) {
            tablaCierres.getItems().clear();
            lblMensaje.setText("No se pudo cargar el historial: " + error.getMessage());
        }
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void guardarBorrador() {
        guardar(false);
    }

    @FXML
    private void confirmarCierre() {
        guardar(true);
    }

    @FXML
    private void limpiar() {
        for (Spinner<Integer> spinner : spinnersBilletes) {
            spinner.getValueFactory().setValue(0);
        }
        for (Spinner<Integer> spinner : spinnersMonedas) {
            spinner.getValueFactory().setValue(0);
        }
        txtFondoSiguiente.setText("0.00");
        txtObservacion.clear();
        chkRevisado.setSelected(false);
        lblMensaje.setText("");
        recalcular();
    }

    private void guardar(boolean confirmar) {
        try {
            BigDecimal contado = contadoActual();
            BigDecimal esperado = importe(txtFondoInicial).add(importe(txtCobros)).add(importe(txtOtros))
                    .subtract(importe(txtSalidas));
            BigDecimal fondoSiguiente = importe(txtFondoSiguiente);
            if (fondoSiguiente.compareTo(contado) > 0) {
                throw new IllegalArgumentException("El fondo para el siguiente turno no puede superar el efectivo contado.");
            }
            String hora = txtHoraCierre.getText() == null ? "" : txtHoraCierre.getText().trim();
            if (confirmar) {
                if (contado.signum() == 0 && esperado.signum() > 0) {
                    throw new IllegalArgumentException("Registre el conteo de billetes y monedas antes de confirmar.");
                }
                if (!chkRevisado.isSelected()) {
                    throw new IllegalArgumentException("Marque la confirmación después de revisar los importes.");
                }
                if (hora.isBlank()) {
                    throw new IllegalArgumentException("Indique la hora de cierre en formato HH:mm.");
                }
            }
            if (!hora.isBlank()) {
                try {
                    LocalTime.parse(hora);
                } catch (DateTimeParseException error) {
                    throw new IllegalArgumentException("La hora de cierre debe tener formato HH:mm.");
                }
            }
            if (confirmar && !Alertas.confirmar("Confirmar cierre", "¿Confirmar el cierre del turno \""
                    + txtTurno.getText().trim() + "\"?\n\nUn cierre confirmado ya no se puede modificar.")) {
                return;
            }
            String observacion = (hora.isBlank() ? "" : "Hora de cierre: " + hora + "\n")
                    + (txtObservacion.getText() == null ? "" : txtObservacion.getText().trim());
            dao.guardarCierre(null, new ModulosDAO.CierreDatos(dpFecha.getValue(), txtTurno.getText(),
                    importe(txtFondoInicial), importe(txtCobros), importe(txtOtros), importe(txtSalidas),
                    esperado, contado, contado.subtract(fondoSiguiente), fondoSiguiente, observacion), confirmar);
            if (confirmar) {
                // el conteo por billetes y monedas queda como el arqueo del turno
                dao.guardarArqueo(txtTurno.getText(), esperado, contado, observacion);
            }
            Alertas.mostrarInfo(confirmar ? "Cierre confirmado" : "Borrador guardado",
                    confirmar ? "El cierre del turno y su arqueo quedaron registrados."
                            : "El borrador quedó guardado. Puede volver a guardarlo hasta confirmar el cierre.");
            if (confirmar) {
                limpiar();
            }
            cargarTurno();
            cargarHistorial();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar el cierre.\n\n" + error.getMessage());
        }
    }

    private String dinero(BigDecimal monto) {
        return String.format(Locale.US, "S/ %.2f", monto);
    }
}
