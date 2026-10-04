package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.Pago;
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
    @FXML private Label lblResumenPagos;
    @FXML private Label lblPagos;
    @FXML private Label lblAvisoCobro;
    @FXML private Label lblAvisoCancelacion;
    @FXML private HBox boxCobro;
    @FXML private ChoiceBox<String> cbMetodoCobro;
    @FXML private TextField txtMontoCobro;
    @FXML private Button btnCobrar;

    private final ReservaService reservaService = new ReservaService();
    private Reserva reserva;
    private Runnable alCambiar;
    private BigDecimal pagado = BigDecimal.ZERO;
    private BigDecimal saldo = BigDecimal.ZERO;

    @FXML
    public void initialize() {
        cbMotivo.getItems().setAll("El cliente desistió", "Cambio de fecha", "Error en la reserva",
                "Falta de pago", "Duplicidad de reserva", "Otro");
        cbMetodoCobro.getItems().setAll("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE");
        cbMetodoCobro.setValue("EFECTIVO");
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
        cargarPagos();
    }

    /** Lista los pagos de la reserva y calcula cuanto falta por cobrar. */
    private void cargarPagos() {
        int idReserva = reserva.getIdReserva();
        Task<List<Pago>> tarea = new Task<>() {
            @Override protected List<Pago> call() throws Exception {
                return reservaService.listarPagos(idReserva);
            }
        };
        tarea.setOnSucceeded(e -> mostrarPagos(tarea.getValue()));
        tarea.setOnFailed(e -> lblResumenPagos.setText("No se pudieron cargar los pagos."));
        ConsultaApi.iniciar(tarea);
    }

    private void mostrarPagos(List<Pago> pagos) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        BigDecimal total = reserva.getMontoTotal() == null ? BigDecimal.ZERO : reserva.getMontoTotal();
        pagado = pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        saldo = total.subtract(pagado).max(BigDecimal.ZERO);
        boolean cancelada = "CANCELADA".equals(reserva.getEstado());
        lblResumenPagos.setText(cancelada
                ? String.format(Locale.US, "Total S/ %.2f  ·  Adelanto retenido por cancelación S/ %.2f", total, pagado)
                : String.format(Locale.US, "Total S/ %.2f  ·  Pagado S/ %.2f  ·  Saldo S/ %.2f", total, pagado, saldo));
        lblPagos.setText(pagos.isEmpty() ? "Todavía no hay pagos registrados." : pagos.stream()
                .map(p -> p.getFechaPago().format(formato) + "  ·  " + p.getMetodoPago() + "  ·  "
                        + p.getTipoPago() + "  ·  " + String.format(Locale.US, "S/ %.2f", p.getMonto())
                        + (cancelada ? "  (retenido)" : ""))
                .collect(java.util.stream.Collectors.joining("\n")));

        boolean confirmada = "CONFIRMADA".equals(reserva.getEstado());
        boolean puedeCobrar = confirmada && saldo.signum() > 0;
        boxCobro.setVisible(puedeCobrar);
        boxCobro.setManaged(puedeCobrar);
        if (puedeCobrar) {
            txtMontoCobro.setText(saldo.toPlainString());
        }
        if (confirmada) {
            lblAvisoCobro.setText(saldo.signum() > 0 ? "" : "La reserva está pagada por completo.");
        } else if ("CHECKIN".equals(reserva.getEstado()) && saldo.signum() > 0) {
            lblAvisoCobro.setText("El huésped ya está alojado: el saldo se cobra en la cuenta de la habitación (Habitaciones).");
        } else {
            lblAvisoCobro.setText("");
        }
        if (cancelada) {
            lblAvisoCancelacion.setText("");
            return;
        }
        lblAvisoCancelacion.setText(pagado.signum() > 0
                ? String.format(Locale.US, "El adelanto pagado (S/ %.2f) NO se devuelve.", pagado) : "");
    }

    @FXML
    private void cobrarSaldo() {
        BigDecimal monto;
        try {
            monto = new BigDecimal(txtMontoCobro.getText().trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            lblMensaje.setText("Ingrese un monto válido.");
            return;
        }
        try {
            BigDecimal queda = reservaService.cobrarSaldo(reserva.getIdReserva(), cbMetodoCobro.getValue(), monto);
            lblMensaje.setText("");
            Alertas.mostrarInfo("Cobro registrado", String.format(Locale.US,
                    "Se cobraron S/ %.2f. Saldo pendiente: S/ %.2f.", monto, queda));
            cargarPagos();
            if (alCambiar != null) {
                alCambiar.run();
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo registrar el cobro.\n\n" + error.getMessage());
        }
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
        String aviso = pagado.signum() > 0
                ? String.format(Locale.US, "\n\nEl adelanto pagado (S/ %.2f) NO se devuelve.", pagado) : "";
        if (!Alertas.confirmar("Cancelar reserva", "¿Cancelar la reserva " + reserva.getCodigo()
                + " de " + reserva.getNombreHuesped() + "?" + aviso)) {
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
