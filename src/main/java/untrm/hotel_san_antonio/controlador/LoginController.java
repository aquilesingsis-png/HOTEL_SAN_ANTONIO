package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import untrm.hotel_san_antonio.dao.UsuarioDAO;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloUtil;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.PasswordUtil;
import untrm.hotel_san_antonio.util.SesionActual;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Login. Sin archivo .css: el fondo, los "campo" (normal/foco/error) y los botones
 * (pasar el mouse/presionado) se resuelven aqui con setStyle(), ya que esos estados
 * dependen de lo que hace la persona y no se pueden fijar de antemano en el FXML.
 */
public class LoginController {

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ROL_RECEPCIONISTA = "RECEPCIONISTA";

    // "campo" (los contenedores de rol/usuario/contraseña)
    private static final String CAMPO_BASE = "-fx-border-width: 1.2px; -fx-border-radius: 11px; "
            + "-fx-background-radius: 11px; -fx-padding: 6px 12px; -fx-min-height: 54px;";
    private static final String CAMPO_NORMAL = "-fx-background-color: #FBFBFB; -fx-border-color: #D9D9D9; " + CAMPO_BASE;
    private static final String CAMPO_FOCO = "-fx-background-color: #FBFBFB; -fx-border-color: #B8862D; " + CAMPO_BASE
            + " -fx-effect: dropshadow(gaussian, rgba(184,134,45,0.18), 8, 0.1, 0, 0);";
    private static final String CAMPO_ERROR = "-fx-background-color: #FFF8F6; -fx-border-color: #B64832; " + CAMPO_BASE;

    // boton "Ver"/"Ocultar" contraseña
    private static final String BTN_VER = "-fx-background-color: transparent; -fx-padding: 7px 4px; "
            + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #B8862D; -fx-cursor: hand;";
    private static final String BTN_VER_ACTIVO = "-fx-background-color: transparent; -fx-padding: 7px 4px; "
            + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #3B210F; -fx-cursor: hand;";

    // boton "INGRESAR"
    private static final String BTN_INGRESAR = "-fx-background-color: linear-gradient(to right, #A96F16, #CB8F2C); "
            + "-fx-background-radius: 11px; -fx-padding: 14px 24px; -fx-font-size: 15px; -fx-font-weight: bold; "
            + "-fx-text-fill: white; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(90,53,22,0.25), 9, 0.12, 0, 3);";
    private static final String BTN_INGRESAR_HOVER = "-fx-background-color: linear-gradient(to right, #BC7F1D, #D9A03C); "
            + "-fx-background-radius: 11px; -fx-padding: 14px 24px; -fx-font-size: 15px; -fx-font-weight: bold; "
            + "-fx-text-fill: white; -fx-cursor: hand;";

    @FXML private StackPane rootLogin;
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

    // se necesita recordar el error de cada campo para no perderlo cuando solo cambia el foco
    private boolean errorUsuario, errorContrasena, errorRol;

    @FXML
    private void initialize() {
        aplicarFondo();

        cmbRol.setItems(FXCollections.observableArrayList("Administrador", "Recepcionista"));
        txtContrasenaVisible.textProperty().bindBidirectional(txtContrasena.textProperty());

        cmbRol.valueProperty().addListener((observable, anterior, actual) -> ocultarError());
        txtUsuario.textProperty().addListener((observable, anterior, actual) -> ocultarError());
        txtContrasena.textProperty().addListener((observable, anterior, actual) -> ocultarError());
        txtUsuario.focusedProperty().addListener((observable, anterior, actual) ->
                actualizarEstiloCampo(contenedorUsuario, errorUsuario, actual));
        txtContrasena.focusedProperty().addListener((observable, anterior, actual) ->
                actualizarFocoContrasena());
        txtContrasenaVisible.focusedProperty().addListener((observable, anterior, actual) ->
                actualizarFocoContrasena());

        actualizarEstiloCampo(contenedorUsuario, false, false);
        actualizarEstiloCampo(contenedorContrasena, false, false);
        actualizarEstiloCampo(contenedorRol, false, false);

        btnMostrarContrasena.setStyle(BTN_VER);
        btnMostrarContrasena.setOnMouseEntered(e -> btnMostrarContrasena.setStyle(BTN_VER_ACTIVO));
        btnMostrarContrasena.setOnMouseExited(e ->
                btnMostrarContrasena.setStyle(btnMostrarContrasena.isSelected() ? BTN_VER_ACTIVO : BTN_VER));
        btnMostrarContrasena.selectedProperty().addListener((obs, antes, ahora) ->
                btnMostrarContrasena.setStyle(ahora ? BTN_VER_ACTIVO : BTN_VER));

        btnIngresar.setStyle(BTN_INGRESAR);
        btnIngresar.setOnMouseEntered(e -> { if (!btnIngresar.isDisabled()) btnIngresar.setStyle(BTN_INGRESAR_HOVER); });
        btnIngresar.setOnMouseExited(e -> btnIngresar.setStyle(BTN_INGRESAR));
        btnIngresar.setOnMousePressed(e -> btnIngresar.setTranslateY(1));
        btnIngresar.setOnMouseReleased(e -> btnIngresar.setTranslateY(0));

        // el color de la flechita del combo y el fondo de su lista desplegable son piezas
        // internas del control: solo se pueden tocar una vez que el combo arma su piel
        EstiloUtil.alArmarPiel(cmbRol, () -> {
            Node flecha = cmbRol.lookup(".arrow");
            if (flecha != null) {
                flecha.setStyle("-fx-background-color: #B8862D;");
            }
        });
    }

    private void aplicarFondo() {
        Image imagen = new Image(getClass().getResource("/untrm/hotel_san_antonio/images/fondo_login.png").toExternalForm());
        BackgroundSize tamano = new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, false, true);
        rootLogin.setBackground(new Background(new BackgroundImage(imagen,
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, tamano)));
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
        return PrincipalController.rutaMarco(rol);
    }

    private void actualizarFocoContrasena() {
        actualizarEstiloCampo(contenedorContrasena, errorContrasena,
                txtContrasena.isFocused() || txtContrasenaVisible.isFocused());
    }

    /** El error manda sobre el foco: un campo enfocado y en error se ve en rojo, no en dorado. */
    private void actualizarEstiloCampo(HBox contenedor, boolean error, boolean foco) {
        contenedor.setStyle(error ? CAMPO_ERROR : foco ? CAMPO_FOCO : CAMPO_NORMAL);
    }

    private void mostrarError(String mensaje, boolean nuevoErrorUsuario,
            boolean nuevoErrorContrasena, boolean nuevoErrorRol) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
        errorUsuario = nuevoErrorUsuario;
        errorContrasena = nuevoErrorContrasena;
        errorRol = nuevoErrorRol;
        actualizarEstiloCampo(contenedorUsuario, errorUsuario, txtUsuario.isFocused());
        actualizarEstiloCampo(contenedorContrasena, errorContrasena,
                txtContrasena.isFocused() || txtContrasenaVisible.isFocused());
        actualizarEstiloCampo(contenedorRol, errorRol, false);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        errorUsuario = false;
        errorContrasena = false;
        errorRol = false;
        actualizarEstiloCampo(contenedorUsuario, false, txtUsuario.isFocused());
        actualizarEstiloCampo(contenedorContrasena, false,
                txtContrasena.isFocused() || txtContrasenaVisible.isFocused());
        actualizarEstiloCampo(contenedorRol, false, false);
    }

    private void cambiarEstadoCarga(boolean cargando) {
        btnIngresar.setDisable(cargando);
        btnIngresar.setOpacity(cargando ? 0.82 : 1);
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
