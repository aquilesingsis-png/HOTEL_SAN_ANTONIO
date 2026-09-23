package untrm.hotel_san_antonio.controlador;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.servicio.CuentaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.CampoValidacion;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ventana "Cuenta de la habitacion": se abre al hacer clic en una habitacion ocupada.
 * Muestra cargos, pagos y saldo; permite cobrar y hacer el check-out.
 */
public class CuentaHabitacionController {

    private static final Map<String, String> METODOS = new LinkedHashMap<>();
    static {
        METODOS.put("Efectivo", "EFECTIVO");
        METODOS.put("Yape", "YAPE");
        METODOS.put("Transferencia", "TRANSFERENCIA");
        METODOS.put("Tarjeta", "TARJETA");
    }

    @FXML private Label lblTitulo, lblEstadia;
    @FXML private Label lblHuesped, lblDocumento, lblTelefono, lblEmpresa;
    @FXML private VBox boxEmpresa;
    @FXML private Label lblTotalCuenta, lblTotalPagos, lblSaldo;
    @FXML private TableView<CuentaHabitacion.Linea> tblCargos;
    @FXML private TableView<CuentaHabitacion.PagoLinea> tblPagos;
    @FXML private TextField txtMonto;
    @FXML private ComboBox<String> cmbMetodo;
    @FXML private Spinner<Integer> spnNoches;
    @FXML private Label lblNuevaSalida;
    @FXML private Button btnAmpliar;
    @FXML private Button btnRegistrarPago, btnCheckOut, btnCerrar;
    @FXML private CheckBox chkDividirPago;
    @FXML private VBox boxSegundoPago;
    @FXML private ComboBox<String> cmbMetodo2;
    @FXML private TextField txtMonto2;
    @FXML private Label lblMetodo1, lblDivision;
    @FXML private Label lblAviso;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CuentaService cuentaService = new CuentaService();

    private CuentaHabitacion cuentaActual;
    private Habitacion habitacion;
    private Runnable alCambiar;

