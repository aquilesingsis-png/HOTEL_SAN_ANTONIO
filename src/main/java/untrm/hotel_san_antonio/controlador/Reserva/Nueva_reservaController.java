package untrm.hotel_san_antonio.controlador.Reserva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
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
import untrm.hotel_san_antonio.util.Validador;

public class Nueva_reservaController {

    @FXML private ToggleButton btnPersonaNatural;
    @FXML private ToggleButton btnEmpresa;
    private final ToggleGroup grupoTipoCliente = new ToggleGroup();
    @FXML private Label lblTipoDocumento;
    @FXML private TextField txtDocumento;
    @FXML private VBox filaDocumentoHuesped;
    @FXML private TextField txtDocumentoHuesped;
    @FXML private Button btnBuscarCliente;
    @FXML private Label lblEstadoBusqueda;
    @FXML private VBox panelDatosHuesped;
    @FXML private TextField txtNombres;
    @FXML private TextField txtApellidos;
    @FXML private TextField txtPais;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtCorreo;
    @FXML private VBox panelDatosEmpresa;
    @FXML private TextField txtRazonSocial;
    @FXML private TextField txtDireccionFiscal;

    @FXML private DatePicker dpFechaIngreso;
    @FXML private DatePicker dpFechaSalida;
    @FXML private ChoiceBox<String> cbHoraIngreso;
    @FXML private Spinner<Integer> spHuespedes;
    @FXML private VBox contenedorAcompanantes;
    @FXML private Label lblNoches;
    @FXML private ChoiceBox<String> cbPiso;
    @FXML private ChoiceBox<String> cbTipoHabitacion;
    @FXML private VBox contenedorHabitaciones;
    @FXML private Label lblSinHabitaciones;

    @FXML private Label lblHabitacionResumen;
    @FXML private Label lblNochesResumen;
    @FXML private Label lblPrecioNoche;
    @FXML private Label lblTotal;
    @FXML private ChoiceBox<String> cbMetodoPago;
    @FXML private TextField txtMontoPago;
    @FXML private Label lblAyudaPago;

    private final ReservaService reservaService = new ReservaService();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private HuespedesAdicionales acompanantes;
    private Habitacion habitacionSeleccionada;
    private Node tarjetaSeleccionada;
    private boolean datosClienteListos;
    private String documentoHuespedResuelto;
    private String rucResuelto;
    private boolean inicializando;
    private int secuenciaConsulta;

    @FXML
    public void initialize() {
        inicializando = true;
        configurarTipoCliente();
        configurarFechasYHoras();
        configurarHuespedes();
        configurarFiltros();
        configurarPago();
        inicializando = false;
        limpiarCliente("Ingrese el documento y pulse Buscar.");
        actualizarFechasYHabitaciones();
    }

    private void configurarTipoCliente() {
        // Vincular los botones aquí mantiene el FXML editable en SceneBuilder.
        btnPersonaNatural.setToggleGroup(grupoTipoCliente);
        btnEmpresa.setToggleGroup(grupoTipoCliente);
        btnPersonaNatural.setSelected(true);
        grupoTipoCliente.selectedToggleProperty().addListener((observable, anterior, actual) -> {
            if (actual == null) {
                if (anterior != null) {
                    anterior.setSelected(true);
                }
                return;
            }
            actualizarTipoCliente();
        });
        actualizarTipoCliente();
    }

    private void actualizarTipoCliente() {
        boolean empresa = btnEmpresa.isSelected();
        lblTipoDocumento.setText(empresa ? "RUC de la empresa *" : "DNI del huésped *");
        txtDocumento.setPromptText(empresa ? "11 dígitos" : "8 dígitos");
        filaDocumentoHuesped.setVisible(empresa);
        filaDocumentoHuesped.setManaged(empresa);
        panelDatosEmpresa.setVisible(empresa && datosClienteListos);
        panelDatosEmpresa.setManaged(empresa && datosClienteListos);
        secuenciaConsulta++;
        limpiarCliente("Busque primero en la base local; las consultas externas son opcionales.");
    }

