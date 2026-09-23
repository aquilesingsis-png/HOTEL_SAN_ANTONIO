/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador.Reserva;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;

public class ConfirmarReservaController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private TextField txtBuscarReserva;

    @FXML
    private Label lblEstado;

    @FXML
    private Label lblCodigo;

    @FXML
    private Label lblFechaReserva;

    @FXML
    private Label lblCliente;

    @FXML
    private Label lblDocumento;

    @FXML
    private Label lblTelefono;

    @FXML
    private Label lblCorreo;

    @FXML
    private Label lblIngreso;

    @FXML
    private Label lblSalida;

    @FXML
    private Label lblNoches;

    @FXML
    private Label lblHuespedes;

    @FXML
    private Label lblHabitacion;

    @FXML
    private Label lblTotal;

    @FXML
    private TextArea txtObservaciones;

    @FXML
    private ChoiceBox<String> cbEstado;

    @FXML
    private TextArea txtMensaje;

    @FXML
    private CheckBox chkCorreo;


    private final ReservaService reservaService = new ReservaService();

    private Reserva reservaActual;


    @FXML
    public void initialize() {

        cbEstado.getItems().addAll(
                "Pendiente",
                "Confirmada"
        );

        cbEstado.setValue(
                "Confirmada"
        );

        limpiar();
    }


    @FXML
    private void buscarReserva() {

        String busqueda =
                txtBuscarReserva
                        .getText()
                        .trim();

        if (busqueda.isEmpty()) {

            Alertas.mostrarAdvertencia(
                    "Búsqueda requerida",
                    "Ingrese código, nombre o documento."
            );

            return;
        }

        try {
            reservaActual = reservaService.buscarUno(busqueda);
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar la reserva.\n\n" + e.getMessage());
            return;
        }

        if (reservaActual == null) {
            limpiar();
            Alertas.mostrarAdvertencia(
                    "Sin resultados",
                    "No se encontró ninguna reserva con \"" + busqueda + "\"."
            );
            return;
        }

        cargarReserva(reservaActual);
    }


    private void cargarReserva(Reserva r) {

        lblEstado.setText("Reserva " + textoEstado(r.getEstado()));
        lblCodigo.setText(r.getCodigo());
        lblFechaReserva.setText(r.getFechaReserva() == null ? "--"
                : r.getFechaReserva().toLocalDate().format(FORMATO_FECHA));
        lblCliente.setText(r.getNombreHuesped());
        lblDocumento.setText(r.getTipoDocumentoHuesped() + " " + r.getNumDocumentoHuesped());
        lblTelefono.setText(valorSeguro(r.getTelefonoHuesped()));
        lblCorreo.setText(valorSeguro(r.getEmailHuesped()));
        lblIngreso.setText(r.getFechaCheckin().format(FORMATO_FECHA));
        lblSalida.setText(r.getFechaCheckout().format(FORMATO_FECHA));
        lblNoches.setText(String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(r.getFechaCheckin(), r.getFechaCheckout())));
        lblHuespedes.setText("--");
        lblHabitacion.setText(r.getNumeroHabitacion() + " · " + r.getNombreTipoHabitacion());
        lblTotal.setText(String.format(java.util.Locale.US, "S/ %.2f", r.getMontoTotal()));

        cbEstado.setValue("PENDIENTE".equals(r.getEstado()) ? "Pendiente" : "Confirmada");
    }


    private String textoEstado(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "pendiente";
            case "CONFIRMADA" -> "confirmada";
            case "CHECKIN" -> "con check-in hecho";
            case "FINALIZADA" -> "finalizada";
            case "CANCELADA" -> "cancelada";
            default -> estado;
        };
    }


    @FXML
    private void confirmarReserva() {

        if (reservaActual == null || "--".equals(lblCodigo.getText())) {

            Alertas.mostrarAdvertencia(
                    "Reserva requerida",
                    "Primero seleccione una reserva."
            );

            return;
        }

        if ("Pendiente".equals(cbEstado.getValue())) {

            Alertas.mostrarAdvertencia(
                    "Nada que hacer",
                    "La reserva ya está pendiente; elija \"Confirmada\" para confirmarla."
            );

            return;
        }

        try {
            reservaService.confirmar(reservaActual.getIdReserva(), reservaActual.getEstado());
        } catch (IllegalStateException e) {
            Alertas.mostrarAdvertencia("No se puede confirmar", e.getMessage());
            return;
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo confirmar la reserva.\n\n" + e.getMessage());
            return;
        }

        Alertas.mostrarInfo(
                "Reserva confirmada",
                "La reserva " + reservaActual.getCodigo() + " quedó confirmada."
        );

        limpiar();
    }


    @FXML
    private void volver() {

        limpiar();
    }


    private void limpiar() {

        reservaActual = null;

        lblEstado.setText(
                "Sin reserva seleccionada"
        );

        lblCodigo.setText("--");

        lblFechaReserva.setText("--");

        lblCliente.setText("--");

        lblDocumento.setText("--");

        lblTelefono.setText("--");

        lblCorreo.setText("--");

        lblIngreso.setText("--");

        lblSalida.setText("--");

        lblNoches.setText("--");

        lblHuespedes.setText("--");

        lblHabitacion.setText("--");

        lblTotal.setText("S/ 0.00");

        txtObservaciones.clear();

        txtMensaje.clear();

        chkCorreo.setSelected(false);
    }


    private String valorSeguro(String valor) {
        return valor == null || valor.isBlank() ? "--" : valor;
    }


}