    @FXML
    public void initialize() {
        untrm.hotel_san_antonio.util.EstiloBoton.aplicarHoverPrimario(btnCheckOut);
        cmbMetodo.setValue("Efectivo");
        cmbMetodo2.setValue("Yape");
        txtMonto2.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().matches("\\d{0,5}(\\.\\d{0,2})?") ? cambio : null));
        boxSegundoPago.visibleProperty().bind(chkDividirPago.selectedProperty());
        boxSegundoPago.managedProperty().bind(boxSegundoPago.visibleProperty());
        txtMonto2.textProperty().addListener((obs, antes, ahora) -> mostrarDivision());
        txtMonto.textProperty().addListener((obs, antes, ahora) -> mostrarDivision());
        chkDividirPago.selectedProperty().addListener((obs, antes, ahora) -> mostrarDivision());
        cmbMetodo.valueProperty().addListener((obs, antes, ahora) -> mostrarDivision());
        cmbMetodo2.valueProperty().addListener((obs, antes, ahora) -> mostrarDivision());
        CampoValidacion.limpiarAlEditar(txtMonto, txtMonto2, cmbMetodo2, spnNoches);
        spnNoches.valueProperty().addListener((obs, antes, ahora) -> mostrarVistaPreviaAmpliacion());
        // solo digitos y hasta 2 decimales
        txtMonto.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().matches("\\d{0,5}(\\.\\d{0,2})?") ? cambio : null));
    }

    /** @param alCambiar se ejecuta despues del check-out, para que la lista de habitaciones se actualice */
    public void iniciar(Habitacion h, CuentaHabitacion cuenta, Runnable alCambiar) {
        this.habitacion = h;
        this.alCambiar = alCambiar;
        lblTitulo.setText("Cuenta · Habitación " + h.getNumero() + " · " + h.getTipo().getNombre());
        mostrar(cuenta);
    }

    private void mostrar(CuentaHabitacion cuenta) {
        this.cuentaActual = cuenta;
        mostrarVistaPreviaAmpliacion();
        lblEstadia.setText("Ingreso " + cuenta.getFechaIngreso() + "  ·  Salida " + cuenta.getFechaSalida());
        lblHuesped.setText(cuenta.getHuesped());
        lblDocumento.setText(("DNI".equals(cuenta.getTipoDocumento()) ? "DNI " : "Pasaporte ") + cuenta.getNumDocumento());
        lblTelefono.setText(cuenta.getTelefono() == null || cuenta.getTelefono().isEmpty() ? "—" : cuenta.getTelefono());
        boolean conEmpresa = cuenta.getEmpresa() != null;
        boxEmpresa.setVisible(conEmpresa);
        boxEmpresa.setManaged(conEmpresa);
        lblEmpresa.setText(conEmpresa ? cuenta.getEmpresa() : "");

        tblCargos.setItems(FXCollections.observableArrayList(cuenta.getCargos()));
        tblPagos.setItems(FXCollections.observableArrayList(cuenta.getPagos()));

        BigDecimal saldo = cuenta.getSaldo();
        boolean pendiente = saldo.signum() > 0;
        lblTotalCuenta.setText("S/ " + CuentaHabitacion.formato(cuenta.getTotalCuenta()));
        lblTotalPagos.setText("S/ " + CuentaHabitacion.formato(cuenta.getTotalPagos()));
        lblSaldo.setText("S/ " + CuentaHabitacion.formato(saldo.max(BigDecimal.ZERO)));
        lblSaldo.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: "
                + (pendiente ? "#DC2626;" : "#16A34A;"));

        // Sin saldo pendiente no hay nada que cobrar y ya se puede hacer el check-out
        txtMonto.setText(pendiente ? CuentaHabitacion.formato(saldo) : "");
        txtMonto.setDisable(!pendiente);
        cmbMetodo.setDisable(!pendiente);
        btnRegistrarPago.setDisable(!pendiente);
        chkDividirPago.setDisable(!pendiente);
        if (!pendiente) {
            chkDividirPago.setSelected(false);
        }
        txtMonto2.clear();
        btnCheckOut.setDisable(pendiente);
        lblAviso.setVisible(pendiente);
        lblAviso.setManaged(pendiente);
    }

    @FXML
    private void onRegistrarPago() {
        CampoValidacion.limpiar(txtMonto, txtMonto2, cmbMetodo2);
        boolean hayError = false;

        BigDecimal monto = leerMonto(txtMonto);
        if (monto == null || monto.signum() <= 0) {
            CampoValidacion.marcar(txtMonto, "Ingresa un monto válido, mayor a 0.");
            hayError = true;
        } else if (monto.compareTo(cuentaActual.getSaldo()) > 0) {
            CampoValidacion.marcar(txtMonto, "No puede superar el saldo pendiente (S/ "
                    + CuentaHabitacion.formato(cuentaActual.getSaldo()) + ").");
            hayError = true;
        }

        BigDecimal segundo = null;
        if (chkDividirPago.isSelected()) {
            segundo = leerMonto(txtMonto2);
            if (segundo == null || segundo.signum() <= 0 || (monto != null && segundo.compareTo(monto) >= 0)) {
                CampoValidacion.marcar(txtMonto2, "Indica cuánto se paga con el segundo método (mayor a 0 y menor al total a pagar).");
                hayError = true;
            }
            if (cmbMetodo.getValue().equals(cmbMetodo2.getValue())) {
                CampoValidacion.marcar(cmbMetodo2, "Elige dos métodos de pago distintos.");
                hayError = true;
            }
        }
        if (hayError) {
            return;
        }

        // Con dos metodos: el segundo monto se descuenta del total a pagar, el resto va al primer metodo
        List<Pago> partes = new ArrayList<>();
        if (segundo != null) {
            partes.add(nuevoPago(monto.subtract(segundo), cmbMetodo.getValue()));
            partes.add(nuevoPago(segundo, cmbMetodo2.getValue()));
        } else {
            partes.add(nuevoPago(monto, cmbMetodo.getValue()));
        }

        try {
            cuentaService.registrarPagos(habitacion.getIdHabitacion(), partes);
            recargar();
        } catch (IllegalStateException e) {
            Alertas.mostrarError("Registrar pago", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error", "No se pudo registrar el pago.\n\n" + e.getMessage());
        }
    }

    private BigDecimal leerMonto(TextField campo) {
        try {
            return new BigDecimal(campo.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Pago nuevoPago(BigDecimal monto, String metodo) {
        Pago pago = new Pago();
        pago.setMonto(monto);
        pago.setMetodoPago(METODOS.get(metodo));
        return pago;
    }

    /** Explica como se reparte el pago cuando se usan dos metodos. */
    private void mostrarDivision() {
        boolean dividir = chkDividirPago.isSelected();
        lblMetodo1.setText(dividir ? "Primer método" : "Método de pago");
        if (!dividir) {
            return;
        }
        try {
            BigDecimal total = new BigDecimal(txtMonto.getText().trim());
            BigDecimal segundo = new BigDecimal(txtMonto2.getText().trim());
            if (segundo.signum() > 0 && segundo.compareTo(total) < 0) {
                lblDivision.setText(cmbMetodo.getValue() + ": S/ " + CuentaHabitacion.formato(total.subtract(segundo))
                        + "  ·  " + cmbMetodo2.getValue() + ": S/ " + CuentaHabitacion.formato(segundo));
                return;
            }
        } catch (NumberFormatException e) {
            // aun no hay montos validos
        }
        lblDivision.setText("Indica cuánto se paga con el segundo método.");
    }

    /** Muestra la nueva fecha de salida y cuanto se suma a la cuenta segun las noches elegidas. */
    private void mostrarVistaPreviaAmpliacion() {
        if (cuentaActual == null || spnNoches.getValue() == null) {
            return;
        }
        int noches = spnNoches.getValue();
        BigDecimal incremento = cuentaActual.getTarifaNoche().multiply(BigDecimal.valueOf(noches));
        lblNuevaSalida.setText("Nueva salida: " + cuentaActual.getSalida().plusDays(noches).format(FORMATO_FECHA)
                + " · + S/ " + CuentaHabitacion.formato(incremento));
    }

    @FXML
    private void onAmpliar() {
        int noches = spnNoches.getValue();
        BigDecimal incremento = cuentaActual.getTarifaNoche().multiply(BigDecimal.valueOf(noches));
        if (!Alertas.confirmar("Ampliar estadía", "¿Agregar " + noches + (noches == 1 ? " noche" : " noches")
                + " a la estadía de " + cuentaActual.getHuesped() + "?\n\nNueva salida: "
                + cuentaActual.getSalida().plusDays(noches).format(FORMATO_FECHA)
                + "\nSe sumarán S/ " + CuentaHabitacion.formato(incremento) + " a la cuenta.")) {
            return;
        }
        try {
            cuentaService.ampliarEstadia(habitacion.getIdHabitacion(), spnNoches.getValue());
            spnNoches.getValueFactory().setValue(1);
            recargar();
        } catch (IllegalStateException e) {
            CampoValidacion.marcar(spnNoches, e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error", "No se pudo ampliar la estadía.\n\n" + e.getMessage());
        }
    }

    @FXML
    private void onCheckOut() {
        if (!Alertas.confirmar("Check-Out", "¿Confirmas el check-out de la habitación " + habitacion.getNumero()
                + "? La habitación pasará a Limpieza.")) {
            return;
        }
        try {
            cuentaService.checkOut(habitacion.getIdHabitacion());
            if (alCambiar != null) {
                alCambiar.run();
            }
            cerrar();
        } catch (IllegalStateException e) {
            Alertas.mostrarError("Check-Out", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error", "No se pudo hacer el check-out.\n\n" + e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        cerrar();
    }

    private void recargar() throws SQLException {
        CuentaHabitacion cuenta = cuentaService.obtener(habitacion.getIdHabitacion());
        if (cuenta == null) {
            cerrar();
            return;
        }
        mostrar(cuenta);
    }

    private void cerrar() {
        ((Stage) btnCerrar.getScene().getWindow()).close();
    }
}
