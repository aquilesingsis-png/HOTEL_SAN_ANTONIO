package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.servicio.RecuperacionService;

/**
 * Ventana del administrador para restablecer el acceso de un usuario: genera un código de un solo uso
 * que el empleado escribe en «Recuperar contraseña» de la pantalla de ingreso.
 */
public class EmisionRecuperacionController {
    @FXML private Label lblUsuario;
    @FXML private TextField txtToken;
    @FXML private Button btnGenerar;
    @FXML private Button btnCopiar;
    @FXML private Label lblMensaje;
    private final RecuperacionService servicio = new RecuperacionService();
    private String nombreUsuario;

    public void iniciar(Usuario usuario) {
        this.nombreUsuario = usuario.getUsuario();
        lblUsuario.setText(usuario.getNombreCompleto() + "  (" + usuario.getUsuario() + ")");
    }

    @FXML private void emitir() {
        txtToken.clear();
        btnCopiar.setDisable(true);
        btnGenerar.setDisable(true);
        Task<String> tarea = new Task<>() {
            @Override protected String call() throws Exception { return servicio.emitirToken(nombreUsuario); }
        };
        tarea.setOnSucceeded(e -> {
            txtToken.setText(tarea.getValue());
            btnCopiar.setDisable(false);
            btnGenerar.setDisable(false);
            lblMensaje.setText("Código generado. Entréguelo solo después de verificar la identidad; vence en 15 minutos.");
        });
        tarea.setOnFailed(e -> {
            btnGenerar.setDisable(false);
            lblMensaje.setText(tarea.getException() instanceof IllegalArgumentException
                    ? tarea.getException().getMessage() : "No se pudo generar el código.");
        });
        Thread hilo = new Thread(tarea, "emitir-token-recuperacion");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML private void copiar() {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(txtToken.getText());
        Clipboard.getSystemClipboard().setContent(contenido);
        lblMensaje.setText("Código copiado.");
    }

    @FXML private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
