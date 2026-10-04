package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.controlador.Reserva.HuespedesAdicionales;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ConflictoFechasException;
import untrm.hotel_san_antonio.servicio.ReniecService;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.ConsultaApi;
import untrm.hotel_san_antonio.util.SoloLectura;
import untrm.hotel_san_antonio.util.Validador;

/** Captura administrativa de una estadía pasada, con fechas del hecho y de captura separadas. */
public class RegistroHistoricoController {
    @FXML private ComboBox<String> cmbHabitacion, cmbMetodo;
    @FXML private DatePicker dpIngreso, dpSalida, dpEvento, dpPago;
    @FXML private Spinner<Integer> spPersonas;
    @FXML private VBox contenedorAcompanantes;
    @FXML private TextField txtDni, txtNombres, txtApellidos, txtTotal;
    @FXML private TextArea txtMotivo;
    @FXML private Label lblEstado;
    @FXML private Button btnGuardar;
    private final ReservaService servicio = new ReservaService();
    private final Map<String,Habitacion> habitaciones = new HashMap<>();
    private HuespedesAdicionales acompanantes;
    private String dniBuscado;
    private Runnable alGuardar;

    @FXML private void initialize() {
        acompanantes = new HuespedesAdicionales(contenedorAcompanantes);
        spPersonas.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1, 1));
        spPersonas.valueProperty().addListener((obs, antes, ahora) -> acompanantes.actualizar(ahora));
        acompanantes.actualizar(1);
        cmbMetodo.getItems().setAll("EFECTIVO", "YAPE", "TRANSFERENCIA", "TARJETA");
        cmbMetodo.setValue("EFECTIVO");
        cmbHabitacion.valueProperty().addListener((obs, antes, ahora) -> {
            Habitacion h = habitaciones.get(ahora);
            if (h != null) {
                spPersonas.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1, h.getTipo().getCapacidad(), 1));
                acompanantes.actualizar(1);
            }
            recalcular();
        });
        dpIngreso.valueProperty().addListener((obs, antes, ahora) -> recalcular());
        dpSalida.valueProperty().addListener((obs, antes, ahora) -> recalcular());
        txtDni.textProperty().addListener((obs, antes, ahora) -> {
            if (!ahora.equals(dniBuscado)) {
                dniBuscado = null;
                bloquearIdentidad(false);
            }
        });
        cargarHabitaciones();
    }

    /**
     * Abre el formulario para un día sin uso: la fecha del hecho y la del pago quedan fijas en ese día.
     *
     * @param alGuardar se ejecuta al registrar la estadía, para actualizar el resumen del día
     */
    public void iniciar(LocalDate dia, Runnable alGuardar) {
        this.alGuardar = alGuardar;
        dpEvento.setValue(dia);
        dpPago.setValue(dia);
        dpEvento.setDisable(true);
        dpPago.setDisable(true);
    }

    @FXML private void cargarHabitaciones() {
        Task<List<Habitacion>> tarea = new Task<>() {
            @Override protected List<Habitacion> call() throws Exception { return servicio.listarHabitaciones(); }
        };
        tarea.setOnSucceeded(e -> {
            habitaciones.clear(); cmbHabitacion.getItems().clear();
            for (Habitacion h : tarea.getValue()) {
                String nombre = h.getNumero() + " · " + h.getTipo().getNombre()
                        + " · capacidad " + h.getTipo().getCapacidad();
                habitaciones.put(nombre, h); cmbHabitacion.getItems().add(nombre);
            }
        });
        tarea.setOnFailed(e -> lblEstado.setText("No se pudieron cargar las habitaciones."));
        ejecutar(tarea, "historico-habitaciones");
    }

    @FXML private void buscarDni() {
        String dni = txtDni.getText().trim();
        if (!Validador.esDniValido(dni)) { lblEstado.setText("Ingrese un DNI válido."); return; }
        Task<Huesped> local = new Task<>() {
            @Override protected Huesped call() throws Exception { return servicio.buscarHuespedLocal("DNI", dni); }
        };
        local.setOnSucceeded(e -> {
            if (!dni.equals(txtDni.getText().trim())) return;
            if (local.getValue() != null) {
                cargar(local.getValue()); dniBuscado = dni;
                bloquearIdentidad(true);
                lblEstado.setText("Huésped local cargado. Verificando identidad...");
                ConsultaApi.actualizarIdentidadLocal(dni, actualizado -> {
                    if (!dni.equals(txtDni.getText().trim())) return;
                    cargar(actualizado);
                    lblEstado.setText("Identidad verificada con RENIEC.");
                });
            } else {
                Task<Huesped> reniec = ReniecService.consultarDni(dni);
                reniec.setOnSucceeded(ev -> {
                    if (!dni.equals(txtDni.getText().trim())) return;
                    if (reniec.getValue() != null) cargar(reniec.getValue());
                    bloquearIdentidad(reniec.getValue() != null);
                    dniBuscado = dni;
                    lblEstado.setText(reniec.getValue() == null
                            ? "Complete los nombres manualmente." : "Identidad autocompletada.");
                });
                reniec.setOnFailed(ev -> {
                    dniBuscado = dni;
                    lblEstado.setText("RENIEC no disponible. Complete los nombres manualmente.");
                });
                ejecutar(reniec, "historico-reniec");
            }
        });
        local.setOnFailed(e -> lblEstado.setText("No se pudo buscar el huésped local."));
        ejecutar(local, "historico-dni");
    }

    /** Nombres y apellidos de RENIEC o de la base no se editan; si no hay ninguno de los dos, se escriben a mano. */
    private void bloquearIdentidad(boolean bloqueada) {
        SoloLectura.aplicar(txtNombres, bloqueada);
        SoloLectura.aplicar(txtApellidos, bloqueada);
    }

    private void cargar(Huesped h) {
        txtNombres.setText(h.getNombres()); txtApellidos.setText(h.getApellidos());
    }

    private void recalcular() {
        Habitacion h = habitaciones.get(cmbHabitacion.getValue());
        LocalDate desde = dpIngreso.getValue(), hasta = dpSalida.getValue();
        if (h == null || desde == null || hasta == null || !hasta.isAfter(desde)) {
            txtTotal.clear(); return;
        }
        txtTotal.setText(h.getTipo().getPrecioBase()
                .multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(desde, hasta))).toPlainString());
    }

    @FXML private void guardar() {
        Habitacion h = habitaciones.get(cmbHabitacion.getValue());
        if (h == null || !txtDni.getText().trim().equals(dniBuscado)) {
            lblEstado.setText("Seleccione una habitación y busque el DNI titular."); return;
        }
        List<Huesped> personas;
        try {
            personas = acompanantes.obtener(new Huesped("DNI", dniBuscado,
                    txtNombres.getText().trim(), txtApellidos.getText().trim(), "Perú", null, null));
        } catch (RuntimeException error) { lblEstado.setText(error.getMessage()); return; }
        Reserva r = new Reserva();
        r.setIdHabitacion(h.getIdHabitacion()); r.setFechaCheckin(dpIngreso.getValue());
        r.setFechaCheckout(dpSalida.getValue()); r.setNumHuespedes(personas.size());
        r.setCanal("PRESENCIAL");
        try { r.setMontoTotal(new BigDecimal(txtTotal.getText())); }
        catch (RuntimeException error) { lblEstado.setText("Seleccione fechas válidas."); return; }
        Pago p = new Pago(); p.setMonto(r.getMontoTotal()); p.setMetodoPago(cmbMetodo.getValue());
        LocalDate evento = dpEvento.getValue(), pagoReal = dpPago.getValue();
        String motivo = txtMotivo.getText();
        btnGuardar.setDisable(true);
        Task<Integer> tarea = new Task<>() {
            @Override protected Integer call() throws Exception {
                return servicio.registrarHistorica(personas, null, r, p, evento, pagoReal, motivo);
            }
        };
        tarea.setOnSucceeded(e -> {
            lblEstado.setText("Estadía histórica R-" + String.format("%04d", tarea.getValue())
                    + " registrada con fecha real y auditoría.");
            btnGuardar.setDisable(false);
            if (alGuardar != null) alGuardar.run();
        });
        tarea.setOnFailed(e -> {
            btnGuardar.setDisable(false);
            Throwable error = tarea.getException();
            lblEstado.setText(error instanceof IllegalArgumentException || error instanceof IllegalStateException
                    ? error.getMessage() : "No se pudo registrar la estadía histórica.");
        });
        ejecutar(tarea, "historico-guardar");
    }

    private void ejecutar(Task<?> tarea, String nombre) {
        Thread hilo = new Thread(tarea, nombre); hilo.setDaemon(true); hilo.start();
    }
}
