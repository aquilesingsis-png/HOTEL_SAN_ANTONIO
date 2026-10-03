package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.servicio.RecuperacionService;

public class EmisionRecuperacionController {
    @FXML private TextField txtUsuario, txtToken;
    @FXML private Label lblMensaje;
    private final RecuperacionService servicio = new RecuperacionService();

    @FXML private void emitir() {
        txtToken.clear();
        String usuario = txtUsuario.getText().trim();
        Task<String> tarea = new Task<>() {
            @Override protected String call() throws Exception { return servicio.emitirToken(usuario); }
        };
        tarea.setOnSucceeded(e -> {
            txtToken.setText(tarea.getValue());
            lblMensaje.setText("Token emitido. Entréguelo tras verificar la identidad; vence en 15 minutos.");
        });
        tarea.setOnFailed(e -> lblMensaje.setText(tarea.getException() instanceof IllegalArgumentException
                ? tarea.getException().getMessage() : "No se pudo emitir el token."));
        Thread hilo = new Thread(tarea, "emitir-token-recuperacion");
        hilo.setDaemon(true);
        hilo.start();
    }
}
