/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador.Reserva;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
/**
 *
 * @author HP
 */
public class Nueva_reservaController {


    // =========================================================
    // CLIENTE
    // =========================================================

    @FXML
    private ToggleButton btnPersonaNatural;

    @FXML
    private ToggleButton btnEmpresa;

    @FXML
    private Label lblTipoDocumento;

    @FXML
    private TextField txtDocumento;

    @FXML
    private VBox panelSinCliente;

    @FXML
    private VBox panelClienteEncontrado;

    @FXML
    private Label lblNombres;

    @FXML
    private Label lblApellidos;

    @FXML
    private Label lblNacimiento;

    @FXML
    private Label lblCelular;

    @FXML
    private Label lblCorreo;

    @FXML
    private Label lblDireccion;


    // =========================================================
    // DATOS DE LA RESERVA
    // =========================================================

    @FXML
    private DatePicker dpFechaIngreso;

    @FXML
    private DatePicker dpFechaSalida;

    @FXML
    private ChoiceBox<String> cbHoraIngreso;

    @FXML
    private ChoiceBox<String> cbHoraSalida;

    @FXML
    private Label lblNoches;

    @FXML
    private Spinner<Integer> spHuespedes;

    @FXML
    private TextArea txtObservaciones;

    @FXML
    private Label lblContadorObservaciones;


    // =========================================================
    // HABITACIONES
    // =========================================================

    @FXML
    private ChoiceBox<String> cbPiso;

    @FXML
    private ChoiceBox<String> cbTipoHabitacion;

    @FXML
    private VBox contenedorHabitaciones;

    @FXML
    private Label lblSinHabitaciones;


    // =========================================================
    // RESUMEN
    // =========================================================

    @FXML
    private Label lblHabitacionResumen;

    @FXML
    private Label lblNochesResumen;

    @FXML
    private Label lblPrecioNoche;

    @FXML
    private Label lblTotal;


    // =========================================================
    // PAGO
    // =========================================================

    @FXML
    private ToggleButton btnEfectivo;

    @FXML
    private ToggleButton btnTarjeta;

    @FXML
    private ToggleButton btnTransferencia;

    @FXML
    private ToggleButton btnYape;

    @FXML
    private TextField txtMontoRecibido;

    @FXML
    private TextField txtVuelto;


    // =========================================================
    // COMPROBANTE
    // =========================================================

    @FXML
    private RadioButton rbBoleta;

    @FXML
    private RadioButton rbFactura;

    @FXML
    private TextField txtDocumentoComprobante;

    @FXML
    private TextField txtNombreComprobante;


    // =========================================================
    // GRUPOS
    // =========================================================

    private final ToggleGroup grupoTipoCliente =
            new ToggleGroup();

    private final ToggleGroup grupoMetodoPago =
            new ToggleGroup();

    private final ToggleGroup grupoComprobante =
            new ToggleGroup();


    // =========================================================
    // VARIABLES DE LA RESERVA
    // =========================================================

    private boolean clienteSeleccionado = false;

    private String habitacionSeleccionada = null;

    private double precioNoche = 0.00;

    private double totalReserva = 0.00;


    // =========================================================
    // INITIALIZE
    // =========================================================

    @FXML
    public void initialize() {

        configurarTipoCliente();

        configurarFechas();

        configurarHoras();

        configurarHuespedes();

        configurarObservaciones();

        configurarFiltrosHabitacion();

        configurarMetodosPago();

        configurarComprobante();

        configurarMontoRecibido();

        limpiarCliente();

        limpiarHabitaciones();

        actualizarResumen();
    }


    // =========================================================
    // TIPO DE CLIENTE
    // =========================================================

