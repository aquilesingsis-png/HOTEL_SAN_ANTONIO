package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.servicio.UsuarioService;

public class CrearUsuarioController {
    @FXML private TextField txtNombres, txtApellidos, txtNombreDeUsuario;
    @FXML private PasswordField pwdContrasena, pwdConfirmarContrasena;
    @FXML private ComboBox<String> cmbRol, cmbEstado;
    @FXML private Label lblMensaje;
    private final UsuarioService servicio = new UsuarioService();

    @FXML private void initialize() {
        Task<java.util.List<String>> tarea = new Task<>() {
            @Override protected java.util.List<String> call() throws Exception { return servicio.listarRoles(); }
        };
        tarea.setOnSucceeded(e -> cmbRol.getItems().setAll(tarea.getValue()));
        tarea.setOnFailed(e -> lblMensaje.setText("No se pudieron cargar los roles."));
        ejecutar(tarea);
    }

    @FXML private void guardar() {
        if (!pwdContrasena.getText().equals(pwdConfirmarContrasena.getText())) {
            lblMensaje.setText("Las contraseñas no coinciden."); return;
        }
        String nombre = txtNombres.getText(), apellido = txtApellidos.getText();
        String login = txtNombreDeUsuario.getText(), clave = pwdContrasena.getText();
        String rol = cmbRol.getValue();
        boolean activo = !"Inactivo".equals(cmbEstado.getValue());
        Task<Integer> tarea = new Task<>() {
            @Override protected Integer call() throws Exception {
                return servicio.crear(nombre, apellido, login, clave, rol, activo);
            }
        };
        tarea.setOnSucceeded(e -> { limpiar(); lblMensaje.setText("Usuario creado (ID " + tarea.getValue() + ")."); });
        tarea.setOnFailed(e -> lblMensaje.setText(tarea.getException() instanceof IllegalArgumentException
                ? tarea.getException().getMessage() : "No se pudo crear el usuario. Revise si el nombre ya existe."));
        ejecutar(tarea);
    }

    @FXML private void limpiar() {
        txtNombres.clear(); txtApellidos.clear(); txtNombreDeUsuario.clear();
        pwdContrasena.clear(); pwdConfirmarContrasena.clear();
        cmbRol.setValue(null); cmbEstado.setValue("Activo");
    }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "usuario-db");
        hilo.setDaemon(true);
        hilo.start();
    }
}