    private void configurarFechasYHoras() {
        LocalDate hoy = LocalDate.now();
        dpFechaIngreso.setValue(hoy);
        dpFechaSalida.setValue(hoy.plusDays(1));
        cbHoraIngreso.getItems().setAll("06:00", "07:00", "08:00", "09:00", "10:00", "11:00",
                "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00",
                "20:00", "21:00", "22:00");
        cbHoraIngreso.setValue("14:00");
        dpFechaIngreso.valueProperty().addListener((observable, anterior, actual) -> actualizarFechasYHabitaciones());
        dpFechaSalida.valueProperty().addListener((observable, anterior, actual) -> actualizarFechasYHabitaciones());
    }

    private void configurarHuespedes() {
        spHuespedes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
        acompanantes = new HuespedesAdicionales(contenedorAcompanantes);
        spHuespedes.valueProperty().addListener((obs, antes, ahora) -> {
            acompanantes.actualizar(ahora);
            if (habitacionSeleccionada != null && ahora > habitacionSeleccionada.getTipo().getCapacidad()) {
                limpiarSeleccionHabitacion();
            }
            if (!inicializando) cargarHabitaciones();
        });
        acompanantes.actualizar(1);
    }

    private void configurarFiltros() {
        cbPiso.getItems().setAll("Todos los pisos");
        cbTipoHabitacion.getItems().setAll("Todos los tipos");
        try {
            for (Integer piso : reservaService.listarPisos()) {
                cbPiso.getItems().add("Piso " + piso);
            }
            cbTipoHabitacion.getItems().addAll(reservaService.listarTiposHabitacion());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar los filtros.\n\n" + error.getMessage());
        }
        cbPiso.setValue("Todos los pisos");
        cbTipoHabitacion.setValue("Todos los tipos");
        cbPiso.valueProperty().addListener((observable, anterior, actual) -> cargarHabitaciones());
        cbTipoHabitacion.valueProperty().addListener((observable, anterior, actual) -> cargarHabitaciones());
    }

    private void configurarPago() {
        cbMetodoPago.getItems().setAll("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE");
        cbMetodoPago.setValue("EFECTIVO");
    }

    @FXML
    private void buscarCliente() {
        int consultaActual = ++secuenciaConsulta;
        limpiarCliente("Buscando primero en la base de datos local...");
        String documento = txtDocumento.getText().trim();
        if (btnEmpresa.isSelected()) {
            String dniHuesped = txtDocumentoHuesped.getText().trim();
            if (!Validador.esRucValido(documento)) {
                mostrarErrorDocumento("El RUC debe contener 11 dígitos válidos.");
                return;
            }
            if (!Validador.esDniValido(dniHuesped)) {
                mostrarErrorDocumento("Ingrese el DNI válido de la persona que se hospedará.");
                return;
            }
            buscarEmpresa(documento, dniHuesped, consultaActual);
        } else {
            if (!Validador.esDniValido(documento)) {
                mostrarErrorDocumento("El DNI debe contener 8 dígitos válidos.");
                return;
            }
            buscarHuesped(documento, null, consultaActual);
        }
    }

    private void buscarEmpresa(String ruc, String dniHuesped, int consultaActual) {
        try {
            Empresa empresaLocal = reservaService.buscarEmpresaLocal(ruc);
            if (empresaLocal != null) {
                cargarEmpresa(empresaLocal, true);
                buscarHuesped(dniHuesped, empresaLocal, consultaActual);
                return;
            }
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar la empresa.\n\n" + error.getMessage());
            return;
        }

        bloquearBusqueda("Empresa no encontrada localmente. Consultando SUNAT...");
        Task<Empresa> tarea = SunatRucService.consultarRuc(ruc);
        tarea.setOnSucceeded(evento -> {
            if (consultaActual != secuenciaConsulta) {
                return;
            }
            Empresa empresa = tarea.getValue();
            if (empresa == null) {
                habilitarEmpresaManual(ruc, "SUNAT no devolvió datos. Complete la empresa manualmente.");
                buscarHuesped(dniHuesped, construirEmpresaDesdeCampos(), consultaActual);
            } else {
                cargarEmpresa(empresa, false);
                buscarHuesped(dniHuesped, empresa, consultaActual);
            }
        });
        tarea.setOnFailed(evento -> {
            if (consultaActual != secuenciaConsulta) {
                return;
            }
            habilitarEmpresaManual(ruc, "SUNAT no está disponible. Complete la empresa manualmente.");
            buscarHuesped(dniHuesped, construirEmpresaDesdeCampos(), consultaActual);
        });
        iniciarTarea(tarea);
    }

