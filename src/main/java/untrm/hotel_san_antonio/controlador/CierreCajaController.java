package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
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
import untrm.hotel_san_antonio.dao.CierreCajaDAO;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Cierre de caja por turno (mañana, tarde o noche) con el arqueo adentro. El sistema calcula lo que debe
 * haber en la caja: la caja chica con la que empezó el turno más lo cobrado, menos lo pagado, desde el cierre
 * anterior. Quien cierra solo cuenta el efectivo por billetes y monedas; la diferencia sale sola. Lo que queda
 * en la caja (la caja chica) es con lo que empieza el turno siguiente.
 */
public class CierreCajaController {

    private static final String[][] BILLETES = {{"Billetes de S/ 200", "200"}, {"Billetes de S/ 100", "100"},
        {"Billetes de S/ 50", "50"}, {"Billetes de S/ 20", "20"}, {"Billetes de S/ 10", "10"}};
    private static final String[][] MONEDAS = {{"Monedas de S/ 5.00", "5"}, {"Monedas de S/ 2.00", "2"},
        {"Monedas de S/ 1.00", "1"}, {"Monedas de S/ 0.50", "0.50"}, {"Monedas de S/ 0.20", "0.20"},
        {"Monedas de S/ 0.10", "0.10"}};
    private static final List<String> TURNOS = List.of("Mañana", "Tarde", "Noche");
    private static final String ESTILO_SPINNER = "-fx-background-color: white; -fx-border-color: #D9D1C5; "
            + "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-pref-height: 34; -fx-min-height: 34;";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMATO_PERIODO = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    @FXML private DatePicker dpFecha;
    @FXML private ChoiceBox<String> cmbTurno;
    @FXML private TextField txtHoraCierre;
    @FXML private Label lblResponsable;
    @FXML private Label lblPeriodo;

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
    @FXML private TableColumn<Cierre, String> colHora;
    @FXML private TableColumn<Cierre, String> colTurno;
    @FXML private TableColumn<Cierre, String> colResponsable;
    @FXML private TableColumn<Cierre, String> colEsperado;
    @FXML private TableColumn<Cierre, String> colContado;
    @FXML private TableColumn<Cierre, String> colDiferencia;
    @FXML private TableColumn<Cierre, String> colEstado;

    private final CierreCajaDAO dao = new CierreCajaDAO();
    private final CajaDAO cajaDAO = new CajaDAO();
    private final List<Spinner<Integer>> spinnersBilletes = new ArrayList<>();
    private final List<Spinner<Integer>> spinnersMonedas = new ArrayList<>();
    private final List<Label> subtotalesBilletes = new ArrayList<>();
    private final List<Label> subtotalesMonedas = new ArrayList<>();
    private boolean cargando;
    /** Mientras no se escriba otra cifra, la caja chica que queda es la misma con la que se empezó. */
    private boolean cajaChicaEscrita;

