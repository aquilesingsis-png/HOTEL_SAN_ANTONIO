package untrm.hotel_san_antonio.controlador.Reserva;

import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;

public class CancelarReservaController {

    @FXML private TextField txtBuscarReserva;
    @FXML private DetalleReservaController detalleReservaController;
    @FXML private ChoiceBox<String> cbMotivo;
    @FXML private TextArea txtDetalle;

    private final ReservaService reservaService = new ReservaService();
    private Reserva reservaSeleccionada;

    @FXML
    public void initialize() {
        cbMotivo.getItems().setAll("El cliente desistió", "Cambio de fecha", "Error en la reserva",
                "Falta de pago", "Duplicidad de reserva", "Otro");
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
    private void cancelarReserva() {
        if (reservaSeleccionada == null) {
            Alertas.mostrarAdvertencia("Reserva requerida", "Primero busque una reserva.");
            return;
        }
        try {
            reservaService.cancelar(reservaSeleccionada.getIdReserva(), cbMotivo.getValue(), txtDetalle.getText());
            Alertas.mostrarInfo("Cancelación registrada",
                    "La reserva " + reservaSeleccionada.getCodigo() + " quedó cancelada.");
            limpiarFormulario();
        } catch (IllegalArgumentException | IllegalStateException error) {
            Alertas.mostrarAdvertencia("No se puede cancelar", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cancelar la reserva.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void volver() {
        limpiarFormulario();
    }

    private void mostrarDetalle(Reserva reserva) {
        detalleReservaController.mostrar(reserva);
    }

    private void limpiarFormulario() {
        txtBuscarReserva.clear();
        limpiarDetalle();
    }

    private void limpiarDetalle() {
        reservaSeleccionada = null;
        detalleReservaController.limpiar();
        cbMotivo.setValue(null);
        txtDetalle.clear();
    }

}
