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

public class CancelarReservaController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private TextField txtBuscarReserva;

    @FXML
    private Label lblEstado;

    @FXML
    private Label lblCodigo;

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
    private Label lblHabitacion;

    @FXML
    private Label lblHuespedes;

    @FXML
    private Label lblTotal;

    @FXML
    private TextArea txtObservaciones;

    @FXML
    private ChoiceBox<String> cbMotivo;

    @FXML
    private TextArea txtDetalle;

    @FXML
    private CheckBox chkNotificar;


    private final ReservaService reservaService = new ReservaService();

    private Reserva reservaActual;


    @FXML
    public void initialize() {

        configurarMotivos();

        limpiar();
    }


    private void configurarMotivos() {

        cbMotivo.getItems().addAll(
                "El cliente desistió",
                "Cambio de fecha",
                "Error en la reserva",
                "Falta de pago",
                "Duplicidad de reserva",
                "Otro"
        );
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
        lblCliente.setText(r.getNombreHuesped());
        lblDocumento.setText(r.getTipoDocumentoHuesped() + " " + r.getNumDocumentoHuesped());
        lblTelefono.setText(valorSeguro(r.getTelefonoHuesped()));
        lblCorreo.setText(valorSeguro(r.getEmailHuesped()));
        lblIngreso.setText(r.getFechaCheckin().format(FORMATO_FECHA));
        lblSalida.setText(r.getFechaCheckout().format(FORMATO_FECHA));
        lblNoches.setText(String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(r.getFechaCheckin(), r.getFechaCheckout())));
        lblHabitacion.setText(r.getNumeroHabitacion() + " · " + r.getNombreTipoHabitacion());
        lblHuespedes.setText("--");
        lblTotal.setText(String.format(java.util.Locale.US, "S/ %.2f", r.getMontoTotal()));
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
    private void cancelarReserva() {

        if (reservaActual == null || "--".equals(lblCodigo.getText())) {

            Alertas.mostrarAdvertencia(
                    "Reserva requerida",
                    "Primero seleccione una reserva."
            );

            return;
        }


        if (cbMotivo.getValue() == null) {

            Alertas.mostrarAdvertencia(
                    "Motivo requerido",
                    "Seleccione un motivo de cancelación."
            );

            return;
        }

        try {
            reservaService.cancelar(reservaActual.getIdReserva(), reservaActual.getEstado(),
                    cbMotivo.getValue(), txtDetalle.getText().trim());
        } catch (IllegalStateException e) {
            Alertas.mostrarAdvertencia("No se puede cancelar", e.getMessage());
            return;
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cancelar la reserva.\n\n" + e.getMessage());
            return;
        }

        Alertas.mostrarInfo(
                "Cancelación registrada",
                "La reserva " + reservaActual.getCodigo() + " quedó cancelada."
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

        lblCliente.setText("--");

        lblDocumento.setText("--");

        lblTelefono.setText("--");

        lblCorreo.setText("--");

        lblIngreso.setText("--");

        lblSalida.setText("--");

        lblNoches.setText("--");

        lblHabitacion.setText("--");

        lblHuespedes.setText("--");

        lblTotal.setText("S/ 0.00");

        txtObservaciones.clear();

        txtDetalle.clear();

        cbMotivo.setValue(null);

        chkNotificar.setSelected(false);
    }


    private String valorSeguro(String valor) {
        return valor == null || valor.isBlank() ? "--" : valor;
    }


}
