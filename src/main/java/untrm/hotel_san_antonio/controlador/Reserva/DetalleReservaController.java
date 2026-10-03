package untrm.hotel_san_antonio.controlador.Reserva;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import untrm.hotel_san_antonio.modelo.Reserva;

/** Controla el bloque visual reutilizado por Confirmar y Cancelar. */
public class DetalleReservaController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label lblEstado;
    @FXML private Label lblCodigo;
    @FXML private Label lblCliente;
    @FXML private Label lblDocumento;
    @FXML private Label lblTelefono;
    @FXML private Label lblCorreo;
    @FXML private Label lblIngreso;
    @FXML private Label lblSalida;
    @FXML private Label lblNoches;
    @FXML private Label lblHabitacion;
    @FXML private Label lblHuespedes;
    @FXML private Label lblTotal;

    public void mostrar(Reserva reserva) {
        lblEstado.setText(textoEstado(reserva.getEstado()));
        lblCodigo.setText(reserva.getCodigo());
        lblCliente.setText(valorSeguro(reserva.getNombreHuesped()));
        lblDocumento.setText(valorSeguro(reserva.getTipoDocumentoHuesped()) + " "
                + valorSeguro(reserva.getNumDocumentoHuesped()));
        lblTelefono.setText(valorSeguro(reserva.getTelefonoHuesped()));
        lblCorreo.setText(valorSeguro(reserva.getEmailHuesped()));
        lblIngreso.setText(reserva.getFechaCheckin().format(FORMATO_FECHA));
        lblSalida.setText(reserva.getFechaCheckout().format(FORMATO_FECHA));
        lblNoches.setText(String.valueOf(ChronoUnit.DAYS.between(
                reserva.getFechaCheckin(), reserva.getFechaCheckout())));
        lblHabitacion.setText(reserva.getNumeroHabitacion() + " · " + reserva.getNombreTipoHabitacion());
        lblHuespedes.setText(String.valueOf(reserva.getNumHuespedes()));
        lblTotal.setText(String.format(Locale.US, "S/ %.2f", reserva.getMontoTotal()));
    }

    public void limpiar() {
        lblEstado.setText("Sin reserva seleccionada");
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
    }

    private String textoEstado(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "Reserva pendiente";
            case "CONFIRMADA" -> "Reserva confirmada";
            case "CHECKIN" -> "Reserva con check-in";
            case "FINALIZADA" -> "Reserva finalizada";
            case "CANCELADA" -> "Reserva cancelada";
            default -> valorSeguro(estado);
        };
    }

    private String valorSeguro(String valor) {
        return valor == null || valor.isBlank() ? "--" : valor;
    }
}
