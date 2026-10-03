package untrm.hotel_san_antonio.controlador;

import java.sql.SQLException;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.servicio.UsuarioService;
import untrm.hotel_san_antonio.util.Alertas;

/** Ventana para crear un usuario o editar sus datos, su rol y su estado (solo el Administrador). */
public class UsuarioFormController {

    @FXML private Label lblTitulo;
    @FXML private TextField txtNombres;
    @FXML private TextField txtApellidos;
    @FXML private TextField txtUsuario;
    @FXML private ChoiceBox<String> cmbRol;
    @FXML private ChoiceBox<String> cmbEstado;
    @FXML private VBox cajaClave;
    @FXML private PasswordField pwdClave;
    @FXML private PasswordField pwdConfirmar;
    @FXML private Label lblNota;
    @FXML private Label lblMensaje;

    private final UsuarioService servicio = new UsuarioService();
    private Usuario editando;
    private Runnable alGuardar;

    @FXML
    public void initialize() {
        cmbEstado.getItems().setAll("Activo", "Inactivo");
        cmbEstado.setValue("Activo");
        try {
            for (String rol : servicio.listarRoles()) cmbRol.getItems().add(UsuariosController.rolTexto(rol));
        } catch (SQLException | SecurityException error) {
            lblMensaje.setText("No se pudieron cargar los roles.");
        }
    }

    /**
     * @param usuario el usuario a editar, o null para crear uno nuevo
     * @param alGuardar se ejecuta al guardar, para que la lista de usuarios se actualice
     */
    public void iniciar(Usuario usuario, Runnable alGuardar) {
        this.editando = usuario;
        this.alGuardar = alGuardar;
        if (usuario == null) {
            lblTitulo.setText("Nuevo usuario");
            lblNota.setText("La contraseña debe tener 12 a 128 caracteres, con letras y números.");
            return;
        }
        lblTitulo.setText("Editar usuario U" + String.format("%03d", usuario.getIdUsuario()));
        txtNombres.setText(usuario.getNombre());
        txtApellidos.setText(usuario.getApellido());
        txtUsuario.setText(usuario.getUsuario());
        txtUsuario.setDisable(true);
        cmbRol.setValue(UsuariosController.rolTexto(usuario.getRol()));
        cmbEstado.setValue(usuario.isActivo() ? "Activo" : "Inactivo");
        cajaClave.setVisible(false);
        cajaClave.setManaged(false);
        lblNota.setText("La contraseña no se cambia aquí: use «Restablecer acceso» en la lista de usuarios.");
    }

    @FXML
    private void guardar() {
        lblMensaje.setText("");
        if (cmbRol.getValue() == null) {
            lblMensaje.setText("Seleccione un rol.");
            return;
        }
        String rol = cmbRol.getValue().toUpperCase(Locale.ROOT);
        boolean activo = "Activo".equals(cmbEstado.getValue());
        try {
            if (editando == null) {
                if (!pwdClave.getText().equals(pwdConfirmar.getText())) {
                    lblMensaje.setText("Las contraseñas no coinciden.");
                    return;
                }
                servicio.crear(txtNombres.getText(), txtApellidos.getText(), txtUsuario.getText(),
                        pwdClave.getText(), rol, activo);
            } else {
                servicio.actualizar(editando.getIdUsuario(), txtNombres.getText(), txtApellidos.getText(), rol, activo);
            }
            if (alGuardar != null) {
                alGuardar.run();
            }
            cerrar();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            if (error instanceof java.sql.SQLIntegrityConstraintViolationException) {
                lblMensaje.setText("Ya existe un usuario con ese nombre de usuario.");
            } else {
                Alertas.mostrarError("Error de base de datos", "No se pudo guardar el usuario.\n\n" + error.getMessage());
            }
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