    private void configurarTipoCliente() {

        btnPersonaNatural.setToggleGroup(
                grupoTipoCliente
        );

        btnEmpresa.setToggleGroup(
                grupoTipoCliente
        );

        btnPersonaNatural.setSelected(true);


        grupoTipoCliente
                .selectedToggleProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            if (actual == null) {

                                if (anterior != null) {
                                    anterior.setSelected(true);
                                }

                                return;
                            }

                            actualizarTipoCliente();
                        }
                );


        actualizarTipoCliente();
    }


    private void actualizarTipoCliente() {

        limpiarCliente();


        if (btnEmpresa.isSelected()) {

            lblTipoDocumento.setText(
                    "RUC de la empresa *"
            );

            txtDocumento.setPromptText(
                    "Ingrese RUC"
            );

        } else {

            lblTipoDocumento.setText(
                    "DNI del cliente *"
            );

            txtDocumento.setPromptText(
                    "Ingrese DNI"
            );
        }


        actualizarEstiloTipoCliente();
    }


    private void actualizarEstiloTipoCliente() {

        String activo =
                "-fx-background-color: #A87425;"
                + "-fx-background-radius: 7;"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 9px;"
                + "-fx-font-weight: bold;";


        String normal =
                "-fx-background-color: #F2F2F2;"
                + "-fx-background-radius: 7;"
                + "-fx-text-fill: #222222;"
                + "-fx-font-size: 9px;";


        if (btnPersonaNatural.isSelected()) {

            btnPersonaNatural.setStyle(activo);
            btnEmpresa.setStyle(normal);

        } else {

            btnPersonaNatural.setStyle(normal);
            btnEmpresa.setStyle(activo);
        }
    }


    // =========================================================
    // BUSCAR CLIENTE
    // =========================================================

    @FXML
    private void buscarCliente() {

        String documento =
                txtDocumento
                        .getText()
                        .trim();


        if (documento.isEmpty()) {

            mostrarAdvertencia(
                    "Documento requerido",
                    "Ingrese un DNI o RUC."
            );

            txtDocumento.requestFocus();

            return;
        }


        if (btnPersonaNatural.isSelected()) {

            if (!documento.matches("\\d{8}")) {

                mostrarAdvertencia(
                        "DNI inválido",
                        "El DNI debe contener exactamente 8 números."
                );

                txtDocumento.requestFocus();

                return;
            }

        } else {

            if (!documento.matches("\\d{11}")) {

                mostrarAdvertencia(
                        "RUC inválido",
                        "El RUC debe contener exactamente 11 números."
                );

                txtDocumento.requestFocus();

                return;
            }
        }


        /*
         * =====================================================
         * AQUÍ SE CONECTARÁ MYSQL
         * =====================================================
         *
         * Ejemplo futuro:
         *
         * Cliente cliente =
         *     clienteService.buscarPorDocumento(documento);
         *
         * if (cliente != null) {
         *     cargarCliente(cliente);
         * }
         *
         */


        mostrarInformacion(
                "Base de datos pendiente",
                "El documento es válido. "
                + "La búsqueda del cliente funcionará cuando conectes MySQL."
        );
    }


    // =========================================================
    // CARGAR CLIENTE
    // =========================================================

    /*
     * Este método se utilizará posteriormente
     * cuando exista conexión con la base de datos.
     */
    private void cargarCliente(
            String nombres,
            String apellidos,
            String nacimiento,
            String celular,
            String correo,
            String direccion
    ) {

        clienteSeleccionado = true;


        lblNombres.setText(
                valorSeguro(nombres)
        );

        lblApellidos.setText(
                valorSeguro(apellidos)
        );

        lblNacimiento.setText(
                valorSeguro(nacimiento)
        );

        lblCelular.setText(
                valorSeguro(celular)
        );

        lblCorreo.setText(
                valorSeguro(correo)
        );

        lblDireccion.setText(
                valorSeguro(direccion)
        );


        panelSinCliente.setVisible(false);
        panelSinCliente.setManaged(false);

        panelClienteEncontrado.setManaged(true);
        panelClienteEncontrado.setVisible(true);


        txtDocumentoComprobante.setText(
                txtDocumento.getText().trim()
        );


        String nombreCompleto =
                (valorSeguro(nombres)
                        + " "
                        + valorSeguro(apellidos))
                        .trim();


        txtNombreComprobante.setText(
                nombreCompleto
        );
    }


    @FXML
    private void editarCliente() {

        if (!clienteSeleccionado) {
            return;
        }

        /*
         * Posteriormente puedes abrir
         * un formulario de edición.
         */
    }


    private void limpiarCliente() {

        clienteSeleccionado = false;


        if (panelClienteEncontrado != null) {

            panelClienteEncontrado.setVisible(false);
            panelClienteEncontrado.setManaged(false);
        }


        if (panelSinCliente != null) {

            panelSinCliente.setManaged(true);
            panelSinCliente.setVisible(true);
        }


        if (lblNombres != null) {
            lblNombres.setText("--");
        }

        if (lblApellidos != null) {
            lblApellidos.setText("--");
        }

        if (lblNacimiento != null) {
            lblNacimiento.setText("--");
        }

        if (lblCelular != null) {
            lblCelular.setText("--");
        }

        if (lblCorreo != null) {
            lblCorreo.setText("--");
        }

        if (lblDireccion != null) {
            lblDireccion.setText("--");
        }


        if (txtDocumentoComprobante != null) {
            txtDocumentoComprobante.clear();
        }

        if (txtNombreComprobante != null) {
            txtNombreComprobante.clear();
        }
    }


    // =========================================================
    // FECHAS
    // =========================================================

    private void configurarFechas() {

        LocalDate hoy =
                LocalDate.now();


        dpFechaIngreso.setValue(
                hoy
        );


        dpFechaSalida.setValue(
                hoy.plusDays(1)
        );


        dpFechaIngreso
                .valueProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            calcularNoches();

                            cargarHabitaciones();
                        }
                );


        dpFechaSalida
                .valueProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            calcularNoches();

                            cargarHabitaciones();
                        }
                );


        calcularNoches();
    }


    @FXML
    private void actualizarReserva() {

        calcularNoches();

        cargarHabitaciones();

        actualizarResumen();
    }


    private void calcularNoches() {

        LocalDate ingreso =
                dpFechaIngreso.getValue();

        LocalDate salida =
                dpFechaSalida.getValue();


        if (ingreso == null ||
            salida == null) {

            lblNoches.setText("0");

            actualizarResumen();

            return;
        }


        long noches =
                ChronoUnit.DAYS.between(
                        ingreso,
                        salida
                );


        if (noches < 0) {
            noches = 0;
        }


        lblNoches.setText(
                String.valueOf(noches)
        );


        actualizarResumen();
    }


    // =========================================================
    // HORAS
    // =========================================================

    private void configurarHoras() {

        cbHoraIngreso
                .getItems()
                .addAll(
                        "06:00",
                        "07:00",
                        "08:00",
                        "09:00",
                        "10:00",
                        "11:00",
                        "12:00",
                        "13:00",
                        "14:00",
                        "15:00",
                        "16:00",
                        "17:00",
                        "18:00",
                        "19:00",
                        "20:00",
                        "21:00",
                        "22:00"
                );


        cbHoraSalida
                .getItems()
                .addAll(
                        "06:00",
                        "07:00",
                        "08:00",
                        "09:00",
                        "10:00",
                        "11:00",
                        "12:00",
                        "13:00",
                        "14:00",
                        "15:00",
                        "16:00",
                        "17:00",
                        "18:00",
                        "19:00",
                        "20:00"
                );


        cbHoraIngreso.setValue(
                "14:00"
        );


        cbHoraSalida.setValue(
                "12:00"
        );
    }


    // =========================================================
    // HUÉSPEDES
    // =========================================================

    private void configurarHuespedes() {

        SpinnerValueFactory
                .IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory
                        .IntegerSpinnerValueFactory(
                                1,
                                20,
                                1
                        );


        spHuespedes.setValueFactory(
                factory
        );
    }


    // =========================================================
    // OBSERVACIONES
    // =========================================================

    private void configurarObservaciones() {

        txtObservaciones
                .textProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            if (actual.length() > 300) {

                                txtObservaciones.setText(
                                        anterior
                                );

                                return;
                            }


                            lblContadorObservaciones.setText(
                                    actual.length()
                                    + " / 300"
                            );
                        }
                );
    }


    // =========================================================
    // FILTROS DE HABITACIÓN
    // =========================================================

    private void configurarFiltrosHabitacion() {

        cbPiso
                .getItems()
                .clear();

        cbPiso
                .getItems()
                .add(
                        "Todos los pisos"
                );

        cbPiso.setValue(
                "Todos los pisos"
        );


        cbTipoHabitacion
                .getItems()
                .clear();

        cbTipoHabitacion
                .getItems()
                .add(
                        "Todos los tipos"
                );

        cbTipoHabitacion.setValue(
                "Todos los tipos"
        );


        cbPiso
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            cargarHabitaciones();
                        }
                );


        cbTipoHabitacion
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            cargarHabitaciones();
                        }
                );
    }


    // =========================================================
    // CARGAR HABITACIONES
    // =========================================================

    private void cargarHabitaciones() {

        /*
         * =====================================================
         * AQUÍ SE CONSULTARÁ MYSQL
         * =====================================================
         *
         * Ejemplo futuro:
         *
         * List<Habitacion> habitaciones =
         *         habitacionService.buscarDisponibles(
         *             dpFechaIngreso.getValue(),
         *             dpFechaSalida.getValue(),
         *             cbPiso.getValue(),
         *             cbTipoHabitacion.getValue()
         *         );
         *
         * dibujarHabitaciones(habitaciones);
         *
         */


        limpiarHabitaciones();
    }


    private void limpiarHabitaciones() {

        habitacionSeleccionada = null;

        precioNoche = 0.00;


        contenedorHabitaciones
                .getChildren()
                .clear();


        lblSinHabitaciones.setText(
                "No hay habitaciones cargadas."
        );


        contenedorHabitaciones
                .getChildren()
                .add(
                        lblSinHabitaciones
                );


        actualizarResumen();
    }


    // =========================================================
    // MÉTODOS DE PAGO
    // =========================================================

    private void configurarMetodosPago() {

        btnEfectivo.setToggleGroup(
                grupoMetodoPago
        );

        btnTarjeta.setToggleGroup(
                grupoMetodoPago
        );

        btnTransferencia.setToggleGroup(
                grupoMetodoPago
        );

        btnYape.setToggleGroup(
                grupoMetodoPago
        );


        btnEfectivo.setSelected(true);


        grupoMetodoPago
                .selectedToggleProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            if (actual == null) {

                                if (anterior != null) {
                                    anterior.setSelected(true);
                                }

                                return;
                            }

                            actualizarEstiloPago();

                            actualizarEstadoMonto();
                        }
                );


        actualizarEstiloPago();

        actualizarEstadoMonto();
    }


    private void actualizarEstiloPago() {

        String normal =
                "-fx-background-color: #F4F4F4;"
                + "-fx-text-fill: #333333;"
                + "-fx-font-size: 7px;"
                + "-fx-background-radius: 5;";


        String activo =
                "-fx-background-color: #A87425;"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 7px;"
                + "-fx-font-weight: bold;"
                + "-fx-background-radius: 5;";


        btnEfectivo.setStyle(
                btnEfectivo.isSelected()
                        ? activo
                        : normal
        );


        btnTarjeta.setStyle(
                btnTarjeta.isSelected()
                        ? activo
                        : normal
        );


        btnTransferencia.setStyle(
                btnTransferencia.isSelected()
                        ? activo
                        : normal
        );


        btnYape.setStyle(
                btnYape.isSelected()
                        ? activo
                        : normal
        );
    }


    private void actualizarEstadoMonto() {

        boolean efectivo =
                btnEfectivo.isSelected();


        txtMontoRecibido.setDisable(
                !efectivo
        );


        if (!efectivo) {

            txtMontoRecibido.clear();

            txtVuelto.setText(
                    "S/ 0.00"
            );
        }
    }


    // =========================================================
    // COMPROBANTE
    // =========================================================

    private void configurarComprobante() {

        rbBoleta.setToggleGroup(
                grupoComprobante
        );

        rbFactura.setToggleGroup(
                grupoComprobante
        );


        rbBoleta.setSelected(true);


        grupoComprobante
                .selectedToggleProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            if (actual == null) {

                                if (anterior != null) {
                                    anterior.setSelected(true);
                                }
                            }
                        }
                );
    }


    // =========================================================
    // MONTO RECIBIDO
    // =========================================================

    private void configurarMontoRecibido() {

        txtVuelto.setEditable(false);


        txtMontoRecibido
                .textProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            calcularVuelto();
                        }
                );
    }


    private void calcularVuelto() {

        if (!btnEfectivo.isSelected()) {

            txtVuelto.setText(
                    "S/ 0.00"
            );

            return;
        }


        String valor =
                txtMontoRecibido
                        .getText()
                        .replace("S/", "")
                        .replace(",", ".")
                        .trim();


        if (valor.isEmpty()) {

            txtVuelto.setText(
                    "S/ 0.00"
            );

            return;
        }


        try {

            double recibido =
                    Double.parseDouble(valor);


            double vuelto =
                    recibido - totalReserva;


            if (vuelto < 0) {
                vuelto = 0;
            }


            txtVuelto.setText(
                    String.format(
                            "S/ %.2f",
                            vuelto
                    )
            );


        } catch (NumberFormatException e) {

            txtVuelto.setText(
                    "S/ 0.00"
            );
        }
    }


    // =========================================================
    // RESUMEN
    // =========================================================

    private void actualizarResumen() {

        long noches = 0;


        try {

            noches =
                    Long.parseLong(
                            lblNoches.getText()
                    );

        } catch (Exception e) {

            noches = 0;
        }


        lblNochesResumen.setText(
                String.valueOf(noches)
        );


        if (habitacionSeleccionada == null) {

            lblHabitacionResumen.setText(
                    "--"
            );

        } else {

            lblHabitacionResumen.setText(
                    habitacionSeleccionada
            );
        }


        lblPrecioNoche.setText(
                String.format(
                        "S/ %.2f",
                        precioNoche
                )
        );


        totalReserva =
                precioNoche * noches;


        lblTotal.setText(
                String.format(
                        "S/ %.2f",
                        totalReserva
                )
        );


        calcularVuelto();
    }


    // =========================================================
    // GUARDAR COMO PENDIENTE
    // =========================================================

    @FXML
    private void guardarPendiente() {

        if (!validarFormularioBasico()) {
            return;
        }


        /*
         * FUTURO:
         *
         * reservaService.guardarPendiente(...);
         */


        mostrarInformacion(
                "Reserva pendiente",
                "La función está preparada para guardar "
                + "la reserva en MySQL."
        );
    }


    // =========================================================
    // CONFIRMAR RESERVA
    // =========================================================

    @FXML
    private void confirmarReserva() {

        if (!validarFormularioBasico()) {
            return;
        }


        if (habitacionSeleccionada == null) {

            mostrarAdvertencia(
                    "Habitación requerida",
                    "Seleccione una habitación disponible."
            );

            return;
        }


        if (totalReserva <= 0) {

            mostrarAdvertencia(
                    "Total inválido",
                    "No existe un total válido para la reserva."
            );

            return;
        }


        if (btnEfectivo.isSelected()) {

            if (!validarMontoEfectivo()) {
                return;
            }
        }


        /*
         * =====================================================
         * FUTURO
         * =====================================================
         *
         * Reserva reserva = ...
         *
         * reservaService.registrar(reserva);
         *
         * pagoService.registrar(...);
         *
         * comprobanteService.generar(...);
         *
         */


        mostrarInformacion(
                "Confirmar reserva",
                "La operación está preparada para "
                + "registrarse en la base de datos."
        );
    }


    // =========================================================
    // CANCELAR FORMULARIO
    // =========================================================

    @FXML
    private void cancelarFormulario() {

        limpiarFormulario();
    }


    private void limpiarFormulario() {

        btnPersonaNatural.setSelected(true);

        txtDocumento.clear();

        limpiarCliente();


        LocalDate hoy =
                LocalDate.now();


        dpFechaIngreso.setValue(
                hoy
        );


        dpFechaSalida.setValue(
                hoy.plusDays(1)
        );


        cbHoraIngreso.setValue(
                "14:00"
        );


        cbHoraSalida.setValue(
                "12:00"
        );


        spHuespedes
                .getValueFactory()
                .setValue(1);


        txtObservaciones.clear();


        cbPiso.setValue(
                "Todos los pisos"
        );


        cbTipoHabitacion.setValue(
                "Todos los tipos"
        );


        limpiarHabitaciones();


        btnEfectivo.setSelected(true);

        rbBoleta.setSelected(true);


        txtMontoRecibido.clear();

        txtVuelto.setText(
                "S/ 0.00"
        );


        actualizarTipoCliente();

        calcularNoches();

        actualizarResumen();
    }


    // =========================================================
    // VALIDACIONES
    // =========================================================

    private boolean validarFormularioBasico() {

        if (!clienteSeleccionado) {

            mostrarAdvertencia(
                    "Cliente requerido",
                    "Debe buscar y seleccionar un cliente."
            );

            return false;
        }


        if (dpFechaIngreso.getValue() == null) {

            mostrarAdvertencia(
                    "Fecha requerida",
                    "Seleccione la fecha de ingreso."
            );

            return false;
        }


        if (dpFechaSalida.getValue() == null) {

            mostrarAdvertencia(
                    "Fecha requerida",
                    "Seleccione la fecha de salida."
            );

            return false;
        }


        long noches =
                ChronoUnit.DAYS.between(
                        dpFechaIngreso.getValue(),
                        dpFechaSalida.getValue()
                );


        if (noches <= 0) {

            mostrarAdvertencia(
                    "Fechas inválidas",
                    "La fecha de salida debe ser posterior "
                    + "a la fecha de ingreso."
            );

            return false;
        }


        return true;
    }


    private boolean validarMontoEfectivo() {

        String valor =
                txtMontoRecibido
                        .getText()
                        .replace("S/", "")
                        .replace(",", ".")
                        .trim();


        if (valor.isEmpty()) {

            mostrarAdvertencia(
                    "Monto requerido",
                    "Ingrese el monto recibido."
            );

            return false;
        }


        try {

            double recibido =
                    Double.parseDouble(valor);


            if (recibido < totalReserva) {

                mostrarAdvertencia(
                        "Monto insuficiente",
                        "El monto recibido es menor "
                        + "al total de la reserva."
                );

                return false;
            }


        } catch (NumberFormatException e) {

            mostrarAdvertencia(
                    "Monto inválido",
                    "Ingrese un monto numérico válido."
            );

            return false;
        }


        return true;
    }


    // =========================================================
    // UTILIDADES
    // =========================================================

    private String valorSeguro(String valor) {

        if (valor == null ||
            valor.isBlank()) {

            return "--";
        }

        return valor;
    }


    private void mostrarAdvertencia(
            String titulo,
            String mensaje
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.WARNING
                );

        alert.setTitle(titulo);

        alert.setHeaderText(null);

        alert.setContentText(mensaje);

        alert.showAndWait();
    }


    private void mostrarInformacion(
            String titulo,
            String mensaje
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(titulo);

        alert.setHeaderText(null);

        alert.setContentText(mensaje);

        alert.showAndWait();
    }
}
