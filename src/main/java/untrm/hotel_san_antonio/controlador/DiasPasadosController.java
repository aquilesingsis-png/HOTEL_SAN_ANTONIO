package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.util.Duration;
import untrm.hotel_san_antonio.dao.DiasSinUsoDAO.ResumenDia;
import untrm.hotel_san_antonio.servicio.DiasSinUsoService;
import untrm.hotel_san_antonio.servicio.DiasSinUsoService.Estado;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Registro de días pasados: solo se activa cuando el sistema estuvo sin uso (por ejemplo, un corte de luz).
 * Se elige uno de esos días y se registran las ventas del carrito y las estadías que ocurrieron; al terminar,
 * se marca el día como completo. Hay un plazo de 24 horas, que el administrador puede ampliar con un motivo.
 */
public class DiasPasadosController {

    static final String RUTA_VENTA = "/untrm/hotel_san_antonio/fxml/nuevos/dias/venta_pasada_form.fxml";
    static final String RUTA_ESTADIA = "/untrm/hotel_san_antonio/fxml/Reserva/registro_historico.fxml";
    private static final DateTimeFormatter CORTO = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter LARGO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML private Label lblPlazo;
    @FXML private Label lblVence;
    @FXML private Label lblPendientes;
    @FXML private Label lblDias;
    @FXML private DatePicker dpDia;
    @FXML private Label lblResumen;
    @FXML private Label lblMensaje;
    @FXML private Button btnVenta;
    @FXML private Button btnEstadia;
    @FXML private Button btnCompleto;
    @FXML private Button btnReabrir;

    private final DiasSinUsoService servicio = new DiasSinUsoService();
    private Estado estado = new Estado(0, java.util.List.of(), null, false, 0, 0);
    private Set<LocalDate> pendientes = new HashSet<>();
    private Timeline refresco;

