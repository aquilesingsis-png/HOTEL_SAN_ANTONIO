package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;

/** Vista "Lista" de la pantalla Reservas. El texto a buscar se lo pasa ReservasController. */
public class ReservasProgramadasController {

    static final String RUTA_DETALLE = "/untrm/hotel_san_antonio/fxml/Reserva/Detalle_reserva_ventana.fxml";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TAMANO_PAGINA = 10;

    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private ToggleButton btnTodas;
    @FXML private ToggleButton btnPorLlegar;
    @FXML private ToggleButton btnEnCasa;
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

    private final ReservaService reservaService = new ReservaService();
    private List<Reserva> resultadoCompleto = List.of();
    private String textoBusqueda = "";
    private int paginaActual = 1;

    @FXML
    public void initialize() {
        configurarTabla();
        tablaReservas.getSelectionModel().selectedItemProperty().addListener(
                (observable, anterior, actual) -> btnVerDetalle.setDisable(actual == null));
        tablaReservas.setOnMouseClicked(evento -> {
            if (evento.getClickCount() == 2) {
                verDetalle();
            }
        });
        btnTodas.setSelected(true);
        buscarYMostrar();
    }

    /** Lo llama ReservasController cuando se escribe en el buscador del encabezado. */
    public void buscarTexto(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim();
        paginaActual = 1;
        buscarYMostrar();
    }

    /** Vuelve a consultar la base (por ejemplo, despues de cancelar o cambiar una reserva). */
    public void recargar() {
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
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        btnTodas.setSelected(true);
        paginaActual = 1;
        buscarYMostrar();
    }

    private void buscarYMostrar() {
        try {
            resultadoCompleto = reservaService.buscar(
                    textoBusqueda, estadoSeleccionado(), dpDesde.getValue(), dpHasta.getValue());
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
        try {
            Navegacion.<DetalleReservaVentanaController>abrirModal(RUTA_DETALLE, "Detalle de reserva",
                    controlador -> controlador.iniciar(reserva, this::buscarYMostrar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el detalle de la reserva.\n\n" + error.getMessage());
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
        if (btnPorLlegar.isSelected()) return "POR_LLEGAR";
        if (btnEnCasa.isSelected()) return "CHECKIN";
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
