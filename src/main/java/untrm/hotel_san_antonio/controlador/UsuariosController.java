package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.servicio.UsuarioService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloGlobal;
import untrm.hotel_san_antonio.util.Navegacion;

/**
 * Usuarios: una sola lista con todas las cuentas. Desde aquí se crea un usuario, se edita (nombre, rol y
 * estado), se genera un código para restablecer su contraseña y se consulta el historial de cambios.
 */
public class UsuariosController {

    private static final String BASE = "/untrm/hotel_san_antonio/fxml/nuevos/usuarios/";
    static final String RUTA_FORMULARIO = BASE + "usuario_form.fxml";
    static final String RUTA_ACCESO = BASE + "recuperacion_admin.fxml";
    static final String RUTA_HISTORIAL = BASE + "historial_usuarios.fxml";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TODOS = "Todos";

    @FXML private TextField txtBuscar;
    @FXML private ChoiceBox<String> cmbRol;
    @FXML private ChoiceBox<String> cmbEstado;
    @FXML private Label lblActivos;
    @FXML private Label lblAdministradores;
    @FXML private Label lblInactivos;
    @FXML private Button btnEditar;
    @FXML private Button btnAcceso;
    @FXML private TableView<Usuario> tabla;
    @FXML private TableColumn<Usuario, String> colCodigo;
    @FXML private TableColumn<Usuario, String> colColaborador;
    @FXML private TableColumn<Usuario, String> colUsuario;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colEstado;
    @FXML private TableColumn<Usuario, String> colCreado;
    @FXML private Label lblCantidad;

    private final UsuarioService servicio = new UsuarioService();
    private List<Usuario> todos = List.of();
    private boolean cargando;

    @FXML
    public void initialize() {
        cmbEstado.getItems().setAll(TODOS, "Activo", "Inactivo");
        cmbEstado.setValue(TODOS);
        cmbRol.getItems().setAll(TODOS);
        cmbRol.setValue(TODOS);
        try {
            for (String rol : servicio.listarRoles()) cmbRol.getItems().add(rolTexto(rol));
        } catch (SQLException | SecurityException error) {
            Alertas.mostrarError("Usuarios", "No se pudieron cargar los roles.\n\n" + error.getMessage());
        }

        colCodigo.setCellValueFactory(d -> new SimpleStringProperty(String.format("U%03d", d.getValue().getIdUsuario())));
        colColaborador.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombreCompleto()));
        colUsuario.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsuario()));
        colRol.setCellValueFactory(d -> new SimpleStringProperty(rolTexto(d.getValue().getRol())));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActivo() ? "Activo" : "Inactivo"));
        colCreado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFechaCreacion() == null ? ""
                : d.getValue().getFechaCreacion().format(FORMATO_FECHA)));
        for (TableColumn<Usuario, String> columna : List.of(colCodigo, colColaborador, colUsuario, colRol,
                colEstado, colCreado)) {
            columna.setStyle("-fx-alignment: CENTER-LEFT;");
        }

        txtBuscar.textProperty().addListener((o, antes, ahora) -> filtrar());
        cmbRol.valueProperty().addListener((o, antes, ahora) -> filtrar());
        cmbEstado.valueProperty().addListener((o, antes, ahora) -> filtrar());
        tabla.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) -> {
            btnEditar.setDisable(ahora == null);
            btnAcceso.setDisable(ahora == null || !ahora.isActivo());
        });
        tabla.setOnMouseClicked(evento -> {
            if (evento.getButton() == MouseButton.PRIMARY && evento.getClickCount() == 2) editar();
        });
        btnEditar.setDisable(true);
        btnAcceso.setDisable(true);
        EstiloGlobal.restilizarEncabezados(tabla);
        cargar();
    }

    // ------------------------------------------------------------------- acciones

    @FXML
    private void cargar() {
        try {
            todos = servicio.listarUsuarios();
        } catch (SQLException | SecurityException error) {
            todos = List.of();
            Alertas.mostrarError("Usuarios", "No se pudo cargar la lista.\n\n" + error.getMessage());
        }
        filtrar();
    }

    @FXML
    private void limpiar() {
        cargando = true;
        txtBuscar.clear();
        cmbRol.setValue(TODOS);
        cmbEstado.setValue(TODOS);
        cargando = false;
        filtrar();
    }

    @FXML
    private void nuevo() {
        abrirFormulario(null);
    }

    @FXML
    private void editar() {
        Usuario elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido != null) abrirFormulario(elegido);
    }

    @FXML
    private void acceso() {
        Usuario elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido == null) return;
        try {
            Navegacion.<EmisionRecuperacionController>abrirModal(RUTA_ACCESO, "Restablecer acceso",
                    controlador -> controlador.iniciar(elegido));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir la ventana.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void historial() {
        try {
            Navegacion.abrirModal(RUTA_HISTORIAL, "Historial de usuarios");
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el historial.\n\n" + error.getMessage());
        }
    }

    private void abrirFormulario(Usuario usuario) {
        try {
            Navegacion.<UsuarioFormController>abrirModal(RUTA_FORMULARIO,
                    usuario == null ? "Nuevo usuario" : "Editar usuario",
                    controlador -> controlador.iniciar(usuario, this::cargar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + error.getMessage());
        }
    }

    // ------------------------------------------------------------------- pantalla

    private void filtrar() {
        if (cargando) return;
        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        List<Usuario> resultado = new ArrayList<>();
        for (Usuario u : todos) {
            if (!texto.isEmpty() && !(u.getUsuario().toLowerCase(Locale.ROOT).contains(texto)
                    || u.getNombreCompleto().toLowerCase(Locale.ROOT).contains(texto))) continue;
            String rol = cmbRol.getValue();
            if (rol != null && !TODOS.equals(rol) && !rol.equals(rolTexto(u.getRol()))) continue;
            String estado = cmbEstado.getValue();
            if ("Activo".equals(estado) && !u.isActivo() || "Inactivo".equals(estado) && u.isActivo()) continue;
            resultado.add(u);
        }
        Usuario elegido = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().setAll(resultado);
        if (elegido != null) {
            for (Usuario u : resultado) {
                if (u.getIdUsuario() == elegido.getIdUsuario()) tabla.getSelectionModel().select(u);
            }
        }
        lblCantidad.setText("TOTAL DE USUARIOS: " + resultado.size());

        int activos = 0;
        int administradores = 0;
        int inactivos = 0;
        for (Usuario u : todos) {
            if (u.isActivo()) {
                activos++;
                if ("ADMINISTRADOR".equals(u.getRol())) administradores++;
            } else {
                inactivos++;
            }
        }
        lblActivos.setText(String.valueOf(activos));
        lblAdministradores.setText(String.valueOf(administradores));
        lblInactivos.setText(String.valueOf(inactivos));
    }

    /** ADMINISTRADOR se muestra como Administrador. */
    static String rolTexto(String rol) {
        if (rol == null || rol.isEmpty()) return "";
        return rol.substring(0, 1).toUpperCase(Locale.ROOT) + rol.substring(1).toLowerCase(Locale.ROOT);
    }
}