    @FXML
    public void initialize() {
        dpDia.setEditable(false);
        dpDia.setConverter(new javafx.util.converter.LocalDateStringConverter(LARGO, LARGO));
        dpDia.setDayCellFactory(selector -> new DateCell() {
            @Override
            public void updateItem(LocalDate fecha, boolean vacia) {
                super.updateItem(fecha, vacia);
                if (vacia || fecha == null) {
                    return;
                }
                boolean pendiente = pendientes.contains(fecha);
                setDisable(!pendiente);
                setStyle(pendiente
                        ? "-fx-background-color: #F5E6C8; -fx-text-fill: #3B210F; -fx-font-weight: bold;"
                        : "-fx-text-fill: #B9B0A3;");
            }
        });
        dpDia.valueProperty().addListener((obs, antes, ahora) -> {
            mostrarResumen();
            actualizarBotones();
        });
        refresco = new Timeline(new KeyFrame(Duration.seconds(60), e -> cargar()));
        refresco.setCycleCount(Timeline.INDEFINITE);
        dpDia.sceneProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                refresco.stop();
            } else {
                refresco.play();
            }
        });
        btnReabrir.setVisible(false);
        btnReabrir.setManaged(false);
        cargar();
    }

    // ------------------------------------------------------------------- estado

    private void cargar() {
        Task<Estado> tarea = new Task<>() {
            @Override protected Estado call() throws Exception { return servicio.estado(); }
        };
        tarea.setOnSucceeded(e -> aplicar(tarea.getValue()));
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudo consultar el estado del sistema."));
        ejecutar(tarea);
    }

    private void aplicar(Estado nuevo) {
        estado = nuevo;
        pendientes = new HashSet<>(nuevo.pendientes());
        lblPendientes.setText(String.valueOf(nuevo.pendientes().size()));
        lblDias.setText(nuevo.hayPendientes()
                ? nuevo.pendientes().stream().map(d -> d.format(CORTO)).collect(Collectors.joining("   "))
                : "Ninguno");
        if (nuevo.abierto()) {
            long horas = nuevo.minutosRestantes() / 60;
            lblPlazo.setText(horas + " h " + nuevo.minutosRestantes() % 60 + " min");
            lblVence.setText("Vence el " + nuevo.habilitaHasta().format(FECHA_HORA)
                    + (nuevo.reaperturas() > 0 ? "  ·  reabierto " + nuevo.reaperturas() + " vez/veces" : ""));
        } else if (nuevo.hayPendientes()) {
            lblPlazo.setText("Vencido");
            lblVence.setText(nuevo.puedeReabrir()
                    ? "El administrador puede reabrir el plazo."
                    : "No se puede ampliar más desde el sistema.");
        } else {
            lblPlazo.setText("—");
            lblVence.setText("No hay días pendientes de registrar.");
        }
        boolean puedeReabrir = nuevo.puedeReabrir() && SesionActual.esAdministrador();
        btnReabrir.setVisible(puedeReabrir);
        btnReabrir.setManaged(puedeReabrir);

        LocalDate elegido = dpDia.getValue();
        if (nuevo.hayPendientes() && (elegido == null || !pendientes.contains(elegido))) {
            dpDia.setValue(nuevo.pendientes().get(0));
        } else if (!nuevo.hayPendientes()) {
            dpDia.setValue(null);
        }
        mostrarResumen();
        actualizarBotones();
        PrincipalController.refrescarDiasPasados();
    }

    private void actualizarBotones() {
        boolean habilitado = estado.abierto() && dpDia.getValue() != null;
        btnVenta.setDisable(!habilitado);
        btnEstadia.setDisable(!habilitado);
        btnCompleto.setDisable(!habilitado);
    }

    private void mostrarResumen() {
        LocalDate dia = dpDia.getValue();
        if (dia == null) {
            lblResumen.setText("");
            return;
        }
        Task<ResumenDia> tarea = new Task<>() {
            @Override protected ResumenDia call() throws Exception { return servicio.resumenDia(dia); }
        };
        tarea.setOnSucceeded(e -> {
            ResumenDia r = tarea.getValue();
            lblResumen.setText(r.ventas() == 0 && r.pagos() == 0
                    ? "Todavía no se registró nada para este día."
                    : String.format(Locale.US, "Registrado para este día: %d venta(s) del carrito (S/ %.2f) y "
                            + "%d pago(s) de estadías (S/ %.2f).", r.ventas(), r.totalVentas(), r.pagos(), r.totalPagos()));
        });
        ejecutar(tarea);
    }

    // ------------------------------------------------------------------- acciones

    @FXML
    private void venta() {
        LocalDate dia = dpDia.getValue();
        if (dia == null) return;
        try {
            Navegacion.<VentaPasadaFormController>abrirModal(RUTA_VENTA, "Venta de un día pasado",
                    controlador -> controlador.iniciar(dia, this::mostrarResumen));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void estadia() {
        LocalDate dia = dpDia.getValue();
        if (dia == null) return;
        try {
            Navegacion.<RegistroHistoricoController>abrirModal(RUTA_ESTADIA, "Estadía de un día pasado",
                    controlador -> controlador.iniciar(dia, this::mostrarResumen));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void completo() {
        LocalDate dia = dpDia.getValue();
        if (dia == null) return;
        if (!Alertas.confirmar("Día completo", "¿Ya registró todo lo del " + dia.format(LARGO)
                + "? Después no podrá agregar nada a ese día.")) {
            return;
        }
        try {
            servicio.completarDia(dia);
            lblMensaje.setText("Día " + dia.format(LARGO) + " completado.");
            cargar();
        } catch (SQLException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        }
    }

    @FXML
    private void reabrir() {
        String motivo = Alertas.pedirTexto("Reabrir el plazo",
                "Escriba por qué hace falta ampliar el plazo 24 horas más (queda en el historial).",
                "Motivo de la reapertura");
        if (motivo == null) return;
        try {
            servicio.reabrir(motivo);
            lblMensaje.setText("Plazo reabierto por 24 horas.");
            cargar();
        } catch (SQLException | IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        }
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "dias-pasados-db");
        hilo.setDaemon(true);
        hilo.start();
    }
}