    private void buscarHuesped(String dni, Empresa empresa, int consultaActual) {
        try {
            Huesped huespedLocal = reservaService.buscarHuespedLocal("DNI", dni);
            if (huespedLocal != null) {
                cargarHuesped(huespedLocal, true);
                completarBusqueda(empresa, dni, consultaActual, "Huésped encontrado en la base local.");
                verificarIdentidadExistente(dni, consultaActual);
                return;
            }
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar al huésped.\n\n" + error.getMessage());
            desbloquearBusqueda();
            return;
        }

        bloquearBusqueda("Huésped no encontrado localmente. Consultando RENIEC...");
        Task<Huesped> tarea = ReniecService.consultarDni(dni);
        tarea.setOnSucceeded(evento -> {
            if (consultaActual != secuenciaConsulta) {
                return;
            }
            Huesped huesped = tarea.getValue();
            if (huesped == null) {
                habilitarHuespedManual(dni, "RENIEC no devolvió datos. Ingrese el huésped manualmente.");
            } else {
                huesped.setPaisProcedencia("Perú");
                cargarHuesped(huesped, false);
                lblEstadoBusqueda.setText("RENIEC autocompletó los datos; complete teléfono y correo si corresponde.");
            }
            completarBusqueda(empresa, dni, consultaActual, lblEstadoBusqueda.getText());
        });
        tarea.setOnFailed(evento -> {
            if (consultaActual != secuenciaConsulta) {
                return;
            }
            habilitarHuespedManual(dni, "Sin conexión con RENIEC. Puede continuar con ingreso manual.");
            completarBusqueda(empresa, dni, consultaActual, lblEstadoBusqueda.getText());
        });
        iniciarTarea(tarea);
    }

    private void verificarIdentidadExistente(String dni, int consultaActual) {
        Task<Huesped> consulta = ReniecService.consultarDni(dni);
        consulta.setOnSucceeded(evento -> {
            if (consultaActual != secuenciaConsulta || consulta.getValue() == null) return;
            Task<Huesped> actualizar = new Task<>() {
                @Override protected Huesped call() throws Exception {
                    return reservaService.reconciliarIdentidadDni(dni, consulta.getValue());
                }
            };
            actualizar.setOnSucceeded(ev -> {
                if (consultaActual != secuenciaConsulta || actualizar.getValue() == null) return;
                Huesped h = actualizar.getValue();
                txtNombres.setText(h.getNombres());
                txtApellidos.setText(h.getApellidos());
                lblEstadoBusqueda.setText("Identidad actualizada desde RENIEC; se conservaron los datos de contacto.");
            });
            actualizar.setOnFailed(ev -> lblEstadoBusqueda.setText(
                    "Se cargaron datos locales; no se pudo actualizar la identidad."));
            iniciarTarea(actualizar);
        });
        consulta.setOnFailed(evento -> {
            if (consultaActual == secuenciaConsulta) {
                lblEstadoBusqueda.setText("Huésped local cargado; RENIEC no está disponible.");
            }
        });
        iniciarTarea(consulta);
    }

    private void completarBusqueda(Empresa empresa, String dni, int consultaActual, String mensaje) {
        if (consultaActual != secuenciaConsulta) {
            return;
        }
        if (btnEmpresa.isSelected() && empresa != null && txtRazonSocial.getText().isBlank()) {
            cargarEmpresa(empresa, false);
        }
        datosClienteListos = true;
        documentoHuespedResuelto = dni;
        rucResuelto = btnEmpresa.isSelected() ? txtDocumento.getText().trim() : null;
        panelDatosHuesped.setVisible(true);
        panelDatosHuesped.setManaged(true);
        panelDatosEmpresa.setVisible(btnEmpresa.isSelected());
        panelDatosEmpresa.setManaged(btnEmpresa.isSelected());
        lblEstadoBusqueda.setText(mensaje);
        desbloquearBusqueda();
    }

