/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador.Reserva;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import untrm.hotel_san_antonio.dao.EmpresaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.HuespedDAO;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ConflictoFechasException;
import untrm.hotel_san_antonio.servicio.ReniecService;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.servicio.SunatRucService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Validador;
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
    private Button btnBuscarCliente;

    @FXML
    private VBox filaDocumentoHuesped;

    @FXML
    private TextField txtDocumentoHuesped;

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

    private Habitacion habitacionElegida = null;

    private double precioNoche = 0.00;

    private double totalReserva = 0.00;

    private Huesped huespedEncontrado = null;

    private Empresa empresaEncontrada = null;

    private final HuespedDAO huespedDAO = new HuespedDAO();

    private final EmpresaDAO empresaDAO = new EmpresaDAO();

    private final HabitacionDAO habitacionDAO = new HabitacionDAO();

    private final ReservaService reservaService = new ReservaService();


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

        cargarHabitaciones();

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

        // Si se factura a una empresa, igual se hospeda una persona: se pide su DNI aparte.
        filaDocumentoHuesped.setVisible(btnEmpresa.isSelected());
        filaDocumentoHuesped.setManaged(btnEmpresa.isSelected());
        txtDocumentoHuesped.clear();


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

            Alertas.mostrarAdvertencia(
                    "Documento requerido",
                    "Ingrese un DNI o RUC."
            );

            txtDocumento.requestFocus();

            return;
        }


        if (btnPersonaNatural.isSelected()) {

            if (!documento.matches("\\d{8}")) {

                Alertas.mostrarAdvertencia(
                        "DNI inválido",
                        "El DNI debe contener exactamente 8 números."
                );

                txtDocumento.requestFocus();

                return;
            }

        } else {

            if (!documento.matches("\\d{11}")) {

                Alertas.mostrarAdvertencia(
                        "RUC inválido",
                        "El RUC debe contener exactamente 11 números."
                );

                txtDocumento.requestFocus();

                return;
            }
        }


        if (btnEmpresa.isSelected()) {
            buscarEmpresaYHuesped(documento);
        } else {
            buscarPersonaNatural(documento);
        }
    }


    /** Busca al huesped (persona natural) primero en la BD y, si no esta, en RENIEC. */
    private void buscarPersonaNatural(String dni) {

        if (!Validador.esDniValido(dni)) {
            Alertas.mostrarAdvertencia("DNI inválido", "Ingrese un DNI válido.");
            return;
        }

        try {
            Huesped local = huespedDAO.buscarPorDocumento("DNI", dni);
            if (local != null) {
                empresaEncontrada = null;
                cargarClienteDesdeHuesped(local);
                return;
            }
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar al cliente.\n\n" + e.getMessage());
            return;
        }

        btnBuscarCliente.setDisable(true);
        Task<Huesped> tarea = ReniecService.consultarDni(dni);
        tarea.setOnSucceeded(e -> {
            btnBuscarCliente.setDisable(false);
            Huesped h = tarea.getValue();
            if (h == null) {
                Alertas.mostrarAdvertencia("Cliente no encontrado",
                        "No se encontraron datos para ese DNI en RENIEC.");
                return;
            }
            h.setPaisProcedencia("Perú");
            empresaEncontrada = null;
            cargarClienteDesdeHuesped(h);
        });
        tarea.setOnFailed(e -> {
            btnBuscarCliente.setDisable(false);
            Alertas.mostrarInfo("RENIEC", "No se pudo consultar RENIEC (" + causaError(tarea.getException()) + ").");
        });
        iniciarTarea(tarea);
    }


    /**
     * Modo "Empresa": el RUC solo sirve para la facturación; quien se hospeda sigue siendo una
     * persona real, por eso tambien se pide y se busca su DNI (txtDocumentoHuesped).
     */
    private void buscarEmpresaYHuesped(String ruc) {

        if (!Validador.esRucValido(ruc)) {
            Alertas.mostrarAdvertencia("RUC inválido", "Ingrese un RUC válido.");
            return;
        }

        String dniHuesped = txtDocumentoHuesped.getText().trim();
        if (!Validador.esDniValido(dniHuesped)) {
            Alertas.mostrarAdvertencia("DNI del huésped requerido",
                    "Ingrese el DNI de la persona que se va a hospedar.");
            return;
        }

        Empresa empresaLocal;
        try (java.sql.Connection con = ConexionBD.conectar()) {
            empresaLocal = empresaDAO.buscarPorRuc(con, ruc);
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar la empresa.\n\n" + e.getMessage());
            return;
        }

        if (empresaLocal != null) {
            continuarConEmpresa(empresaLocal, dniHuesped);
            return;
        }

        btnBuscarCliente.setDisable(true);
        Task<Empresa> tarea = SunatRucService.consultarRuc(ruc);
        tarea.setOnSucceeded(e -> {
            btnBuscarCliente.setDisable(false);
            Empresa empresa = tarea.getValue();
            if (empresa == null) {
                Alertas.mostrarAdvertencia("Empresa no encontrada", "No se encontraron datos para ese RUC en la SUNAT.");
                return;
            }
            continuarConEmpresa(empresa, dniHuesped);
        });
        tarea.setOnFailed(e -> {
            btnBuscarCliente.setDisable(false);
            Alertas.mostrarInfo("SUNAT", "No se pudo consultar la SUNAT (" + causaError(tarea.getException()) + ").");
        });
        iniciarTarea(tarea);
    }


    /** Con la empresa ya resuelta, busca (BD y luego RENIEC) al huesped que se va a hospedar. */
    private void continuarConEmpresa(Empresa empresa, String dniHuesped) {

        try {
            Huesped local = huespedDAO.buscarPorDocumento("DNI", dniHuesped);
            if (local != null) {
                empresaEncontrada = empresa;
                cargarClienteDesdeHuesped(local);
                return;
            }
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar al huésped.\n\n" + e.getMessage());
            return;
        }

        btnBuscarCliente.setDisable(true);
        Task<Huesped> tarea = ReniecService.consultarDni(dniHuesped);
        tarea.setOnSucceeded(e -> {
            btnBuscarCliente.setDisable(false);
            Huesped h = tarea.getValue();
            if (h == null) {
                Alertas.mostrarAdvertencia("Huésped no encontrado",
                        "No se encontraron datos para el DNI del huésped en RENIEC.");
                return;
            }
            h.setPaisProcedencia("Perú");
            empresaEncontrada = empresa;
            cargarClienteDesdeHuesped(h);
        });
        tarea.setOnFailed(e -> {
            btnBuscarCliente.setDisable(false);
            Alertas.mostrarInfo("RENIEC", "No se pudo consultar RENIEC (" + causaError(tarea.getException()) + ").");
        });
        iniciarTarea(tarea);
    }


    private void iniciarTarea(Task<?> tarea) {
        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }


    private String causaError(Throwable t) {
        return t == null || t.getMessage() == null ? "sin conexión" : t.getMessage();
    }


    private void cargarClienteDesdeHuesped(Huesped h) {

        huespedEncontrado = h;

        cargarCliente(
                h.getNombres(),
                h.getApellidos(),
                "--",
                h.getTelefono(),
                h.getEmail(),
                empresaEncontrada != null
                        ? "Se factura a " + empresaEncontrada.getRazonSocial() + " (RUC " + empresaEncontrada.getRuc() + ")"
                        : "--"
        );

        rbFactura.setSelected(empresaEncontrada != null);
        rbBoleta.setSelected(empresaEncontrada == null);
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


        if (empresaEncontrada != null) {

            txtDocumentoComprobante.setText(empresaEncontrada.getRuc());

            txtNombreComprobante.setText(empresaEncontrada.getRazonSocial());

        } else {

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

        huespedEncontrado = null;

        empresaEncontrada = null;


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

        cbTipoHabitacion
                .getItems()
                .clear();

        cbTipoHabitacion
                .getItems()
                .add(
                        "Todos los tipos"
                );

        try (java.sql.Connection con = ConexionBD.conectar()) {
            for (Integer piso : habitacionDAO.listarPisos(con)) {
                cbPiso.getItems().add("Piso " + piso);
            }
            cbTipoHabitacion.getItems().addAll(habitacionDAO.listarNombresTipo(con));
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar los pisos y tipos.\n\n" + e.getMessage());
        }

        cbPiso.setValue(
                "Todos los pisos"
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

        LocalDate ingreso = dpFechaIngreso.getValue();
        LocalDate salida = dpFechaSalida.getValue();

        if (ingreso == null || salida == null || !salida.isAfter(ingreso)) {
            limpiarHabitaciones();
            return;
        }

        String piso = cbPiso.getValue();
        Integer numeroPiso = (piso != null && piso.startsWith("Piso "))
                ? Integer.valueOf(piso.substring("Piso ".length())) : null;
        String tipo = cbTipoHabitacion.getValue();
        String nombreTipo = (tipo == null || "Todos los tipos".equals(tipo)) ? null : tipo;

        List<Habitacion> disponibles;
        try {
            disponibles = reservaService.buscarDisponibles(ingreso, salida, numeroPiso, nombreTipo);
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar las habitaciones.\n\n" + e.getMessage());
            limpiarHabitaciones();
            return;
        }

        habitacionElegida = null;
        precioNoche = 0.00;
        contenedorHabitaciones.getChildren().clear();

        if (disponibles.isEmpty()) {
            lblSinHabitaciones.setText("No hay habitaciones disponibles para esas fechas y filtros.");
            contenedorHabitaciones.getChildren().add(lblSinHabitaciones);
        } else {
            for (Habitacion h : disponibles) {
                contenedorHabitaciones.getChildren().add(crearTarjetaHabitacion(h));
            }
        }

        actualizarResumen();
    }


    /** Fila compacta y clickeable de una habitacion disponible, para elegirla. */
    private HBox crearTarjetaHabitacion(Habitacion h) {

        HBox tarjeta = new HBox(8.0);
        tarjeta.setStyle(estiloTarjetaHabitacion(false));

        Region punto = new Region();
        punto.setPrefSize(9.0, 9.0);
        punto.setMaxSize(9.0, 9.0);
        punto.setStyle("-fx-background-color: #1C9748; -fx-background-radius: 5;");

        VBox datos = new VBox(1.0);
        Label lblNumeroTipo = new Label(h.getNumero() + " · " + h.getTipo().getNombre());
        lblNumeroTipo.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #222222;");
        Label lblPrecio = new Label(String.format(Locale.US, "S/ %.2f por noche · Piso %d", h.getTipo().getPrecioBase(), h.getPiso()));
        lblPrecio.setStyle("-fx-font-size: 8px; -fx-text-fill: #777777;");
        datos.getChildren().addAll(lblNumeroTipo, lblPrecio);

        HBox.setHgrow(datos, javafx.scene.layout.Priority.ALWAYS);
        tarjeta.getChildren().addAll(punto, datos);
        tarjeta.setOnMouseClicked(e -> elegirHabitacion(h, tarjeta));

        return tarjeta;
    }

    private String estiloTarjetaHabitacion(boolean elegida) {
        return "-fx-padding: 8px 10px; -fx-background-radius: 6; -fx-cursor: hand; -fx-alignment: CENTER_LEFT; "
                + (elegida
                    ? "-fx-background-color: #FBF1E1; -fx-border-color: #A87425; -fx-border-radius: 6; -fx-border-width: 1.4px;"
                    : "-fx-background-color: white; -fx-border-color: #E7E7E7; -fx-border-radius: 6; -fx-border-width: 1px;");
    }

    private void elegirHabitacion(Habitacion h, HBox tarjetaElegida) {

        habitacionElegida = h;
        precioNoche = h.getTipo().getPrecioBase().doubleValue();

        for (javafx.scene.Node nodo : contenedorHabitaciones.getChildren()) {
            if (nodo instanceof HBox fila) {
                fila.setStyle(estiloTarjetaHabitacion(fila == tarjetaElegida));
            }
        }

        actualizarResumen();
    }


    private void limpiarHabitaciones() {

        habitacionElegida = null;

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


        if (habitacionElegida == null) {

            lblHabitacionResumen.setText(
                    "--"
            );

        } else {

            lblHabitacionResumen.setText(
                    habitacionElegida.getNumero() + " · " + habitacionElegida.getTipo().getNombre()
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

        if (habitacionElegida == null) {

            Alertas.mostrarAdvertencia(
                    "Habitación requerida",
                    "Seleccione una habitación disponible."
            );

            return;
        }

        if (dpFechaIngreso.getValue().equals(LocalDate.now())) {

            Alertas.mostrarAdvertencia(
                    "Ingreso hoy",
                    "Para un ingreso hoy, use \"Confirmar reserva\" con el pago; "
                    + "\"Guardar pendiente\" es solo para fechas futuras."
            );

            return;
        }

        Huesped huesped = construirHuesped();
        if (huesped == null) {
            return;
        }

        try {
            int idReserva = reservaService.registrarPendiente(huesped, empresaEncontrada, construirReserva());

            Alertas.mostrarInfo(
                    "Reserva pendiente guardada",
                    "La reserva " + String.format("R-%04d", idReserva) + " quedó pendiente de confirmación."
            );

            limpiarFormulario();

        } catch (ConflictoFechasException e) {
            Alertas.mostrarAdvertencia("Fechas no disponibles", e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            Alertas.mostrarAdvertencia("No se pudo guardar", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar la reserva.\n\n" + e.getMessage());
        }
    }


    // =========================================================
    // CONFIRMAR RESERVA
    // =========================================================

    @FXML
    private void confirmarReserva() {

        if (!validarFormularioBasico()) {
            return;
        }


        if (habitacionElegida == null) {

            Alertas.mostrarAdvertencia(
                    "Habitación requerida",
                    "Seleccione una habitación disponible."
            );

            return;
        }


        if (totalReserva <= 0) {

            Alertas.mostrarAdvertencia(
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

        Huesped huesped = construirHuesped();
        if (huesped == null) {
            return;
        }

        boolean checkinInmediato = dpFechaIngreso.getValue().equals(LocalDate.now());

        Pago pago = new Pago();
        pago.setMonto(java.math.BigDecimal.valueOf(totalReserva).setScale(2, RoundingMode.HALF_UP));
        pago.setMetodoPago(metodoPagoSeleccionado());
        pago.setTipoPago("COMPLETO");

        try {
            int idReserva = reservaService.registrar(huesped, empresaEncontrada, construirReserva(),
                    List.of(pago), checkinInmediato);

            Alertas.mostrarInfo(
                    "Confirmar reserva",
                    checkinInmediato
                            ? "El huésped quedó registrado en la habitación " + habitacionElegida.getNumero() + "."
                            : "La reserva " + String.format("R-%04d", idReserva) + " quedó confirmada."
            );

            limpiarFormulario();

        } catch (ConflictoFechasException e) {
            Alertas.mostrarAdvertencia("Fechas no disponibles", e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            Alertas.mostrarAdvertencia("No se pudo confirmar", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo registrar la reserva.\n\n" + e.getMessage());
        }
    }


    private Huesped construirHuesped() {
        if (huespedEncontrado == null) {
            Alertas.mostrarAdvertencia("Cliente requerido", "Debe buscar y seleccionar un cliente.");
            return null;
        }
        return huespedEncontrado;
    }


    private Reserva construirReserva() {
        Reserva r = new Reserva();
        r.setIdHabitacion(habitacionElegida.getIdHabitacion());
        r.setFechaCheckin(dpFechaIngreso.getValue());
        r.setFechaCheckout(dpFechaSalida.getValue());
        r.setMontoTotal(java.math.BigDecimal.valueOf(totalReserva).setScale(2, RoundingMode.HALF_UP));
        r.setCanal("PRESENCIAL");
        r.setNumHuespedes(spHuespedes.getValue() == null ? 1 : spHuespedes.getValue());
        if (cbHoraIngreso.getValue() != null) {
            r.setHoraCheckin(java.time.LocalTime.parse(cbHoraIngreso.getValue()));
        }
        return r;
    }


    private String metodoPagoSeleccionado() {
        if (btnTarjeta.isSelected()) {
            return "TARJETA";
        }
        if (btnTransferencia.isSelected()) {
            return "TRANSFERENCIA";
        }
        if (btnYape.isSelected()) {
            return "YAPE";
        }
        return "EFECTIVO";
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

            Alertas.mostrarAdvertencia(
                    "Cliente requerido",
                    "Debe buscar y seleccionar un cliente."
            );

            return false;
        }


        if (dpFechaIngreso.getValue() == null) {

            Alertas.mostrarAdvertencia(
                    "Fecha requerida",
                    "Seleccione la fecha de ingreso."
            );

            return false;
        }


        if (dpFechaSalida.getValue() == null) {

            Alertas.mostrarAdvertencia(
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

            Alertas.mostrarAdvertencia(
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

            Alertas.mostrarAdvertencia(
                    "Monto requerido",
                    "Ingrese el monto recibido."
            );

            return false;
        }


        try {

            double recibido =
                    Double.parseDouble(valor);


            if (recibido < totalReserva) {

                Alertas.mostrarAdvertencia(
                        "Monto insuficiente",
                        "El monto recibido es menor "
                        + "al total de la reserva."
                );

                return false;
            }


        } catch (NumberFormatException e) {

            Alertas.mostrarAdvertencia(
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


}
