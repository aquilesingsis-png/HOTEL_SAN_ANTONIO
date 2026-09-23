/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador.Reserva;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;

import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;

/**
 *
 * @author HP
 */

public class ReservasProgramadasController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TAMANO_PAGINA = 10;

    @FXML
    private TextField txtBuscar;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private ToggleButton btnTodas;

    @FXML
    private ToggleButton btnPendientes;

    @FXML
    private ToggleButton btnConfirmadas;

    @FXML
    private ToggleButton btnPorLlegar;

    @FXML
    private ToggleButton btnFinalizadas;

    @FXML
    private ToggleButton btnCanceladas;

    @FXML
    private TableView<Reserva> tablaReservas;

    @FXML
    private TableColumn<Reserva, String> colCodigo;

    @FXML
    private TableColumn<Reserva, String> colCliente;

    @FXML
    private TableColumn<Reserva, String> colHabitacion;

    @FXML
    private TableColumn<Reserva, String> colIngreso;

    @FXML
    private TableColumn<Reserva, String> colSalida;

    @FXML
    private TableColumn<Reserva, String> colEstado;

    @FXML
    private TableColumn<Reserva, Void> colAcciones;

    @FXML
    private Label lblCantidad;

    @FXML
    private Button btnPagina;


    private final ReservaService reservaService = new ReservaService();

    private List<Reserva> resultadoCompleto = java.util.List.of();

    private int paginaActual = 1;


    @FXML
    public void initialize() {

        configurarFechas();

        configurarTabla();

        buscarYMostrar();
    }


    private void configurarFechas() {

        dpDesde.setValue(null);

        dpHasta.setValue(null);
    }


    private void configurarTabla() {

        tablaReservas.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        colCodigo.setCellValueFactory(datos ->
                new javafx.beans.property.SimpleStringProperty(datos.getValue().getCodigo()));

        colCliente.setCellValueFactory(datos ->
                new javafx.beans.property.SimpleStringProperty(datos.getValue().getNombreHuesped()));

        colHabitacion.setCellValueFactory(datos ->
                new javafx.beans.property.SimpleStringProperty(
                        datos.getValue().getNumeroHabitacion() + " · " + datos.getValue().getNombreTipoHabitacion()));

        colIngreso.setCellValueFactory(datos ->
                new javafx.beans.property.SimpleStringProperty(datos.getValue().getFechaCheckin().format(FORMATO_FECHA)));

        colSalida.setCellValueFactory(datos ->
                new javafx.beans.property.SimpleStringProperty(datos.getValue().getFechaCheckout().format(FORMATO_FECHA)));

        colEstado.setCellValueFactory(datos ->
                new javafx.beans.property.SimpleStringProperty(textoEstado(datos.getValue().getEstado())));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean vacio) {
                super.updateItem(estado, vacio);
                if (vacio || estado == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(estado);
                setStyle("-fx-text-fill: " + colorEstado(getTableRow().getItem() == null
                        ? "" : getTableRow().getItem().getEstado()) + "; -fx-font-weight: bold;");
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnConfirmar = new Button("Confirmar");
            private final Button btnCancelar = new Button("Cancelar");
            private final HBox caja = new HBox(6, btnConfirmar, btnCancelar);
            {
                btnConfirmar.setStyle("-fx-background-color: #2E88DD; -fx-text-fill: white; -fx-font-size: 10px; "
                        + "-fx-background-radius: 5; -fx-cursor: hand;");
                btnCancelar.setStyle("-fx-background-color: #E24C4C; -fx-text-fill: white; -fx-font-size: 10px; "
                        + "-fx-background-radius: 5; -fx-cursor: hand;");
                btnConfirmar.setOnAction(e -> confirmarDesdeTabla(getTableRow().getItem()));
                btnCancelar.setOnAction(e -> cancelarDesdeTabla(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void valor, boolean vacio) {
                super.updateItem(valor, vacio);
                Reserva r = vacio ? null : getTableRow().getItem();
                if (r == null) {
                    setGraphic(null);
                    return;
                }
                btnConfirmar.setVisible("PENDIENTE".equals(r.getEstado()));
                btnConfirmar.setManaged("PENDIENTE".equals(r.getEstado()));
                boolean cancelable = "PENDIENTE".equals(r.getEstado()) || "CONFIRMADA".equals(r.getEstado());
                btnCancelar.setVisible(cancelable);
                btnCancelar.setManaged(cancelable);
                setGraphic(cancelable || "PENDIENTE".equals(r.getEstado()) ? caja : null);
            }
        });
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

    private String colorEstado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "PENDIENTE" -> "#C98A1B";
            case "CONFIRMADA" -> "#2E88DD";
            case "CHECKIN" -> "#2CB95F";
            case "FINALIZADA" -> "#6A5F52";
            case "CANCELADA" -> "#D9433A";
            default -> "#2C2118";
        };
    }


    private void confirmarDesdeTabla(Reserva r) {
        if (r == null) {
            return;
        }
        if (!Alertas.confirmar("Confirmar reserva", "¿Confirmar la reserva " + r.getCodigo() + " de "
                + r.getNombreHuesped() + "?")) {
            return;
        }
        try {
            reservaService.confirmar(r.getIdReserva(), r.getEstado());
            buscarYMostrar();
        } catch (IllegalStateException e) {
            Alertas.mostrarError("No se puede confirmar", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo confirmar la reserva.\n\n" + e.getMessage());
        }
    }

    private void cancelarDesdeTabla(Reserva r) {
        if (r == null) {
            return;
        }
        if (!Alertas.confirmar("Cancelar reserva", "¿Cancelar la reserva " + r.getCodigo() + " de "
                + r.getNombreHuesped() + "?")) {
            return;
        }
        try {
            reservaService.cancelar(r.getIdReserva(), r.getEstado(), "Otro", "Cancelada desde el listado de reservas.");
            buscarYMostrar();
        } catch (IllegalStateException e) {
            Alertas.mostrarError("No se puede cancelar", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cancelar la reserva.\n\n" + e.getMessage());
        }
    }


    private String estadoSeleccionado() {
        if (btnPendientes.isSelected()) {
            return "PENDIENTE";
        }
        if (btnConfirmadas.isSelected()) {
            return "CONFIRMADA";
        }
        if (btnPorLlegar.isSelected()) {
            return "POR_LLEGAR";
        }
        if (btnFinalizadas.isSelected()) {
            return "FINALIZADA";
        }
        if (btnCanceladas.isSelected()) {
            return "CANCELADA";
        }
        return null;
    }


    @FXML
    private void filtrarEstado() {

        paginaActual = 1;

        buscarYMostrar();
    }


    @FXML
    private void filtrarFechas() {

        paginaActual = 1;

        buscarYMostrar();
    }


    private void buscarYMostrar() {

        String texto = txtBuscar == null ? null : txtBuscar.getText();

        try {
            resultadoCompleto = reservaService.buscar(texto, estadoSeleccionado(), dpDesde.getValue(), dpHasta.getValue());
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar las reservas.\n\n" + e.getMessage());
            resultadoCompleto = java.util.List.of();
        }

        actualizarPagina();
    }


    @FXML
    private void abrirNuevaReserva() {

        try {
            Navegacion.mostrar("/untrm/hotel_san_antonio/fxml/Reserva/Nueva_reserva.fxml");
        } catch (java.io.IOException e) {
            Alertas.mostrarError("Error", "No se pudo abrir Nueva reserva.\n\n" + e.getMessage());
        }
    }


    @FXML
    private void paginaAnterior() {

        if (paginaActual > 1) {
            paginaActual--;
        }

        actualizarPagina();
    }


    @FXML
    private void paginaSiguiente() {

        int totalPaginas = totalPaginas();

        if (paginaActual < totalPaginas) {
            paginaActual++;
        }

        actualizarPagina();
    }


    private int totalPaginas() {
        return Math.max(1, (int) Math.ceil(resultadoCompleto.size() / (double) TAMANO_PAGINA));
    }


    private void actualizarPagina() {

        int totalPaginas = totalPaginas();
        if (paginaActual > totalPaginas) {
            paginaActual = totalPaginas;
        }

        int desde = (paginaActual - 1) * TAMANO_PAGINA;
        int hasta = Math.min(desde + TAMANO_PAGINA, resultadoCompleto.size());

        tablaReservas.getItems().setAll(desde < hasta ? resultadoCompleto.subList(desde, hasta) : java.util.List.of());

        btnPagina.setText(String.valueOf(paginaActual));

        actualizarContador();
    }


    private void actualizarContador() {

        int cantidad = resultadoCompleto.size();

        lblCantidad.setText(
                cantidad + (cantidad == 1 ? " reserva" : " reservas")
        );
    }
}
