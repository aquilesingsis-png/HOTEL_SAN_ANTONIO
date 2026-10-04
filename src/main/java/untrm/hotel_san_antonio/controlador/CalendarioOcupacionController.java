package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;

public class CalendarioOcupacionController {

    private static final String RECURSO_FILA =
            "/untrm/hotel_san_antonio/fxml/Reserva/Fila_calendario_reserva.fxml";
    private static final String RECURSO_CELDA =
            "/untrm/hotel_san_antonio/fxml/Reserva/Celda_calendario_reserva.fxml";
    private static final Locale LOCALE_ES = Locale.of("es", "PE");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label lblAyuda;
    @FXML private Label lblPeriodo;
    @FXML private ToggleButton btnSemana;
    @FXML private ToggleButton btnMes;
    @FXML private ToggleGroup grupoVista;
    @FXML private ChoiceBox<String> cbPiso;
    @FXML private HBox contenedorCabeceraDias;
    @FXML private VBox contenedorHabitaciones;
    @FXML private Label lblSinHabitaciones;

    private final ReservaService reservaService = new ReservaService();
    private LocalDate inicioSemana;
    private YearMonth mesActual;
    private LocalDate inicioPeriodo;
    private LocalDate finPeriodo;
    private List<Habitacion> habitaciones = List.of();
    private Map<Integer, List<Reserva>> reservasPorHabitacion = Map.of();
    private String textoCliente = "";

    @FXML
    public void initialize() {
        inicioSemana = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        mesActual = YearMonth.now();
        btnSemana.setSelected(true);
        grupoVista.selectedToggleProperty().addListener((observable, anterior, actual) -> {
            if (actual == null && anterior != null) {
                anterior.setSelected(true);
            }
        });
        configurarPisos();
        cbPiso.valueProperty().addListener((observable, anterior, actual) -> renderizarHabitaciones());
        actualizarPeriodoYCargar();
    }

    private void configurarPisos() {
        cbPiso.getItems().setAll("Todos los pisos");
        try {
            for (Integer piso : reservaService.listarPisos()) {
                cbPiso.getItems().add("Piso " + piso);
            }
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar los pisos.\n\n" + error.getMessage());
        }
        cbPiso.setValue("Todos los pisos");
    }

    @FXML
    private void periodoAnterior() {
        if (btnSemana.isSelected()) {
            inicioSemana = inicioSemana.minusWeeks(1);
        } else {
            mesActual = mesActual.minusMonths(1);
        }
        actualizarPeriodoYCargar();
    }

    @FXML
    private void periodoSiguiente() {
        if (btnSemana.isSelected()) {
            inicioSemana = inicioSemana.plusWeeks(1);
        } else {
            mesActual = mesActual.plusMonths(1);
        }
        actualizarPeriodoYCargar();
    }

    @FXML
    private void cambiarVista() {
        actualizarPeriodoYCargar();
    }

