package untrm.hotel_san_antonio.controlador.Reserva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;

public class ConfirmarReservaController {

    @FXML private TextField txtBuscarReserva;
    @FXML private DetalleReservaController detalleReservaController;
    @FXML private Label lblAdelanto;
    @FXML private Label lblMinimo;
    @FXML private ChoiceBox<String> cbMetodoPago;
    @FXML private TextField txtMontoPago;

    private final ReservaService reservaService = new ReservaService();
    private Reserva reservaSeleccionada;

    @FXML
    public void initialize() {
        cbMetodoPago.getItems().setAll("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE");
        cbMetodoPago.setValue("EFECTIVO");
        limpiarDetalle();
    }

    @FXML
    private void buscarReserva() {
        String busqueda = txtBuscarReserva.getText().trim();
        if (busqueda.isEmpty()) {
            Alertas.mostrarAdvertencia("Búsqueda requerida", "Ingrese el código exacto de la reserva.");
            return;
        }
        try {
            Reserva encontrada = reservaService.buscarUno(busqueda);
            if (encontrada == null) {
                limpiarDetalle();
                Alertas.mostrarAdvertencia("Sin resultados", "No se encontró una reserva con ese criterio.");
                return;
            }
            reservaSeleccionada = encontrada;
            mostrarDetalle(encontrada);
        } catch (IllegalArgumentException error) {
            limpiarDetalle();
            Alertas.mostrarAdvertencia("Búsqueda ambigua", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar la reserva.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void confirmarReserva() {
        if (reservaSeleccionada == null) {
            Alertas.mostrarAdvertencia("Reserva requerida", "Primero busque una reserva pendiente.");
            return;
        }
        BigDecimal monto;
        try {
            monto = new BigDecimal(txtMontoPago.getText().trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException error) {
            Alertas.mostrarAdvertencia("Monto inválido", "Ingrese un monto con máximo dos decimales.");
            return;
        }

        Pago pago = new Pago();
        pago.setMonto(monto);
        pago.setMetodoPago(cbMetodoPago.getValue());
        try {
            reservaService.confirmar(reservaSeleccionada.getIdReserva(), pago);
            Alertas.mostrarInfo("Reserva confirmada",
                    "La reserva " + reservaSeleccionada.getCodigo() + " quedó confirmada y el pago fue registrado.");
            limpiarFormulario();
        } catch (IllegalArgumentException | IllegalStateException error) {
            Alertas.mostrarAdvertencia("No se puede confirmar", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos",
                    "No se confirmó la reserva; la transacción fue revertida.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void volver() {
        limpiarFormulario();
    }

    private void mostrarDetalle(Reserva reserva) {
        detalleReservaController.mostrar(reserva);
        BigDecimal total = reserva.getMontoTotal();
        BigDecimal adelanto = reserva.getAdelanto() == null ? BigDecimal.ZERO : reserva.getAdelanto();
        BigDecimal minimo = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP)
                .subtract(adelanto).max(BigDecimal.ZERO);
        lblAdelanto.setText(moneda(adelanto));
        lblMinimo.setText(moneda(minimo));
        txtMontoPago.setText(minimo.toPlainString());
    }

    private void limpiarFormulario() {
        txtBuscarReserva.clear();
        limpiarDetalle();
    }

    private void limpiarDetalle() {
        reservaSeleccionada = null;
        detalleReservaController.limpiar();
        lblAdelanto.setText("S/ 0.00");
        lblMinimo.setText("S/ 0.00");
        txtMontoPago.clear();
    }

    private String moneda(BigDecimal valor) {
        return String.format(Locale.US, "S/ %.2f", valor);
    }

}
