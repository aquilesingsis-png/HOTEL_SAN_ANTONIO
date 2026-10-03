package untrm.hotel_san_antonio.controlador.Reserva;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;

public class ReservasProgramadasController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TAMANO_PAGINA = 10;

    @FXML private TextField txtBuscar;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private ToggleButton btnTodas;
    @FXML private ToggleButton btnPendientes;
    @FXML private ToggleButton btnConfirmadas;
    @FXML private ToggleButton btnPorLlegar;
    @FXML private ToggleButton btnFinalizadas;
    @FXML private ToggleButton btnCanceladas;
    @FXML private TableView<Reserva> tablaReservas;
    @FXML private TableColumn<Reserva, String> colCodigo;
    @FXML private TableColumn<Reserva, String> colCliente;
    @FXML private TableColumn<Reserva, String> colHabitacion;
    @FXML private TableColumn<Reserva, String> colIngreso;
    @FXML private TableColumn<Reserva, String> colSalida;
    @FXML private TableColumn<Reserva, String> colEstado;
    @FXML private Label lblCantidad;
    @FXML private Label lblPagina;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Button btnVerDetalle;
    @FXML private Button btnCambiarHabitacion;

    private final ReservaService reservaService = new ReservaService();
    private List<Reserva> resultadoCompleto = List.of();
    private int paginaActual = 1;

    @FXML
    public void initialize() {
        configurarTabla();
        tablaReservas.getSelectionModel().selectedItemProperty().addListener(
                (observable, anterior, actual) -> {
                    btnVerDetalle.setDisable(actual == null);
                    btnCambiarHabitacion.setDisable(actual == null || !("CONFIRMADA".equals(actual.getEstado())
                            || "CHECKIN".equals(actual.getEstado())));
                });
        btnTodas.setSelected(true);
        buscarYMostrar();
    }

    private void configurarTabla() {
        tablaReservas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colCodigo.setCellValueFactory(datos -> new SimpleStringProperty(datos.getValue().getCodigo()));
        colCliente.setCellValueFactory(datos -> new SimpleStringProperty(datos.getValue().getNombreHuesped()));
        colHabitacion.setCellValueFactory(datos -> new SimpleStringProperty(
                datos.getValue().getNumeroHabitacion() + " · " + datos.getValue().getNombreTipoHabitacion()));
        colIngreso.setCellValueFactory(datos -> new SimpleStringProperty(
                datos.getValue().getFechaCheckin().format(FORMATO_FECHA)));
        colSalida.setCellValueFactory(datos -> new SimpleStringProperty(
                datos.getValue().getFechaCheckout().format(FORMATO_FECHA)));
        colEstado.setCellValueFactory(datos -> new SimpleStringProperty(textoEstado(datos.getValue().getEstado())));
    }

    @FXML
    private void filtrarEstado() {
        paginaActual = 1;
        buscarYMostrar();
    }

    @FXML
    private void buscarReservas() {
        paginaActual = 1;
        buscarYMostrar();
    }

    @FXML
    private void limpiarFiltros() {
        txtBuscar.clear();
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        btnTodas.setSelected(true);
        paginaActual = 1;
        buscarYMostrar();
    }

    private void buscarYMostrar() {
        try {
            resultadoCompleto = reservaService.buscar(
                    txtBuscar.getText(), estadoSeleccionado(), dpDesde.getValue(), dpHasta.getValue());
        } catch (IllegalArgumentException error) {
            Alertas.mostrarAdvertencia("Filtro inválido", error.getMessage());
            return;
        } catch (SQLException error) {
            resultadoCompleto = List.of();
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar las reservas.\n\n" + error.getMessage());
        }
        actualizarPagina();
    }

    @FXML
    private void verDetalle() {
        Reserva reserva = tablaReservas.getSelectionModel().getSelectedItem();
        if (reserva == null) {
            return;
        }
        Task<List<Huesped>> tarea = new Task<>() {
            @Override protected List<Huesped> call() throws Exception {
                return reservaService.listarHuespedes(reserva.getIdReserva());
            }
        };
        tarea.setOnSucceeded(e -> mostrarDetalle(reserva, tarea.getValue()));
        tarea.setOnFailed(e -> Alertas.mostrarError("Detalle de reserva",
                "No se pudieron cargar los huéspedes de la reserva."));
        Thread hilo = new Thread(tarea, "detalle-huespedes");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void mostrarDetalle(Reserva reserva, List<Huesped> huespedes) {
        long noches = ChronoUnit.DAYS.between(reserva.getFechaCheckin(), reserva.getFechaCheckout());
        String nombres = huespedes.stream()
                .map(h -> h.getNombres() + " " + h.getApellidos() + " (" + h.getNumDocumento() + ")")
                .collect(java.util.stream.Collectors.joining("\n  "));
        String detalle = "Código: " + reserva.getCodigo()
                + "\nCliente: " + reserva.getNombreHuesped()
                + "\nDocumento: " + reserva.getTipoDocumentoHuesped() + " " + reserva.getNumDocumentoHuesped()
                + "\nHabitación: " + reserva.getNumeroHabitacion() + " · " + reserva.getNombreTipoHabitacion()
                + "\nIngreso: " + reserva.getFechaCheckin().format(FORMATO_FECHA)
                + "\nSalida: " + reserva.getFechaCheckout().format(FORMATO_FECHA)
                + "\nNoches: " + noches
                + "\nHuéspedes: " + reserva.getNumHuespedes()
                + "\n  " + nombres
                + "\nEstado: " + textoEstado(reserva.getEstado())
                + String.format(Locale.US, "\nTotal: S/ %.2f", reserva.getMontoTotal());
        Alertas.mostrarInfo("Detalle de " + reserva.getCodigo(), detalle);
    }

    @FXML private void cambiarHabitacion() {
        Reserva seleccionada = tablaReservas.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            Alertas.mostrarInfo("Cambio de habitación", "Seleccione una reserva activa.");
            return;
        }
        try {
            Navegacion.<CambiarHabitacionController>abrirModal(
                    "/untrm/hotel_san_antonio/fxml/Reserva/cambiar_habitacion.fxml",
                    "Cambiar habitación", controlador -> controlador.iniciar(seleccionada, this::buscarYMostrar));
        } catch (java.io.IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el cambio de habitación.");
        }
    }

    @FXML
    private void abrirNuevaReserva() {
        try {
            Navegacion.mostrar("/untrm/hotel_san_antonio/fxml/Reserva/Nueva_reserva.fxml");
        } catch (java.io.IOException error) {
            Alertas.mostrarError("Error", "No se pudo abrir Nueva reserva.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void paginaAnterior() {
        if (paginaActual > 1) {
            paginaActual--;
            actualizarPagina();
        }
    }

    @FXML
    private void paginaSiguiente() {
        if (paginaActual < totalPaginas()) {
            paginaActual++;
            actualizarPagina();
        }
    }

    private void actualizarPagina() {
        int totalPaginas = totalPaginas();
        paginaActual = Math.min(paginaActual, totalPaginas);
        int desde = (paginaActual - 1) * TAMANO_PAGINA;
        int hasta = Math.min(desde + TAMANO_PAGINA, resultadoCompleto.size());
        tablaReservas.getItems().setAll(desde < hasta
                ? resultadoCompleto.subList(desde, hasta) : List.of());
        lblPagina.setText(paginaActual + " / " + totalPaginas);
        btnAnterior.setDisable(paginaActual <= 1);
        btnSiguiente.setDisable(paginaActual >= totalPaginas);
        btnVerDetalle.setDisable(tablaReservas.getSelectionModel().getSelectedItem() == null);
        int cantidad = resultadoCompleto.size();
        lblCantidad.setText(cantidad + (cantidad == 1 ? " reserva" : " reservas"));
    }

    private int totalPaginas() {
        return Math.max(1, (int) Math.ceil(resultadoCompleto.size() / (double) TAMANO_PAGINA));
    }

    private String estadoSeleccionado() {
        if (btnPendientes.isSelected()) return "PENDIENTE";
        if (btnConfirmadas.isSelected()) return "CONFIRMADA";
        if (btnPorLlegar.isSelected()) return "POR_LLEGAR";
        if (btnFinalizadas.isSelected()) return "FINALIZADA";
        if (btnCanceladas.isSelected()) return "CANCELADA";
        return null;
    }

    private String textoEstado(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "Pendiente";
            case "CONFIRMADA" -> "Confirmada";
            case "CHECKIN" -> "Con check-in";
            case "FINALIZADA" -> "Finalizada";
            case "CANCELADA" -> "Cancelada";
            default -> estado;
        };
    }
}
