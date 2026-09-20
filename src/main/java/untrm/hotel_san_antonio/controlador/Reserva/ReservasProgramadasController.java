/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador.Reserva;
import java.time.LocalDate;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
/**
 *
 * @author HP
 */

public class ReservasProgramadasController {

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
    private TableView<Object> tablaReservas;

    @FXML
    private TableColumn<Object, String> colCodigo;

    @FXML
    private TableColumn<Object, String> colCliente;

    @FXML
    private TableColumn<Object, String> colHabitacion;

    @FXML
    private TableColumn<Object, String> colIngreso;

    @FXML
    private TableColumn<Object, String> colSalida;

    @FXML
    private TableColumn<Object, String> colEstado;

    @FXML
    private TableColumn<Object, String> colAcciones;

    @FXML
    private Label lblCantidad;

    @FXML
    private Button btnPagina;


    private int paginaActual = 1;


    @FXML
    public void initialize() {

        configurarFechas();

        configurarTabla();

        actualizarContador();

        actualizarPagina();
    }


    private void configurarFechas() {

        LocalDate hoy = LocalDate.now();

        dpDesde.setValue(hoy);

        dpHasta.setValue(
                hoy.plusDays(30)
        );
    }


    private void configurarTabla() {

        tablaReservas.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        // La tabla inicia completamente vacía.
        tablaReservas.getItems().clear();
    }


    @FXML
    private void filtrarEstado() {

        /*
         * Cuando tengas MySQL:
         *
         * String estado = obtenerEstadoSeleccionado();
         * reservaService.buscarPorEstado(estado);
         */

        tablaReservas.getItems().clear();

        actualizarContador();
    }


    @FXML
    private void filtrarFechas() {

        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();

        if (desde == null || hasta == null) {
            return;
        }

        /*
         * FUTURO:
         *
         * reservaService.buscarPorFechas(desde, hasta);
         */

        tablaReservas.getItems().clear();

        actualizarContador();
    }


    @FXML
    private void abrirNuevaReserva() {

        /*
         * La navegación será manejada
         * posteriormente desde PrimaryController.
         */
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

        /*
         * Cuando exista la BD se verificará
         * si hay otra página.
         */

        paginaActual++;

        actualizarPagina();
    }


    private void actualizarPagina() {

        btnPagina.setText(
                String.valueOf(paginaActual)
        );
    }


    private void actualizarContador() {

        int cantidad =
                tablaReservas.getItems().size();

        lblCantidad.setText(
                cantidad + " reservas"
        );
    }
}
