package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.servicio.RecuperacionService;

public class RecuperacionController {
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtToken, txtNueva, txtConfirmar;
    @FXML private Label lblMensaje;
    private final RecuperacionService servicio = new RecuperacionService();

    @FXML private void restablecer() {
        if (!txtNueva.getText().equals(txtConfirmar.getText())) {
            lblMensaje.setText("Las nuevas contraseñas no coinciden.");
            return;
        }
        String usuario = txtUsuario.getText();
        String token = txtToken.getText();
        String nueva = txtNueva.getText();
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                servicio.restablecer(usuario, token, nueva);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            lblMensaje.setText("Contraseña actualizada. Ya puede iniciar sesión.");
            txtToken.clear(); txtNueva.clear(); txtConfirmar.clear();
        });
        tarea.setOnFailed(e -> lblMensaje.setText(tarea.getException() instanceof IllegalArgumentException
                ? tarea.getException().getMessage() : "No se pudo restablecer la contraseña."));
        Thread hilo = new Thread(tarea, "restablecer-contrasena");
        hilo.setDaemon(true);
        hilo.start();
    }
}
