package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
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
import untrm.hotel_san_antonio.util.CampoValidacion;
import untrm.hotel_san_antonio.util.Validador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ventana "Registrar huesped y reserva": se abre al hacer clic en una habitacion
 * disponible. Este formulario tambien lo puede reutilizar el modulo Reservas.
 */
public class ReservaFormController {

    private static final Map<String, String> TIPOS_DOC = new LinkedHashMap<>();
    private static final Map<String, String> CANALES = new LinkedHashMap<>();
    private static final Map<String, String> METODOS = new LinkedHashMap<>();
    static {
        TIPOS_DOC.put("DNI", "DNI");
        TIPOS_DOC.put("Pasaporte", "PASAPORTE");
        CANALES.put("Presencial", "PRESENCIAL");
        CANALES.put("Teléfono", "TELEFONO");
        CANALES.put("WhatsApp", "WHATSAPP");
        CANALES.put("Booking", "BOOKING");
        METODOS.put("Efectivo", "EFECTIVO");
        METODOS.put("Yape", "YAPE");
        METODOS.put("Transferencia", "TRANSFERENCIA");
        METODOS.put("Tarjeta", "TARJETA");
    }

    private static final BigDecimal MITAD = new BigDecimal("0.50");

    @FXML private Label lblTitulo, lblEstadoHab;
    @FXML private ComboBox<String> cmbTipoDoc, cmbCanal, cmbMetodo;
    @FXML private TextField txtDocumento, txtNombres, txtApellidos, txtPais, txtTelefono, txtEmail, txtAdelanto;
    @FXML private Button btnBuscarDoc, btnBuscarRuc, btnCancelar, btnGuardar;
    @FXML private DatePicker dpIngreso, dpSalida;
    @FXML private Label lblNoches, lblTituloPago, lblMontoPago;
    @FXML private Label lblMetodo1, lblDivision;
    @FXML private ComboBox<String> cmbMetodo2;
    @FXML private TextField txtMonto2;
    @FXML private GridPane boxSegundoPago;
    @FXML private CheckBox chkEmpresa, chkDividirPago;
    @FXML private VBox boxEmpresa;
    @FXML private TextField txtRuc, txtRazonSocial, txtDireccionFiscal;
    @FXML private Label lblHabNumero, lblHabTipo, lblHabCapacidad, lblHabTarifa, lblHabIncluye;
    @FXML private Label lblResCalculo, lblResTotal, lblResMinimo, lblResSaldo;
    @FXML private ScrollPane scrollForm;

    private final HuespedDAO huespedDAO = new HuespedDAO();
    private final ReservaService reservaService = new ReservaService();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();

    private Habitacion habitacion;
    private Runnable alGuardar;
    private boolean adelantoManual = false;

