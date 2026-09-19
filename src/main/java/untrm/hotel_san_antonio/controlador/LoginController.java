package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.dao.UsuarioDAO;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.PasswordUtil;
import untrm.hotel_san_antonio.util.SesionActual;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtContrasena;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    private void onIngresar() {
        ocultarError();

        String usuario = txtUsuario.getText().trim();
        String contrasena = txtContrasena.getText();

        if (usuario.isEmpty() || contrasena.isEmpty()) {
            mostrarError("Ingresa usuario y contraseña.");
            return;
        }

        try {
            Usuario encontrado = usuarioDAO.buscarPorUsuario(usuario);

            if (encontrado == null || !PasswordUtil.coincide(contrasena, encontrado.getContrasenaHash())) {
                mostrarError("Usuario o contraseña incorrectos.");
                return;
            }

            SesionActual.iniciar(encontrado);
            Navegacion.irA("/untrm/hotel_san_antonio/fxml/dashboard.fxml");

        } catch (SQLException e) {
            Alertas.mostrarError("Error de conexión",
                    "No se pudo conectar a la base de datos. Verifica que MySQL/XAMPP esté encendido.\n\n" + e.getMessage());
        } catch (IOException e) {
            Alertas.mostrarError("Error", "No se pudo cargar el Dashboard.\n\n" + e.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