    @FXML
    public void initialize() {
        dpFecha.setValue(LocalDate.now());
        dpFecha.setDisable(true);
        dpFecha.setStyle(dpFecha.getStyle() + " -fx-opacity: 1;");
        cmbTurno.getItems().setAll(TURNOS);
        cmbTurno.setValue(turnoSegunHora());
        lblResponsable.setText(SesionActual.getUsuario().getNombreCompleto());
        txtFondoSiguiente.setText("0.00");
        txtFondoSiguiente.setOnKeyTyped(evento -> cajaChicaEscrita = true);

        armarConteo(gridBilletes, BILLETES, spinnersBilletes, subtotalesBilletes);
        armarConteo(gridMonedas, MONEDAS, spinnersMonedas, subtotalesMonedas);
        for (TextField campo : List.of(txtFondoInicial, txtCobros, txtOtros, txtSalidas, txtFondoSiguiente)) {
            campo.textProperty().addListener((obs, antes, ahora) -> recalcular());
        }
        // la primera vez (sin cierre anterior) la caja chica inicial se escribe a mano y la que queda la acompaña
        txtFondoInicial.textProperty().addListener((obs, antes, ahora) -> {
            if (!cargando && !cajaChicaEscrita) {
                txtFondoSiguiente.setText(ahora);
            }
        });

        colNumero.setCellValueFactory(d -> new SimpleStringProperty("C-" + String.format("%04d", d.getValue().id())));
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fecha().format(FORMATO_FECHA)));
        colHora.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().confirmado() == null ? "—" : d.getValue().confirmado().format(FORMATO_HORA)));
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

    /** El turno que probablemente se esta cerrando, segun la hora; se puede cambiar. */
    private String turnoSegunHora() {
        LocalTime ahora = LocalTime.now();
        return ahora.isBefore(LocalTime.of(14, 0)) ? TURNOS.get(0) : ahora.isBefore(LocalTime.of(22, 0)) ? TURNOS.get(1) : TURNOS.get(2);
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

    /** Trae de la base la caja chica inicial, los cobros, los otros ingresos y las salidas en efectivo del turno. */
    @FXML
    private void cargarTurno() {
        try {
            CierreCajaDAO.Resumen resumen = dao.resumen();
            cargando = true;
            txtFondoInicial.setText(resumen.fondoInicial().setScale(2).toPlainString());
            // sin un cierre anterior no hay de donde sacar la caja chica: se escribe una sola vez, a mano
            txtFondoInicial.setEditable(!resumen.hayCierrePrevio());
            txtCobros.setText(resumen.cobros().setScale(2).toPlainString());
            txtOtros.setText(resumen.otros().setScale(2).toPlainString());
            txtSalidas.setText(resumen.salidas().setScale(2).toPlainString());
            if (!cajaChicaEscrita) {
                txtFondoSiguiente.setText(resumen.fondoInicial().setScale(2).toPlainString());
            }
            txtHoraCierre.setText(resumen.hasta().format(FORMATO_HORA));
            lblPeriodo.setText(resumen.hayCierrePrevio()
                    ? "Cuenta lo cobrado y pagado desde el cierre anterior (" + resumen.desde().format(FORMATO_PERIODO) + ")."
                    : "Todavía no hay un cierre anterior: cuenta desde las 00:00 de hoy. Escriba la caja chica con la que empezó el turno.");
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
        cajaChicaEscrita = false;
        txtFondoSiguiente.setText(txtFondoInicial.getText());
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
            String turno = cmbTurno.getValue();
            if (turno == null) {
                throw new IllegalArgumentException("Seleccione el turno que se cierra.");
            }
            if (fondoSiguiente.compareTo(contado) > 0) {
                throw new IllegalArgumentException("La caja chica que queda no puede superar el efectivo contado.");
            }
            if (confirmar) {
                if (contado.signum() == 0 && esperado.signum() > 0) {
                    throw new IllegalArgumentException("Registre el conteo de billetes y monedas antes de confirmar.");
                }
                if (!chkRevisado.isSelected()) {
                    throw new IllegalArgumentException("Marque la confirmación después de revisar los importes.");
                }
                if (!Alertas.confirmar("Confirmar cierre", "¿Confirmar el cierre del turno «" + turno + "»?\n\n"
                        + "Un cierre confirmado ya no se puede modificar, y el siguiente turno empieza con la caja chica de "
                        + dinero(fondoSiguiente) + ".")) {
                    return;
                }
            }
            String observacion = txtObservacion.getText() == null ? "" : txtObservacion.getText().trim();
            dao.guardar(new CierreCajaDAO.Entrada(turno, importe(txtFondoInicial), importe(txtCobros),
                    importe(txtOtros), importe(txtSalidas), contado, fondoSiguiente, observacion), confirmar);
            Alertas.mostrarInfo(confirmar ? "Cierre confirmado" : "Borrador guardado",
                    confirmar ? "El cierre del turno quedó registrado."
                            : "El borrador quedó guardado. Puede volver a guardarlo hasta confirmar el cierre.");
            if (confirmar) {
                limpiar();
            }
            cargarTurno();
            cargarHistorial();
        } catch (IllegalStateException error) {
            // casi siempre: se registraron movimientos mientras se contaba. Se actualizan los importes y se avisa.
            cargarTurno();
            lblMensaje.setText(error.getMessage());
        } catch (IllegalArgumentException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar el cierre.\n\n" + error.getMessage());
        }
    }

    private String dinero(BigDecimal monto) {
        return String.format(Locale.US, "S/ %.2f", monto);
    }
}