    private void cargarHuesped(Huesped huesped, boolean existente) {
        txtNombres.setText(valorSeguro(huesped.getNombres()));
        txtApellidos.setText(valorSeguro(huesped.getApellidos()));
        txtPais.setText(valorSeguro(huesped.getPaisProcedencia()).isBlank() ? "Perú" : huesped.getPaisProcedencia());
        txtTelefono.setText(valorSeguro(huesped.getTelefono()));
        txtCorreo.setText(valorSeguro(huesped.getEmail()));
        hacerEditablesDatosHuesped(!existente);
    }

    private void habilitarHuespedManual(String dni, String mensaje) {
        txtNombres.clear();
        txtApellidos.clear();
        txtPais.setText("Perú");
        txtTelefono.clear();
        txtCorreo.clear();
        hacerEditablesDatosHuesped(true);
        lblEstadoBusqueda.setText(mensaje + " DNI: " + dni);
    }

    private void hacerEditablesDatosHuesped(boolean editable) {
        txtNombres.setDisable(!editable);
        txtApellidos.setDisable(!editable);
        txtPais.setDisable(!editable);
        txtTelefono.setDisable(!editable);
        txtCorreo.setDisable(!editable);
    }

    private void cargarEmpresa(Empresa empresa, boolean existente) {
        txtRazonSocial.setText(valorSeguro(empresa.getRazonSocial()));
        txtDireccionFiscal.setText(valorSeguro(empresa.getDireccion()));
        txtRazonSocial.setDisable(existente);
        txtDireccionFiscal.setDisable(existente);
    }

    private void habilitarEmpresaManual(String ruc, String mensaje) {
        panelDatosEmpresa.setVisible(true);
        panelDatosEmpresa.setManaged(true);
        txtRazonSocial.clear();
        txtDireccionFiscal.clear();
        txtRazonSocial.setDisable(false);
        txtDireccionFiscal.setDisable(false);
        lblEstadoBusqueda.setText(mensaje + " RUC: " + ruc);
    }

    private void iniciarTarea(Task<?> tarea) {
        Thread hiloConsulta = new Thread(tarea, "consulta-api-reservas");
        hiloConsulta.setDaemon(true);
        hiloConsulta.start();
    }

    private void bloquearBusqueda(String mensaje) {
        btnBuscarCliente.setDisable(true);
        lblEstadoBusqueda.setText(mensaje);
    }

    private void desbloquearBusqueda() {
        btnBuscarCliente.setDisable(false);
    }

    private void mostrarErrorDocumento(String mensaje) {
        desbloquearBusqueda();
        lblEstadoBusqueda.setText(mensaje);
        Alertas.mostrarAdvertencia("Documento inválido", mensaje);
    }

    private void actualizarFechasYHabitaciones() {
        calcularNochesYTotal();
        if (!inicializando) {
            cargarHabitaciones();
        }
    }

    private void cargarHabitaciones() {
        limpiarSeleccionHabitacion();
        LocalDate ingreso = dpFechaIngreso.getValue();
        LocalDate salida = dpFechaSalida.getValue();
        if (ingreso == null || salida == null || !salida.isAfter(ingreso)) {
            mostrarMensajeHabitaciones("Seleccione un rango de fechas válido.");
            return;
        }
        Integer piso = obtenerPisoSeleccionado();
        String tipo = "Todos los tipos".equals(cbTipoHabitacion.getValue()) ? null : cbTipoHabitacion.getValue();
        try {
            List<Habitacion> disponibles = reservaService.buscarDisponibles(ingreso, salida, piso, tipo);
            Set<Integer> idsDisponibles = new HashSet<>();
            for (Habitacion habitacion : disponibles) idsDisponibles.add(habitacion.getIdHabitacion());
            Map<Integer, List<Habitacion>> porPiso = new TreeMap<>();
            for (Habitacion habitacion : habitacionDAO.listar()) {
                if (piso != null && habitacion.getPiso() != piso) continue;
                if (tipo != null && !tipo.equals(habitacion.getTipo().getNombre())) continue;
                porPiso.computeIfAbsent(habitacion.getPiso(), numero -> new ArrayList<>()).add(habitacion);
            }
            contenedorHabitaciones.getChildren().clear();
            if (porPiso.isEmpty()) {
                mostrarMensajeHabitaciones("No hay habitaciones que coincidan con el piso y tipo seleccionados.");
                return;
            }
            if (disponibles.isEmpty()) {
                Label aviso = new Label("No hay habitaciones disponibles para esas fechas y filtros.");
                aviso.setStyle("-fx-text-fill: #8A462E; -fx-font-weight: bold;");
                contenedorHabitaciones.getChildren().add(aviso);
            }
            porPiso.forEach((numero, habitaciones) ->
                    contenedorHabitaciones.getChildren().add(crearPlanoPiso(numero, habitaciones, idsDisponibles)));
        } catch (IllegalArgumentException error) {
            mostrarMensajeHabitaciones(error.getMessage());
        } catch (SQLException error) {
            mostrarMensajeHabitaciones("No se pudieron cargar las habitaciones.");
            Alertas.mostrarError("Error de base de datos", error.getMessage());
        }
    }

