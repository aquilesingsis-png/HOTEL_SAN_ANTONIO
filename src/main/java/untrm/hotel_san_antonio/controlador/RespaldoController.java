package untrm.hotel_san_antonio.controlador;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import untrm.hotel_san_antonio.servicio.RespaldoService;
import untrm.hotel_san_antonio.servicio.RespaldoService.Resultado;
import untrm.hotel_san_antonio.servicio.RespaldoService.UltimoRespaldo;
import untrm.hotel_san_antonio.util.Alertas;

/**
 * Respaldo: el administrador guarda una copia de toda la base de datos en un archivo .sql, en la carpeta que
 * elija (mejor un USB o la nube, no la misma computadora). Restaurar se hace a mano desde phpMyAdmin.
 */
public class RespaldoController {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int DIAS_RECOMENDADOS = 7;

    @FXML private Label lblUltimo;
    @FXML private Button btnCrear;
    @FXML private ProgressBar barra;
    @FXML private Label lblEstado;
    @FXML private VBox cajaResultado;
    @FXML private Label lblArchivo;
    @FXML private Label lblDetalle;

    private final RespaldoService servicio = new RespaldoService();

    @FXML
    public void initialize() {
        cajaResultado.setVisible(false);
        cajaResultado.setManaged(false);
        barra.setVisible(false);
        mostrarUltimo();
    }

    @FXML
    private void crear() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar respaldo de la base de datos");
        selector.setInitialFileName(RespaldoService.nombreSugerido());
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo SQL", "*.sql"));
        File elegido = selector.showSaveDialog(btnCrear.getScene().getWindow());
        if (elegido == null) {
            return;
        }
        File archivo = elegido.getName().toLowerCase(Locale.ROOT).endsWith(".sql")
                ? elegido : new File(elegido.getParentFile(), elegido.getName() + ".sql");

        btnCrear.setDisable(true);
        cajaResultado.setVisible(false);
        cajaResultado.setManaged(false);
        barra.setVisible(true);
        lblEstado.setText("Preparando el respaldo…");
        Task<Resultado> tarea = new Task<>() {
            @Override protected Resultado call() throws Exception {
                return servicio.crear(archivo.toPath(), this::updateMessage);
            }
        };
        lblEstado.textProperty().bind(tarea.messageProperty());
        tarea.setOnSucceeded(e -> {
            terminar();
            Resultado r = tarea.getValue();
            lblEstado.setText("Respaldo terminado.");
            lblArchivo.setText(r.archivo().toAbsolutePath().toString());
            lblDetalle.setText(r.tablas() + " tablas · " + r.filas() + " registros · " + tamano(r.bytes()));
            cajaResultado.setVisible(true);
            cajaResultado.setManaged(true);
            mostrarUltimo();
        });
        tarea.setOnFailed(e -> {
            terminar();
            lblEstado.setText("No se pudo crear el respaldo.");
            Throwable causa = tarea.getException();
            Alertas.mostrarError("Respaldo", "No se pudo crear el respaldo.\n\n"
                    + (causa == null ? "" : causa.getMessage()));
        });
        Thread hilo = new Thread(tarea, "respaldo-bd");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void terminar() {
        lblEstado.textProperty().unbind();
        barra.setVisible(false);
        btnCrear.setDisable(false);
    }

    /** Dice cuándo fue el último respaldo y avisa si ya pasó una semana (o si nunca se hizo). */
    private void mostrarUltimo() {
        try {
            UltimoRespaldo ultimo = servicio.ultimo();
            if (ultimo == null) {
                lblUltimo.setText("Todavía no se hizo ningún respaldo desde el sistema.");
                lblUltimo.setStyle("-fx-text-fill: #B3261E; -fx-font-weight: bold;");
                return;
            }
            long dias = ChronoUnit.DAYS.between(ultimo.fecha().toLocalDate(), LocalDate.now());
            String cuando = dias == 0 ? "hoy" : dias == 1 ? "ayer" : "hace " + dias + " días";
            lblUltimo.setText("Último respaldo: " + ultimo.fecha().format(FORMATO) + " (" + cuando + ") por "
                    + ultimo.usuario() + (dias >= DIAS_RECOMENDADOS ? ". Se recomienda respaldar cada semana." : "."));
            lblUltimo.setStyle(dias >= DIAS_RECOMENDADOS ? "-fx-text-fill: #B26A00; -fx-font-weight: bold;"
                    : "-fx-text-fill: #1F7A3E; -fx-font-weight: bold;");
        } catch (SQLException | SecurityException error) {
            lblUltimo.setText("No se pudo consultar el último respaldo.");
        }
    }

    private String tamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