    private void actualizarPeriodoYCargar() {
        if (btnSemana.isSelected()) {
            inicioPeriodo = inicioSemana;
            finPeriodo = inicioSemana.plusDays(6);
            lblPeriodo.setText(inicioPeriodo.format(FORMATO_FECHA) + " - " + finPeriodo.format(FORMATO_FECHA));
        } else {
            inicioPeriodo = mesActual.atDay(1);
            finPeriodo = mesActual.atEndOfMonth();
            String nombreMes = mesActual.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES);
            lblPeriodo.setText(Character.toUpperCase(nombreMes.charAt(0)) + nombreMes.substring(1)
                    + " " + mesActual.getYear());
        }
        cargarDatos();
    }

    private void cargarDatos() {
        try {
            habitaciones = reservaService.listarHabitaciones();
            reservasPorHabitacion = reservaService.listarEnRango(inicioPeriodo, finPeriodo).stream()
                    .collect(Collectors.groupingBy(Reserva::getIdHabitacion));
            renderizarCabecera();
            renderizarHabitaciones();
        } catch (SQLException error) {
            habitaciones = List.of();
            reservasPorHabitacion = Map.of();
            mostrarSinDatos("No se pudo cargar el calendario.");
            Alertas.mostrarError("Error de base de datos", error.getMessage());
        } catch (IOException error) {
            mostrarSinDatos("No se pudieron cargar los componentes del calendario.");
            Alertas.mostrarError("Error de interfaz", error.getMessage());
        }
    }

    private void renderizarCabecera() throws IOException {
        contenedorCabeceraDias.getChildren().clear();
        for (LocalDate fecha = inicioPeriodo; !fecha.isAfter(finPeriodo); fecha = fecha.plusDays(1)) {
            FXMLLoader cargador = new FXMLLoader(getClass().getResource(RECURSO_CELDA));
            Label celda = cargador.load();
            Label texto = (Label) cargador.getNamespace().get("lblCelda");
            texto.setText(fecha.getDayOfWeek().getDisplayName(TextStyle.SHORT, LOCALE_ES)
                    + "\n" + fecha.getDayOfMonth());
            texto.setStyle(texto.getStyle() + " -fx-background-color: #f1ece4; -fx-text-fill: #4d4339;");
            contenedorCabeceraDias.getChildren().add(celda);
        }
    }

    private void renderizarHabitaciones() {
        if (inicioPeriodo == null) {
            return;
        }
        Integer piso = obtenerPisoSeleccionado();
        String busqueda = textoCliente;
        // con texto: las habitaciones cuyo numero o tipo lo contienen, o que tienen una reserva de esa persona
        List<Habitacion> filtradas = habitaciones.stream()
                .filter(habitacion -> piso == null || habitacion.getPiso() == piso)
                .filter(habitacion -> busqueda.isEmpty()
                        || habitacion.getNumero().toLowerCase(Locale.ROOT).contains(busqueda)
                        || habitacion.getTipo().getNombre().toLowerCase(Locale.ROOT).contains(busqueda)
                        || reservasPorHabitacion.getOrDefault(habitacion.getIdHabitacion(), List.of())
                                .stream().anyMatch(this::coincide))
                .toList();
        if (filtradas.isEmpty()) {
            mostrarSinDatos(busqueda.isEmpty() ? "No hay habitaciones para los filtros seleccionados."
                    : "No hay reservas de \"" + busqueda + "\" en este periodo. Cambie de semana o use la vista Lista.");
            return;
        }
        contenedorHabitaciones.getChildren().clear();
        try {
            for (Habitacion habitacion : filtradas) {
                contenedorHabitaciones.getChildren().add(cargarFila(habitacion));
            }
        } catch (IOException error) {
            mostrarSinDatos("No se pudo construir el calendario.");
            Alertas.mostrarError("Error de interfaz", error.getMessage());
        }
    }

    private HBox cargarFila(Habitacion habitacion) throws IOException {
        FXMLLoader cargadorFila = new FXMLLoader(getClass().getResource(RECURSO_FILA));
        HBox fila = cargadorFila.load();
        Map<String, Object> controlesFila = cargadorFila.getNamespace();
        ((Label) controlesFila.get("lblNumeroHabitacion")).setText(habitacion.getNumero());
        ((Label) controlesFila.get("lblTipoHabitacion")).setText(habitacion.getTipo().getNombre());
        HBox contenedorDias = (HBox) controlesFila.get("contenedorDias");
        List<Reserva> reservas = reservasPorHabitacion.getOrDefault(habitacion.getIdHabitacion(), List.of());
        for (LocalDate fecha = inicioPeriodo; !fecha.isAfter(finPeriodo); fecha = fecha.plusDays(1)) {
            contenedorDias.getChildren().add(cargarCelda(habitacion, reservas, fecha));
        }
        return fila;
    }

    private Label cargarCelda(Habitacion habitacion, List<Reserva> reservas, LocalDate fecha) throws IOException {
        FXMLLoader cargador = new FXMLLoader(getClass().getResource(RECURSO_CELDA));
        Label celda = cargador.load();
        Label texto = (Label) cargador.getNamespace().get("lblCelda");
        Tooltip ayuda = (Tooltip) cargador.getNamespace().get("tooltipCelda");
        EstadoDia estado = estadoParaDia(habitacion, reservas, fecha);
        Reserva reserva = reservaDelDia(reservas, fecha);
        texto.setText(reserva == null ? estado.abreviatura() : apellido(reserva));
        texto.setStyle(texto.getStyle() + " -fx-background-color: " + estado.color() + ";");
        ayuda.setText("Habitación " + habitacion.getNumero() + " · " + fecha.format(FORMATO_FECHA)
                + " · " + estado.descripcion() + (reserva == null ? "" : " · " + reserva.getNombreHuesped()));
        if (reserva != null) {
            texto.setStyle(texto.getStyle() + " -fx-cursor: hand;"
                    + (coincide(reserva) && !textoCliente.isEmpty()
                            ? " -fx-border-color: #3B210F; -fx-border-width: 2; -fx-border-radius: 5;" : ""));
            texto.setOnMouseClicked(evento -> abrirDetalle(reserva));
        }
        return celda;
    }

    private Reserva reservaDelDia(List<Reserva> reservas, LocalDate fecha) {
        for (Reserva reserva : reservas) {
            if (!fecha.isBefore(reserva.getFechaCheckin()) && fecha.isBefore(reserva.getFechaCheckout())) {
                return reserva;
            }
        }
        return null;
    }

    /** Ultima palabra del nombre (el apellido), recortada para que entre en la celda. */
    private String apellido(Reserva reserva) {
        String nombre = reserva.getNombreHuesped() == null ? "" : reserva.getNombreHuesped().trim();
        String ultima = nombre.contains(" ") ? nombre.substring(nombre.lastIndexOf(' ') + 1) : nombre;
        return ultima.length() > 8 ? ultima.substring(0, 8) : ultima;
    }

    private void abrirDetalle(Reserva reserva) {
        try {
            Navegacion.<DetalleReservaVentanaController>abrirModal(ReservasProgramadasController.RUTA_DETALLE,
                    "Detalle de reserva", controlador -> controlador.iniciar(reserva, this::cargarDatos));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el detalle de la reserva.\n\n" + error.getMessage());
        }
    }

    /** true si la reserva es de la persona que se esta buscando (nombre o codigo). */
    private boolean coincide(Reserva reserva) {
        if (textoCliente.isEmpty()) {
            return true;
        }
        String nombre = reserva.getNombreHuesped() == null ? "" : reserva.getNombreHuesped().toLowerCase(Locale.ROOT);
        return nombre.contains(textoCliente) || reserva.getCodigo().toLowerCase(Locale.ROOT).contains(textoCliente);
    }

    /** Lo llama ReservasController cuando se escribe en el buscador del encabezado. */
    public void buscarTexto(String texto) {
        textoCliente = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        renderizarHabitaciones();
    }

    private EstadoDia estadoParaDia(Habitacion habitacion, List<Reserva> reservas, LocalDate fecha) {
        for (Reserva reserva : reservas) {
            if (!fecha.isBefore(reserva.getFechaCheckin()) && fecha.isBefore(reserva.getFechaCheckout())) {
                return switch (reserva.getEstado()) {
                    case "PENDIENTE" -> EstadoDia.PENDIENTE;
                    case "CHECKIN" -> EstadoDia.OCUPADA;
                    default -> EstadoDia.RESERVADA;
                };
            }
        }
        if ("MANTENIMIENTO".equals(habitacion.getEstado())) {
            return EstadoDia.MANTENIMIENTO;
        }
        if (fecha.equals(LocalDate.now()) && "LIMPIEZA".equals(habitacion.getEstado())) {
            return EstadoDia.LIMPIEZA;
        }
        if (fecha.equals(LocalDate.now()) && "OCUPADA".equals(habitacion.getEstado())) {
            return EstadoDia.OCUPADA;
        }
        return EstadoDia.DISPONIBLE;
    }

    private Integer obtenerPisoSeleccionado() {
        String piso = cbPiso.getValue();
        return piso != null && piso.startsWith("Piso ") ? Integer.valueOf(piso.substring(5)) : null;
    }

    private void mostrarSinDatos(String mensaje) {
        contenedorHabitaciones.getChildren().setAll(lblSinHabitaciones);
        lblSinHabitaciones.setText(mensaje);
    }

    private enum EstadoDia {
        DISPONIBLE("Libre", "Disponible", "#2cb95f"),
        PENDIENTE("Pend.", "Reserva pendiente", "#c98a1b"),
        RESERVADA("Res.", "Reserva confirmada", "#2e88dd"),
        OCUPADA("Ocup.", "Ocupada / check-in", "#ef4141"),
        LIMPIEZA("Limp.", "En limpieza", "#eab851"),
        MANTENIMIENTO("Mant.", "Mantenimiento", "#8f887f");

        private final String abreviatura;
        private final String descripcion;
        private final String color;

        EstadoDia(String abreviatura, String descripcion, String color) {
            this.abreviatura = abreviatura;
            this.descripcion = descripcion;
            this.color = color;
        }

        String abreviatura() { return abreviatura; }
        String descripcion() { return descripcion; }
        String color() { return color; }
    }
}