    /** Dibuja cada tramo como dos alas de cuatro habitaciones separadas por un pasillo. */
    private VBox crearPlanoPiso(int numero, List<Habitacion> habitaciones, Set<Integer> idsDisponibles) {
        VBox piso = new VBox(9);
        piso.getStyleClass().add("plano-piso");
        Label titulo = new Label("Piso " + numero + " · " + habitaciones.size() + " habitaciones");
        titulo.getStyleClass().add("plano-titulo");
        piso.getChildren().add(titulo);
        for (int inicio = 0; inicio < habitaciones.size(); inicio += 8) {
            GridPane tramo = new GridPane();
            tramo.setHgap(8);
            tramo.setVgap(8);
            Label pasillo = new Label("PASILLO CENTRAL  ·  PISO " + numero);
            pasillo.getStyleClass().add("plano-pasillo");
            pasillo.setMaxWidth(Double.MAX_VALUE);
            pasillo.setAlignment(Pos.CENTER);
            tramo.add(pasillo, 0, 1, 4, 1);
            int fin = Math.min(inicio + 8, habitaciones.size());
            for (int indice = inicio; indice < fin; indice++) {
                Habitacion habitacion = habitaciones.get(indice);
                boolean disponible = idsDisponibles.contains(habitacion.getIdHabitacion());
                boolean capacidad = habitacion.getTipo().getCapacidad() >= spHuespedes.getValue();
                Button celda = new Button(habitacion.getNumero() + "\n" + habitacion.getTipo().getNombre()
                        + " · " + habitacion.getTipo().getCapacidad() + " pers.\n"
                        + (disponible && capacidad
                                ? moneda(habitacion.getTipo().getPrecioBase()) + " / noche"
                                : disponible ? "Capacidad insuficiente" : "No disponible"));
                celda.getStyleClass().addAll("plano-habitacion",
                        disponible && capacidad ? "plano-habitacion-disponible" : "plano-habitacion-ocupada");
                celda.setDisable(!disponible || !capacidad);
                celda.setOnAction(evento -> seleccionarHabitacion(habitacion, celda));
                celda.setAccessibleText("Habitación " + habitacion.getNumero() + ", "
                        + (disponible && capacidad ? "disponible" : "no disponible"));
                tramo.add(celda, (indice - inicio) % 4, indice - inicio < 4 ? 0 : 2);
            }
            piso.getChildren().add(tramo);
        }
        return piso;
    }

    private void seleccionarHabitacion(Habitacion habitacion, Node tarjeta) {
        if (spHuespedes.getValue() > habitacion.getTipo().getCapacidad()) {
            Alertas.mostrarAdvertencia("Capacidad insuficiente", "La habitación admite como máximo "
                    + habitacion.getTipo().getCapacidad() + " huésped(es).");
            return;
        }
        if (tarjetaSeleccionada != null) {
            tarjetaSeleccionada.getStyleClass().remove("plano-habitacion-seleccionada");
        }
        habitacionSeleccionada = habitacion;
        tarjetaSeleccionada = tarjeta;
        tarjeta.getStyleClass().add("plano-habitacion-seleccionada");
        calcularNochesYTotal();
    }

    private Integer obtenerPisoSeleccionado() {
        String piso = cbPiso.getValue();
        return piso != null && piso.startsWith("Piso ") ? Integer.valueOf(piso.substring(5)) : null;
    }

    private void mostrarMensajeHabitaciones(String mensaje) {
        contenedorHabitaciones.getChildren().setAll(lblSinHabitaciones);
        lblSinHabitaciones.setText(mensaje);
    }

