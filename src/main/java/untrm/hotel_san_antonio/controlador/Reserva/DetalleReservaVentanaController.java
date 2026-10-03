package untrm.hotel_san_antonio.controlador.Reserva;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.ConsultaApi;
import untrm.hotel_san_antonio.util.Navegacion;

/**
 * Ventana emergente con el detalle de una reserva (se abre desde el Calendario y desde la Lista).
 * Desde aqui se puede cancelar la reserva o cambiar de habitacion.
 */
public class DetalleReservaVentanaController {

    private static final String RUTA_CAMBIO = "/untrm/hotel_san_antonio/fxml/Reserva/cambiar_habitacion.fxml";

    @FXML private DetalleReservaController detalleReservaController;
    @FXML private Label lblHuespedes;
    @FXML private Label lblMensaje;
    @FXML private VBox boxCancelar;
    @FXML private ChoiceBox<String> cbMotivo;
    @FXML private TextArea txtDetalle;
    @FXML private Button btnCambiar;
    @FXML private Button btnCancelar;

    private final ReservaService reservaService = new ReservaService();
    private Reserva reserva;
    private Runnable alCambiar;

    @FXML
    public void initialize() {
        cbMotivo.getItems().setAll("El cliente desistió", "Cambio de fecha", "Error en la reserva",
                "Falta de pago", "Duplicidad de reserva", "Otro");
    }

    /** @param alCambiar se ejecuta cuando la reserva se cancela o cambia de habitacion (para recargar la pantalla) */
    public void iniciar(Reserva reservaElegida, Runnable alCambiar) {
        this.alCambiar = alCambiar;
        this.reserva = reservaElegida;
        try {
            // el calendario solo trae lo basico: se pide la reserva completa por su codigo
            Reserva completa = reservaService.buscarUno(reservaElegida.getCodigo());
            if (completa != null) {
                this.reserva = completa;
            }
        } catch (IllegalArgumentException | SQLException error) {
            // se muestra con los datos que ya se tenian
        }
        detalleReservaController.mostrar(reserva);
        btnCancelar.setDisable(!("PENDIENTE".equals(reserva.getEstado()) || "CONFIRMADA".equals(reserva.getEstado())));
        btnCambiar.setDisable(!("CONFIRMADA".equals(reserva.getEstado()) || "CHECKIN".equals(reserva.getEstado())));
        cargarHuespedes();
    }

    private void cargarHuespedes() {
        int idReserva = reserva.getIdReserva();
        Task<List<Huesped>> tarea = new Task<>() {
            @Override protected List<Huesped> call() throws Exception {
                return reservaService.listarHuespedes(idReserva);
            }
        };
        tarea.setOnSucceeded(e -> lblHuespedes.setText(tarea.getValue().stream()
                .map(h -> h.getNombres() + " " + h.getApellidos() + " (" + h.getNumDocumento() + ")")
                .collect(java.util.stream.Collectors.joining("\n"))));
        tarea.setOnFailed(e -> lblHuespedes.setText("No se pudieron cargar los huéspedes."));
        ConsultaApi.iniciar(tarea);
    }

    @FXML
    private void mostrarCancelacion() {
        boxCancelar.setVisible(true);
        boxCancelar.setManaged(true);
        btnCancelar.setDisable(true);
    }

    @FXML
    private void volverDeCancelar() {
        boxCancelar.setVisible(false);
        boxCancelar.setManaged(false);
        btnCancelar.setDisable(false);
        lblMensaje.setText("");
    }

    @FXML
    private void confirmarCancelacion() {
        if (cbMotivo.getValue() == null) {
            lblMensaje.setText("Seleccione un motivo de cancelación.");
            return;
        }
        if (!Alertas.confirmar("Cancelar reserva", "¿Cancelar la reserva " + reserva.getCodigo()
                + " de " + reserva.getNombreHuesped() + "?")) {
            return;
        }
        try {
            reservaService.cancelar(reserva.getIdReserva(), cbMotivo.getValue(), txtDetalle.getText());
            Alertas.mostrarInfo("Cancelación registrada", "La reserva " + reserva.getCodigo() + " quedó cancelada.");
            notificarYCerrar();
        } catch (IllegalArgumentException | IllegalStateException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cancelar la reserva.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cambiarHabitacion() {
        try {
            Navegacion.<CambiarHabitacionController>abrirModal(RUTA_CAMBIO, "Cambiar habitación",
                    controlador -> controlador.iniciar(reserva, this::notificarYCerrar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el cambio de habitación.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }

    private void notificarYCerrar() {
        if (alCambiar != null) {
            alCambiar.run();
        }
        cerrar();
    }
}