    @FXML
    public void initialize() {
        untrm.hotel_san_antonio.util.EstiloBoton.aplicarHoverPrimario(btnGuardar);
        cmbTipoDoc.getItems().addAll(TIPOS_DOC.keySet());
        cmbTipoDoc.setValue("DNI");
        cmbCanal.getItems().addAll(CANALES.keySet());
        cmbCanal.setValue("Presencial");
        cmbMetodo.getItems().addAll(METODOS.keySet());
        cmbMetodo.setValue("Efectivo");
        cmbMetodo2.getItems().addAll(METODOS.keySet());
        cmbMetodo2.setValue("Yape");
        txtPais.setText("Perú");

        dpIngreso.setEditable(false);
        dpSalida.setEditable(false);
        dpIngreso.setValue(LocalDate.now());
        dpSalida.setValue(LocalDate.now().plusDays(1));
        dpIngreso.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate fecha, boolean vacio) {
                super.updateItem(fecha, vacio);
                setDisable(vacio || fecha.isBefore(LocalDate.now()));
            }
        });
        dpSalida.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate fecha, boolean vacio) {
                super.updateItem(fecha, vacio);
                LocalDate ingreso = dpIngreso.getValue();
                setDisable(vacio || (ingreso != null && !fecha.isAfter(ingreso)));
            }
        });
        dpIngreso.valueProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null && (dpSalida.getValue() == null || !dpSalida.getValue().isAfter(ahora))) {
                dpSalida.setValue(ahora.plusDays(1));
            }
            recalcular();
        });
        dpSalida.valueProperty().addListener((obs, antes, ahora) -> recalcular());

        // Los campos solo aceptan lo que es valido para cada formato
        txtDocumento.setTextFormatter(new TextFormatter<String>(c -> {
            String texto = c.getControlNewText();
            boolean esDni = "DNI".equals(cmbTipoDoc.getValue());
            return (esDni ? texto.matches("\\d{0,8}") : texto.matches("[A-Za-z0-9]{0,20}")) ? c : null;
        }));
        cmbTipoDoc.valueProperty().addListener((obs, antes, ahora) -> {
            txtDocumento.clear();
            btnBuscarDoc.setDisable(!"DNI".equals(ahora));
        });
        txtTelefono.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,9}") ? c : null));
        txtRuc.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,11}") ? c : null));
        txtAdelanto.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,6}(\\.\\d{0,2})?") ? c : null));

        txtAdelanto.setOnKeyTyped(e -> adelantoManual = true);
        txtAdelanto.textProperty().addListener((obs, antes, ahora) -> actualizarResumen());

        txtMonto2.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,6}(\\.\\d{0,2})?") ? c : null));
        boxSegundoPago.visibleProperty().bind(chkDividirPago.selectedProperty());
        boxSegundoPago.managedProperty().bind(boxSegundoPago.visibleProperty());
        txtMonto2.textProperty().addListener((obs, antes, ahora) -> actualizarResumen());
        chkDividirPago.selectedProperty().addListener((obs, antes, ahora) -> actualizarResumen());
        cmbMetodo.valueProperty().addListener((obs, antes, ahora) -> actualizarResumen());
        cmbMetodo2.valueProperty().addListener((obs, antes, ahora) -> actualizarResumen());

        limitarLargo(txtNombres, 80);
        limitarLargo(txtApellidos, 80);
        limitarLargo(txtPais, 60);
        limitarLargo(txtEmail, 100);
        limitarLargo(txtRazonSocial, 150);
        limitarLargo(txtDireccionFiscal, 200);
        CampoValidacion.limpiarAlEditar(camposValidables());

        boxEmpresa.visibleProperty().bind(chkEmpresa.selectedProperty());
        boxEmpresa.managedProperty().bind(boxEmpresa.visibleProperty());
    }

    /** El campo no acepta mas caracteres de los que caben en la columna de la base de datos. */
    private void limitarLargo(TextField campo, int maximo) {
        campo.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= maximo ? c : null));
    }

    /** La llama quien abre la ventana, para indicar la habitacion y que hacer al guardar. */
    public void iniciar(Habitacion h, Runnable alGuardar) {
        this.habitacion = h;
        this.alGuardar = alGuardar;

        int capacidad = h.getTipo().getCapacidad();
        lblTitulo.setText("Habitación " + h.getNumero() + " · " + h.getTipo().getNombre() + " · Piso " + h.getPiso());
        lblEstadoHab.setText("● Disponible");
        lblHabNumero.setText(h.getNumero());
        lblHabTipo.setText(h.getTipo().getNombre() + " · Piso " + h.getPiso());
        lblHabCapacidad.setText("Capacidad: " + capacidad + (capacidad == 1 ? " persona" : " personas"));
        lblHabTarifa.setText(String.format(Locale.US, "Tarifa: S/ %.2f por noche", h.getTipo().getPrecioBase()));
        String incluye = "Baño privado, Wi-Fi, televisor, armario, mesa y silla, velador, ventilador, frigobar y cochera";
        if ("Ejecutiva".equalsIgnoreCase(h.getTipo().getNombre())) {
            incluye += ", agua caliente";
        }
        lblHabIncluye.setText(incluye + ".");
        recalcular();
    }

    // ---------------------------------------------------------------- calculos

    private long noches() {
        LocalDate ingreso = dpIngreso.getValue();
        LocalDate salida = dpSalida.getValue();
        if (ingreso == null || salida == null || !salida.isAfter(ingreso)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(ingreso, salida);
    }

    private BigDecimal total() {
        if (habitacion == null) {
            return BigDecimal.ZERO;
        }
        return habitacion.getTipo().getPrecioBase().multiply(BigDecimal.valueOf(noches()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Si el ingreso es hoy es un check-in directo: se paga todo. Si no, es una reserva con adelanto. */
    private boolean esCheckin() {
        return LocalDate.now().equals(dpIngreso.getValue());
    }

    /** Monto minimo a pagar ahora: el total en un check-in, el 50 % en una reserva. */
    private BigDecimal minimoAdelanto() {
        return esCheckin() ? total() : total().multiply(MITAD).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal montoAdelanto() {
        String texto = txtAdelanto.getText().trim();
        return Validador.esMontoValido(texto) ? new BigDecimal(texto) : null;
    }

    private void recalcular() {
        long n = noches();
        lblNoches.setText(n > 0 ? String.valueOf(n) : "—");
        boolean checkin = esCheckin();
        if (checkin) {
            adelantoManual = false; // al volver a una fecha futura se recalcula el 50 %
        }
        if (checkin || !adelantoManual) {
            txtAdelanto.setText(n > 0 ? minimoAdelanto().toPlainString() : "");
        }
        txtAdelanto.setDisable(checkin); // en un check-in el pago es completo y no se edita
        lblTituloPago.setText(checkin ? "Pago" : "Adelanto");
        lblMontoPago.setText(checkin ? "Monto a pagar (S/)" : "Monto del adelanto (S/)");
        btnGuardar.setText(checkin ? "Registrar check-in" : "Guardar reserva");
        actualizarResumen();
    }

    private void actualizarResumen() {
        if (habitacion == null) {
            return;
        }
        long n = noches();
        BigDecimal total = total();
        lblResCalculo.setText(n > 0
                ? n + (n == 1 ? " noche" : " noches") + String.format(Locale.US, " × S/ %.2f", habitacion.getTipo().getPrecioBase())
                : "Elige las fechas de la estadía");
        lblResTotal.setText(String.format(Locale.US, "S/ %.2f", total));
        lblResMinimo.setText(esCheckin()
                ? "Pago completo al ingresar"
                : String.format(Locale.US, "Adelanto mínimo (50 %%): S/ %.2f", minimoAdelanto()));
        BigDecimal adelanto = montoAdelanto();
        lblResSaldo.setText(String.format(Locale.US, "Saldo pendiente: S/ %.2f",
                adelanto == null ? total : total.subtract(adelanto).max(BigDecimal.ZERO)));
        actualizarDivision(adelanto);
    }

    private BigDecimal montoSegundoMetodo() {
        String texto = txtMonto2.getText().trim();
        return Validador.esMontoValido(texto) ? new BigDecimal(texto) : null;
    }

    /** Explica como se reparte el pago cuando se usan dos metodos. */
    private void actualizarDivision(BigDecimal aPagar) {
        boolean dividir = chkDividirPago.isSelected();
        lblMetodo1.setText(dividir ? "Primer método" : "Método de pago");
        if (!dividir) {
            return;
        }
        BigDecimal segundo = montoSegundoMetodo();
        if (aPagar == null || segundo == null || segundo.compareTo(aPagar) >= 0) {
            lblDivision.setText("Indica cuánto se paga con el segundo método.");
            return;
        }
        lblDivision.setText(String.format(Locale.US, "%s: S/ %.2f  ·  %s: S/ %.2f",
                cmbMetodo.getValue(), aPagar.subtract(segundo), cmbMetodo2.getValue(), segundo));
    }

    // ------------------------------------------------------------- consultas API

    @FXML
    private void onBuscarDocumento() {
        String dni = txtDocumento.getText().trim();
        if (!Validador.esDniValido(dni)) {
            CampoValidacion.marcar(txtDocumento, "El DNI debe tener 8 dígitos.");
            return;
        }

        try {
            Huesped local = huespedDAO.buscarPorDocumento("DNI", dni);
            if (local != null) {
                txtNombres.setText(local.getNombres());
                txtApellidos.setText(local.getApellidos());
                txtPais.setText(local.getPaisProcedencia());
                txtTelefono.setText(local.getTelefono() == null ? "" : local.getTelefono());
                txtEmail.setText(local.getEmail() == null ? "" : local.getEmail());
                return;
            }
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar al huésped.\n\n" + e.getMessage());
            return;
        }

        Task<Huesped> tarea = ReniecService.consultarDni(dni);
        btnBuscarDoc.setDisable(true);
        tarea.setOnSucceeded(e -> {
            btnBuscarDoc.setDisable(false);
            Huesped h = tarea.getValue();
            if (h == null) {
                CampoValidacion.marcar(txtDocumento, "No se encontraron datos para ese DNI. Puedes completarlos manualmente.");
            } else {
                txtNombres.setText(h.getNombres());
                txtApellidos.setText(h.getApellidos());
            }
        });
        tarea.setOnFailed(e -> {
            btnBuscarDoc.setDisable(false);
            Alertas.mostrarInfo("RENIEC", "No se pudo consultar RENIEC (" + causa(tarea.getException()) + ").\nPuedes ingresar los datos manualmente.");
        });
        iniciarTarea(tarea);
    }

    @FXML
    private void onBuscarRuc() {
        String ruc = txtRuc.getText().trim();
        if (!Validador.esRucValido(ruc)) {
            CampoValidacion.marcar(txtRuc, "El RUC debe tener 11 dígitos.");
            return;
        }

        Task<Empresa> tarea = SunatRucService.consultarRuc(ruc);
        btnBuscarRuc.setDisable(true);
        tarea.setOnSucceeded(e -> {
            btnBuscarRuc.setDisable(false);
            Empresa emp = tarea.getValue();
            if (emp == null) {
                CampoValidacion.marcar(txtRuc, "No se encontraron datos para ese RUC. Puedes completarlos manualmente.");
            } else {
                txtRazonSocial.setText(emp.getRazonSocial());
                txtDireccionFiscal.setText(emp.getDireccion());
            }
        });
        tarea.setOnFailed(e -> {
            btnBuscarRuc.setDisable(false);
            Alertas.mostrarInfo("SUNAT", "No se pudo consultar la SUNAT (" + causa(tarea.getException()) + ").\nPuedes ingresar los datos manualmente.");
        });
        iniciarTarea(tarea);
    }

    private void iniciarTarea(Task<?> tarea) {
        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    private String causa(Throwable t) {
        return t == null || t.getMessage() == null ? "sin conexión" : t.getMessage();
    }

    // ------------------------------------------------------------------ guardar

    @FXML
    private void onGuardar() {
        if (!validar()) {
            return;
        }

        LocalDate ingreso = dpIngreso.getValue();
        BigDecimal total = total();
        BigDecimal adelanto = montoAdelanto();

        Huesped huesped = new Huesped(
                TIPOS_DOC.get(cmbTipoDoc.getValue()),
                txtDocumento.getText().trim(),
                txtNombres.getText().trim(),
                txtApellidos.getText().trim(),
                txtPais.getText().trim(),
                vacioANull(txtTelefono.getText()),
                vacioANull(txtEmail.getText()));

        Empresa empresa = null;
        if (chkEmpresa.isSelected()) {
            empresa = new Empresa();
            empresa.setRuc(txtRuc.getText().trim());
            empresa.setRazonSocial(txtRazonSocial.getText().trim());
            empresa.setDireccion(vacioANull(txtDireccionFiscal.getText()));
        }

        Reserva reserva = new Reserva();
        reserva.setIdHabitacion(habitacion.getIdHabitacion());
        reserva.setFechaCheckin(ingreso);
        reserva.setFechaCheckout(dpSalida.getValue());
        reserva.setAdelanto(adelanto);
        reserva.setMontoTotal(total);
        reserva.setCanal(CANALES.get(cmbCanal.getValue()));

        String tipoPago = adelanto.compareTo(total) >= 0 ? "COMPLETO" : "ADELANTO";
        java.util.List<Pago> pagos = new java.util.ArrayList<>();
        BigDecimal segundo = chkDividirPago.isSelected() ? montoSegundoMetodo() : null;
        pagos.add(nuevoPago(segundo == null ? adelanto : adelanto.subtract(segundo), cmbMetodo.getValue(), tipoPago));
        if (segundo != null) {
            pagos.add(nuevoPago(segundo, cmbMetodo2.getValue(), tipoPago));
        }

        boolean checkin = ingreso.equals(LocalDate.now());
        try {
            reservaService.registrar(huesped, empresa, reserva, pagos, checkin);
        } catch (ConflictoFechasException e) {
            // la habitacion ya esta reservada en esas fechas: se marcan las dos fechas
            CampoValidacion.marcar(dpIngreso, e.getMessage());
            CampoValidacion.marcar(dpSalida, e.getMessage());
            mostrarCampo(dpIngreso);
            return;
        } catch (IllegalStateException | IllegalArgumentException e) {
            Alertas.mostrarError("No se pudo guardar", e.getMessage());
            if (alGuardar != null) {
                alGuardar.run();
            }
            return;
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar la reserva.\n\n" + e.getMessage());
            return;
        }

        Alertas.mostrarInfo(checkin ? "Check-in registrado" : "Reserva guardada",
                checkin ? "El huésped quedó registrado en la habitación " + habitacion.getNumero() + "."
                        : "La reserva de la habitación " + habitacion.getNumero() + " quedó confirmada.");
        if (alGuardar != null) {
            alGuardar.run();
        }
        cerrar();
    }

    // ------------------------------------------------------------ validacion

    private Control primerInvalido;

    private Control[] camposValidables() {
        return new Control[]{txtDocumento, txtNombres, txtApellidos, txtPais, txtTelefono, txtEmail,
                dpIngreso, dpSalida, txtAdelanto, txtMonto2, cmbMetodo2, txtRuc, txtRazonSocial, txtDireccionFiscal};
    }

    /** Marca el campo en rojo (el motivo se ve al pasar el mouse) y recuerda el primero para llevar la vista ahi. */
    private void invalido(Control campo, String motivo) {
        CampoValidacion.marcar(campo, motivo);
        if (primerInvalido == null) {
            primerInvalido = campo;
        }
    }

    /** Marca en rojo cada campo con un dato incorrecto. Devuelve true si todo esta bien. */
    private boolean validar() {
        CampoValidacion.limpiar(camposValidables());
        primerInvalido = null;

        String tipo = TIPOS_DOC.get(cmbTipoDoc.getValue());
        String numero = txtDocumento.getText().trim();
        if ("DNI".equals(tipo) && !Validador.esDniValido(numero)) {
            invalido(txtDocumento, "El DNI debe tener 8 dígitos.");
        }
        if ("PASAPORTE".equals(tipo) && !Validador.esPasaporteValido(numero)) {
            invalido(txtDocumento, "El pasaporte debe tener entre 6 y 20 letras o números.");
        }
        if (!Validador.esNombreValido(txtNombres.getText())) {
            invalido(txtNombres, "Ingresa los nombres del huésped (solo letras).");
        }
        if (!Validador.esNombreValido(txtApellidos.getText())) {
            invalido(txtApellidos, "Ingresa los apellidos del huésped (solo letras).");
        }
        if (!Validador.esPaisValido(txtPais.getText())) {
            invalido(txtPais, "Ingresa el país de procedencia (solo letras).");
        }
        String telefono = txtTelefono.getText().trim();
        if (!telefono.isEmpty() && !Validador.esTelefonoValido(telefono)) {
            invalido(txtTelefono, "El teléfono debe tener 9 dígitos y empezar con 9.");
        }
        String email = txtEmail.getText().trim();
        if (!email.isEmpty() && !Validador.esEmailValido(email)) {
            invalido(txtEmail, "El correo electrónico no tiene un formato válido.");
        }
        if (dpIngreso.getValue() == null || dpIngreso.getValue().isBefore(LocalDate.now())) {
            invalido(dpIngreso, "La fecha de ingreso no puede ser anterior a hoy.");
        }
        if (dpIngreso.getValue() != null && dpIngreso.getValue().isAfter(LocalDate.now().plusDays(365))) {
            invalido(dpIngreso, "Solo se puede reservar con hasta 365 días de anticipación.");
        }
        if (noches() == 0) {
            invalido(dpSalida, "La fecha de salida debe ser posterior a la de ingreso.");
        } else if (noches() > ReservaService.MAX_NOCHES) {
            invalido(dpSalida, "La estadía no puede superar " + ReservaService.MAX_NOCHES + " noches.");
        } else {
            validarPago();
        }
        if (chkEmpresa.isSelected()) {
            if (!Validador.esRucValido(txtRuc.getText().trim())) {
                invalido(txtRuc, "El RUC debe tener 11 dígitos.");
            }
            if (!Validador.esTextoObligatorioValido(txtRazonSocial.getText())) {
                invalido(txtRazonSocial, "Ingresa la razón social de la empresa.");
            }
            if (txtDireccionFiscal.getText().trim().length() > 200) {
                invalido(txtDireccionFiscal, "La dirección fiscal no puede superar 200 caracteres.");
            }
        }

        if (primerInvalido != null) {
            mostrarCampo(primerInvalido);
            primerInvalido.requestFocus();
        }
        return primerInvalido == null;
    }

    private void validarPago() {
        BigDecimal adelanto = montoAdelanto();
        if (adelanto == null) {
            invalido(txtAdelanto, "Ingresa un monto válido (por ejemplo 150.00).");
        } else if (adelanto.compareTo(minimoAdelanto()) < 0) {
            invalido(txtAdelanto, esCheckin()
                    ? String.format(Locale.US, "El check-in requiere el pago completo (S/ %.2f).", minimoAdelanto())
                    : String.format(Locale.US, "El adelanto debe ser al menos el 50 %% del total (S/ %.2f).", minimoAdelanto()));
        } else if (adelanto.compareTo(total()) > 0) {
            invalido(txtAdelanto, "El adelanto no puede superar el total de la estadía.");
        } else if (chkDividirPago.isSelected()) {
            BigDecimal segundo = montoSegundoMetodo();
            if (segundo == null || segundo.signum() <= 0) {
                invalido(txtMonto2, "Indica cuánto se paga con el segundo método.");
            } else if (segundo.compareTo(adelanto) >= 0) {
                invalido(txtMonto2, "El monto del segundo método debe ser menor al total a pagar.");
            }
        }
        if (chkDividirPago.isSelected() && cmbMetodo.getValue().equals(cmbMetodo2.getValue())) {
            invalido(cmbMetodo2, "Elige dos métodos de pago distintos.");
        }
    }

    /** Desplaza el formulario para que el campo indicado quede a la vista. */
    private void mostrarCampo(Node campo) {
        Node contenido = scrollForm.getContent();
        double yCampo = campo.localToScene(campo.getBoundsInLocal()).getMinY();
        double yContenido = contenido.localToScene(contenido.getBoundsInLocal()).getMinY();
        double exceso = contenido.getBoundsInLocal().getHeight() - scrollForm.getViewportBounds().getHeight();
        if (exceso > 0) {
            scrollForm.setVvalue(Math.max(0, Math.min(1, (yCampo - yContenido - 24) / exceso)));
        }
    }

    @FXML
    private void onCancelar() {
        cerrar();
    }

    /** Saca la habitacion de circulacion (no se le pueden registrar huespedes). Se revierte desde la grilla. */
    @FXML
    private void onMantenimiento() {
        try {
            String motivo = Alertas.pedirTexto("Mantenimiento",
                    "¿Cuál es el motivo? (ej. \"Aire acondicionado dañado\", \"Fuga de agua\")",
                    "Motivo del mantenimiento");
            if (motivo == null) {
                return; // canceló o no escribió nada
            }

            java.time.LocalDate proxima = reservaService.proximaReserva(habitacion.getIdHabitacion());
            String aviso = proxima == null ? "" : "\n\nOjo: la habitación tiene una reserva desde el "
                    + proxima.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
            if (!Alertas.confirmar("Mantenimiento", "¿Poner la habitación " + habitacion.getNumero()
                    + " en mantenimiento por \"" + motivo + "\"? No se podrán registrar huéspedes hasta que termine." + aviso)) {
                return;
            }
            if (!habitacionDAO.cambiarEstadoSiEs(habitacion.getIdHabitacion(), "DISPONIBLE", "MANTENIMIENTO", motivo)) {
                Alertas.mostrarInfo("Mantenimiento", "La habitación ya no está disponible (cambió mientras tenías esta ventana abierta).");
                if (alGuardar != null) {
                    alGuardar.run();
                }
                cerrar();
                return;
            }
            if (alGuardar != null) {
                alGuardar.run();
            }
            cerrar();
        } catch (SQLException e) {
            Alertas.mostrarError("Error", "No se pudo cambiar el estado.\n\n" + e.getMessage());
        }
    }

    private Pago nuevoPago(BigDecimal monto, String metodo, String tipoPago) {
        Pago pago = new Pago();
        pago.setMonto(monto);
        pago.setMetodoPago(METODOS.get(metodo));
        pago.setTipoPago(tipoPago);
        return pago;
    }

    private void cerrar() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }

    private String vacioANull(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
