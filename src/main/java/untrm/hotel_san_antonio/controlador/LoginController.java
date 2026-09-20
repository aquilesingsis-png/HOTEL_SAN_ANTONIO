package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import untrm.hotel_san_antonio.dao.UsuarioDAO;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.PasswordUtil;
import untrm.hotel_san_antonio.util.SesionActual;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {

    private static final PseudoClass ESTADO_ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass ESTADO_FOCO = PseudoClass.getPseudoClass("focused");

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtContrasena;
    @FXML private TextField txtContrasenaVisible;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;
    @FXML private ToggleButton btnMostrarContrasena;
    @FXML private ProgressIndicator progresoLogin;
    @FXML private HBox contenedorUsuario;
    @FXML private HBox contenedorContrasena;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    private void initialize() {
        txtContrasenaVisible.textProperty().bindBidirectional(txtContrasena.textProperty());

        txtUsuario.textProperty().addListener((observable, anterior, actual) -> ocultarError());
        txtContrasena.textProperty().addListener((observable, anterior, actual) -> ocultarError());
        txtUsuario.focusedProperty().addListener((observable, anterior, actual) ->
                contenedorUsuario.pseudoClassStateChanged(ESTADO_FOCO, actual));
        txtContrasena.focusedProperty().addListener((observable, anterior, actual) ->
                actualizarFocoContrasena());
        txtContrasenaVisible.focusedProperty().addListener((observable, anterior, actual) ->
                actualizarFocoContrasena());
    }

    @FXML
    private void onIngresar() {
        ocultarError();

        String usuario = txtUsuario.getText().trim();
        String contrasena = txtContrasena.getText();

        if (usuario.isEmpty()) {
            mostrarError("Ingresa tu usuario.", true, false);
            txtUsuario.requestFocus();
            return;
        }

        if (contrasena.isEmpty()) {
            mostrarError("Ingresa tu contraseña.", false, true);
            campoContrasenaActivo().requestFocus();
            return;
        }

        cambiarEstadoCarga(true);

        Task<Usuario> tareaAutenticacion = new Task<Usuario>() {
            @Override
            protected Usuario call() throws SQLException {
                Usuario encontrado = usuarioDAO.buscarPorUsuario(usuario);
                if (encontrado == null
                        || !PasswordUtil.coincide(contrasena, encontrado.getContrasenaHash())) {
                    return null;
                }
                return encontrado;
            }
        };

        tareaAutenticacion.setOnSucceeded(evento -> {
            cambiarEstadoCarga(false);
            Usuario encontrado = tareaAutenticacion.getValue();

            if (encontrado == null) {
                mostrarError("Usuario o contraseña incorrectos.", true, true);
                txtContrasena.clear();
                campoContrasenaActivo().requestFocus();
                return;
            }

            if (!"ADMINISTRADOR".equals(encontrado.getRol())
                    && !"RECEPCIONISTA".equals(encontrado.getRol())) {
                mostrarError("El usuario no tiene un rol autorizado.", true, true);
                return;
            }

            try {
                SesionActual.iniciar(encontrado);
                Navegacion.irA("/untrm/hotel_san_antonio/fxml/dashboard.fxml");
            } catch (IOException e) {
                SesionActual.cerrar();
                Alertas.mostrarError("Error", "No se pudo cargar el Dashboard.\n\n" + e.getMessage());
            }
        });

        tareaAutenticacion.setOnFailed(evento -> {
            cambiarEstadoCarga(false);
            Throwable error = tareaAutenticacion.getException();
            Alertas.mostrarError("Error de conexión",
                    "No se pudo conectar a la base de datos. Verifica que MySQL/XAMPP esté encendido.\n\n"
                            + (error == null ? "Error desconocido." : error.getMessage()));
        });

        Thread hilo = new Thread(tareaAutenticacion, "autenticacion-usuario");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void onMostrarContrasena() {
        boolean mostrar = btnMostrarContrasena.isSelected();

        txtContrasena.setManaged(!mostrar);
        txtContrasena.setVisible(!mostrar);
        txtContrasenaVisible.setManaged(mostrar);
        txtContrasenaVisible.setVisible(mostrar);
        btnMostrarContrasena.setText(mostrar ? "Ocultar" : "Ver");

        TextField campoActivo = campoContrasenaActivo();
        campoActivo.requestFocus();
        campoActivo.positionCaret(campoActivo.getText().length());
    }

    private TextField campoContrasenaActivo() {
        return btnMostrarContrasena.isSelected() ? txtContrasenaVisible : txtContrasena;
    }

    private void actualizarFocoContrasena() {
        contenedorContrasena.pseudoClassStateChanged(ESTADO_FOCO,
                txtContrasena.isFocused() || txtContrasenaVisible.isFocused());
    }

    private void mostrarError(String mensaje, boolean errorUsuario, boolean errorContrasena) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
        contenedorUsuario.pseudoClassStateChanged(ESTADO_ERROR, errorUsuario);
        contenedorContrasena.pseudoClassStateChanged(ESTADO_ERROR, errorContrasena);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        contenedorUsuario.pseudoClassStateChanged(ESTADO_ERROR, false);
        contenedorContrasena.pseudoClassStateChanged(ESTADO_ERROR, false);
    }

    private void cambiarEstadoCarga(boolean cargando) {
        btnIngresar.setDisable(cargando);
        btnIngresar.setText(cargando ? "" : "INGRESAR");
        progresoLogin.setVisible(cargando);
        progresoLogin.setManaged(cargando);
        txtUsuario.setDisable(cargando);
        txtContrasena.setDisable(cargando);
        txtContrasenaVisible.setDisable(cargando);
        btnMostrarContrasena.setDisable(cargando);
    }
}