    private void limpiarSeleccionHabitacion() {
        habitacionSeleccionada = null;
        tarjetaSeleccionada = null;
        calcularNochesYTotal();
    }

    private void calcularNochesYTotal() {
        LocalDate ingreso = dpFechaIngreso.getValue();
        LocalDate salida = dpFechaSalida.getValue();
        long noches = ingreso != null && salida != null && salida.isAfter(ingreso)
                ? ChronoUnit.DAYS.between(ingreso, salida) : 0;
        BigDecimal precio = habitacionSeleccionada == null
                ? BigDecimal.ZERO : habitacionSeleccionada.getTipo().getPrecioBase();
        BigDecimal total = precio.multiply(BigDecimal.valueOf(noches));
        lblNoches.setText(String.valueOf(noches));
        lblNochesResumen.setText(String.valueOf(noches));
        lblHabitacionResumen.setText(habitacionSeleccionada == null ? "--"
                : habitacionSeleccionada.getNumero() + " · " + habitacionSeleccionada.getTipo().getNombre());
        lblPrecioNoche.setText(moneda(precio));
        lblTotal.setText(moneda(total));
        actualizarPagoSugerido(total);
    }

    private void actualizarPagoSugerido(BigDecimal total) {
        boolean ingresoHoy = LocalDate.now().equals(dpFechaIngreso.getValue());
        BigDecimal sugerido = ingresoHoy ? total
                : total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        txtMontoPago.setText(sugerido.signum() == 0 ? "" : sugerido.toPlainString());
        lblAyudaPago.setText(ingresoHoy
                ? "Ingreso hoy: se requiere el pago completo."
                : "Reserva futura: se requiere al menos el 50 % para confirmar.");
    }

