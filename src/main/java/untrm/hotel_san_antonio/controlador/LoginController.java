package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ROL_RECEPCIONISTA = "RECEPCIONISTA";
    private static final PseudoClass ESTADO_ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass ESTADO_FOCO = PseudoClass.getPseudoClass("focused");

    @FXML private TextField txtUsuario;
    @FXML private ComboBox<String> cmbRol;
    @FXML private PasswordField txtContrasena;
    @FXML private TextField txtContrasenaVisible;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;
    @FXML private ToggleButton btnMostrarContrasena;
    @FXML private ProgressIndicator progresoLogin;
    @FXML private HBox contenedorUsuario;
    @FXML private HBox contenedorContrasena;
    @FXML private HBox contenedorRol;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    private void initialize() {
        cmbRol.setItems(FXCollections.observableArrayList("Administrador", "Recepcionista"));
        txtContrasenaVisible.textProperty().bindBidirectional(txtContrasena.textProperty());

        cmbRol.valueProperty().addListener((observable, anterior, actual) -> ocultarError());
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
        String rolSeleccionado = obtenerRolSeleccionado();

        if (rolSeleccionado == null) {
            mostrarError("Selecciona el tipo de usuario.", false, false, true);
            cmbRol.requestFocus();
            return;
        }

        if (usuario.isEmpty()) {
            mostrarError("Ingresa tu usuario.", true, false, false);
            txtUsuario.requestFocus();
            return;
        }

        if (contrasena.isEmpty()) {
            mostrarError("Ingresa tu contraseña.", false, true, false);
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
                txtContrasena.clear();
                mostrarError("Usuario o contraseña incorrectos.", true, true, false);
                campoContrasenaActivo().requestFocus();
                return;
            }

            String rolRegistrado = encontrado.getRol() == null
                    ? "" : encontrado.getRol().trim().toUpperCase();

            if (!ROL_ADMINISTRADOR.equals(rolRegistrado)
                    && !ROL_RECEPCIONISTA.equals(rolRegistrado)) {
                mostrarError("El usuario no tiene un rol autorizado.", true, true, true);
                return;
            }

            if (!rolSeleccionado.equals(rolRegistrado)) {
                txtContrasena.clear();
                mostrarError("Las credenciales no corresponden al rol seleccionado.",
                        true, true, true);
                campoContrasenaActivo().requestFocus();
                return;
            }

            try {
                SesionActual.iniciar(encontrado);
                Navegacion.irA(rutaSegunRol(rolRegistrado));
            } catch (IOException e) {
                SesionActual.cerrar();
                Alertas.mostrarError("Error", "No se pudo cargar la pantalla principal.\n\n"
                        + e.getMessage());
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

    private String obtenerRolSeleccionado() {
        String rol = cmbRol.getValue();
        return rol == null ? null : rol.trim().toUpperCase();
    }

    private String rutaSegunRol(String rol) {
        return ROL_ADMINISTRADOR.equals(rol)
                ? "/untrm/hotel_san_antonio/fxml/administrador.fxml"
                : "/untrm/hotel_san_antonio/fxml/recepcionista.fxml";
    }

    private void actualizarFocoContrasena() {
        contenedorContrasena.pseudoClassStateChanged(ESTADO_FOCO,
                txtContrasena.isFocused() || txtContrasenaVisible.isFocused());
    }

    private void mostrarError(String mensaje, boolean errorUsuario,
            boolean errorContrasena, boolean errorRol) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
        contenedorUsuario.pseudoClassStateChanged(ESTADO_ERROR, errorUsuario);
        contenedorContrasena.pseudoClassStateChanged(ESTADO_ERROR, errorContrasena);
        contenedorRol.pseudoClassStateChanged(ESTADO_ERROR, errorRol);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        contenedorUsuario.pseudoClassStateChanged(ESTADO_ERROR, false);
        contenedorContrasena.pseudoClassStateChanged(ESTADO_ERROR, false);
        contenedorRol.pseudoClassStateChanged(ESTADO_ERROR, false);
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
        cmbRol.setDisable(cargando);
    }
}