    @FXML
    private void guardarPendiente() {
        try {
            List<Huesped> huespedes = acompanantes.obtener(construirHuesped());
            Empresa empresa = construirEmpresa();
            Reserva reserva = construirReserva();
            int idReserva = reservaService.registrarPendiente(huespedes, empresa, reserva);
            Alertas.mostrarInfo("Reserva pendiente guardada",
                    "La reserva " + String.format("R-%04d", idReserva) + " quedó pendiente.");
            limpiarFormulario();
        } catch (ConflictoFechasException error) {
            Alertas.mostrarAdvertencia("Fechas no disponibles", error.getMessage());
            cargarHabitaciones();
        } catch (IllegalArgumentException | IllegalStateException error) {
            Alertas.mostrarAdvertencia("No se pudo guardar", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar la reserva.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void confirmarReserva() {
        try {
            List<Huesped> huespedes = acompanantes.obtener(construirHuesped());
            Empresa empresa = construirEmpresa();
            Reserva reserva = construirReserva();
            Pago pago = construirPago();
            boolean checkinInmediato = LocalDate.now().equals(reserva.getFechaCheckin());
            int idReserva = reservaService.registrar(huespedes, empresa, reserva, List.of(pago), checkinInmediato);
            Alertas.mostrarInfo("Reserva confirmada", checkinInmediato
                    ? "Se registró el check-in en la habitación " + habitacionSeleccionada.getNumero() + "."
                    : "La reserva " + String.format("R-%04d", idReserva) + " quedó confirmada.");
            limpiarFormulario();
        } catch (ConflictoFechasException error) {
            Alertas.mostrarAdvertencia("Fechas no disponibles", error.getMessage());
            cargarHabitaciones();
        } catch (IllegalArgumentException | IllegalStateException error) {
            Alertas.mostrarAdvertencia("No se pudo confirmar", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo registrar la reserva.\n\n" + error.getMessage());
        }
    }

    private Huesped construirHuesped() {
        String documentoActual = btnEmpresa.isSelected()
                ? txtDocumentoHuesped.getText().trim() : txtDocumento.getText().trim();
        if (!datosClienteListos || !documentoActual.equals(documentoHuespedResuelto)) {
            throw new IllegalArgumentException("Busque nuevamente el documento del huésped antes de guardar.");
        }
        return new Huesped("DNI", documentoActual, txtNombres.getText().trim(), txtApellidos.getText().trim(),
                txtPais.getText().trim(), textoOpcional(txtTelefono.getText()), textoOpcional(txtCorreo.getText()));
    }

    private Empresa construirEmpresa() {
        if (!btnEmpresa.isSelected()) {
            return null;
        }
        String ruc = txtDocumento.getText().trim();
        if (!ruc.equals(rucResuelto)) {
            throw new IllegalArgumentException("Busque nuevamente el RUC antes de guardar.");
        }
        Empresa empresa = new Empresa();
        empresa.setRuc(ruc);
        empresa.setRazonSocial(txtRazonSocial.getText().trim());
        empresa.setDireccion(textoOpcional(txtDireccionFiscal.getText()));
        return empresa;
    }

    private Empresa construirEmpresaDesdeCampos() {
        Empresa empresa = new Empresa();
        empresa.setRuc(txtDocumento.getText().trim());
        empresa.setRazonSocial(txtRazonSocial.getText().trim());
        empresa.setDireccion(textoOpcional(txtDireccionFiscal.getText()));
        return empresa;
    }

    private Reserva construirReserva() {
        if (habitacionSeleccionada == null) {
            throw new IllegalArgumentException("Seleccione una habitación disponible.");
        }
        Reserva reserva = new Reserva();
        reserva.setIdHabitacion(habitacionSeleccionada.getIdHabitacion());
        reserva.setFechaCheckin(dpFechaIngreso.getValue());
        reserva.setFechaCheckout(dpFechaSalida.getValue());
        reserva.setHoraCheckin(cbHoraIngreso.getValue() == null ? null : LocalTime.parse(cbHoraIngreso.getValue()));
        reserva.setNumHuespedes(spHuespedes.getValue());
        reserva.setMontoTotal(habitacionSeleccionada.getTipo().getPrecioBase().multiply(
                BigDecimal.valueOf(ChronoUnit.DAYS.between(dpFechaIngreso.getValue(), dpFechaSalida.getValue()))));
        reserva.setCanal("PRESENCIAL");
        return reserva;
    }

    private Pago construirPago() {
        BigDecimal monto;
        try {
            monto = new BigDecimal(txtMontoPago.getText().trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IllegalArgumentException("Ingrese un monto de pago con máximo dos decimales.");
        }
        Pago pago = new Pago();
        pago.setMonto(monto);
        pago.setMetodoPago(cbMetodoPago.getValue());
        pago.setTipoPago("ADELANTO");
        return pago;
    }

    @FXML
    private void cancelarFormulario() {
        limpiarFormulario();
    }

    private void limpiarFormulario() {
        secuenciaConsulta++;
        btnPersonaNatural.setSelected(true);
        txtDocumento.clear();
        txtDocumentoHuesped.clear();
        limpiarCliente("Ingrese el documento y pulse Buscar.");
        LocalDate hoy = LocalDate.now();
        dpFechaIngreso.setValue(hoy);
        dpFechaSalida.setValue(hoy.plusDays(1));
        cbHoraIngreso.setValue("14:00");
        spHuespedes.getValueFactory().setValue(1);
        cbPiso.setValue("Todos los pisos");
        cbTipoHabitacion.setValue("Todos los tipos");
        cbMetodoPago.setValue("EFECTIVO");
        cargarHabitaciones();
    }

    private void limpiarCliente(String mensaje) {
        datosClienteListos = false;
        documentoHuespedResuelto = null;
        rucResuelto = null;
        panelDatosHuesped.setVisible(false);
        panelDatosHuesped.setManaged(false);
        panelDatosEmpresa.setVisible(false);
        panelDatosEmpresa.setManaged(false);
        txtNombres.clear();
        txtApellidos.clear();
        txtPais.setText("Perú");
        txtTelefono.clear();
        txtCorreo.clear();
        txtRazonSocial.clear();
        txtDireccionFiscal.clear();
        hacerEditablesDatosHuesped(true);
        txtRazonSocial.setDisable(false);
        txtDireccionFiscal.setDisable(false);
        lblEstadoBusqueda.setText(mensaje);
        desbloquearBusqueda();
    }

    private String moneda(BigDecimal valor) {
        return String.format(Locale.US, "S/ %.2f", valor);
    }

    private String valorSeguro(String valor) {
        return valor == null ? "" : valor;
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
