package untrm.hotel_san_antonio.controlador;

import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.servicio.UsuarioService;

public class AsignarRolController {
    @FXML private TabPane tabsModulo;
    @FXML private TableView<Usuario> tblRegistros;
    @FXML private TableColumn<Usuario,String> colCodigo, colColaborador, colUsuario, colRolActual, colEstado;
    @FXML private TextField filtroBuscarUsuarioOColaborador;
    @FXML private ComboBox<String> filtroRolActual, filtroEstado;
    @FXML private TextField txtCodigoDeUsuario, txtNombreCompleto, txtNombreDeUsuario, txtRolActual;
    @FXML private ComboBox<String> cmbNuevoRol;
    @FXML private CheckBox chkConfirmoLaAsignacionDelRolSeleccionado;
    @FXML private Label lblMensajeRol;
    private final UsuarioService servicio = new UsuarioService();
    private List<Usuario> todos = List.of();
    private Usuario seleccionado;

    @FXML private void initialize() {
        colCodigo.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getIdUsuario())));
        colColaborador.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombreCompleto()));
        colUsuario.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsuario()));
        colRolActual.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRol()));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActivo() ? "Activo" : "Inactivo"));
        tblRegistros.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> seleccionado = ahora);
        Task<List<String>> roles = new Task<>() {
            @Override protected List<String> call() throws Exception { return servicio.listarRoles(); }
        };
        roles.setOnSucceeded(e -> {
            cmbNuevoRol.getItems().setAll(roles.getValue());
            filtroRolActual.getItems().setAll("Todos");
            filtroRolActual.getItems().addAll(roles.getValue());
            filtroRolActual.setValue("Todos");
            filtroEstado.getItems().setAll("Todos", "Activo", "Inactivo");
            filtroEstado.setValue("Todos");
        });
        roles.setOnFailed(e -> lblMensajeRol.setText("No se pudieron cargar los roles."));
        ejecutar(roles);
        actualizarLista();
    }

    @FXML private void actualizarLista() {
        Task<List<Usuario>> tarea = new Task<>() {
            @Override protected List<Usuario> call() throws Exception { return servicio.listarUsuarios(); }
        };
        tarea.setOnSucceeded(e -> { todos = tarea.getValue(); filtrar(); });
        tarea.setOnFailed(e -> lblMensajeRol.setText("No se pudieron cargar los usuarios."));
        ejecutar(tarea);
    }

    @FXML private void filtrar() {
        String q = filtroBuscarUsuarioOColaborador.getText().trim().toLowerCase();
        String rol = filtroRolActual.getValue(), estado = filtroEstado.getValue();
        tblRegistros.getItems().setAll(todos.stream().filter(u -> q.isEmpty()
                || u.getUsuario().toLowerCase().contains(q)
                || u.getNombreCompleto().toLowerCase().contains(q))
                .filter(u -> rol == null || "Todos".equals(rol) || rol.equals(u.getRol()))
                .filter(u -> estado == null || "Todos".equals(estado)
                        || (u.isActivo() ? "Activo" : "Inactivo").equals(estado)).toList());
    }

    @FXML private void limpiarFiltros() {
        filtroBuscarUsuarioOColaborador.clear();
        filtroRolActual.setValue("Todos"); filtroEstado.setValue("Todos");
        filtrar();
    }

    @FXML private void seleccionarUsuario() {
        seleccionado = tblRegistros.getSelectionModel().getSelectedItem();
        if (seleccionado == null) { lblMensajeRol.setText("Seleccione un usuario."); return; }
        txtCodigoDeUsuario.setText(String.valueOf(seleccionado.getIdUsuario()));
        txtNombreCompleto.setText(seleccionado.getNombreCompleto());
        txtNombreDeUsuario.setText(seleccionado.getUsuario());
        txtRolActual.setText(seleccionado.getRol());
        cmbNuevoRol.setValue(null);
        chkConfirmoLaAsignacionDelRolSeleccionado.setSelected(false);
        tabsModulo.getSelectionModel().select(1);
    }

    @FXML private void guardar() {
        if (seleccionado == null || cmbNuevoRol.getValue() == null
                || !chkConfirmoLaAsignacionDelRolSeleccionado.isSelected()) {
            lblMensajeRol.setText("Seleccione un usuario, un rol y confirme la asignación."); return;
        }
        int id = seleccionado.getIdUsuario();
        String rol = cmbNuevoRol.getValue();
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception { servicio.asignarRol(id, rol); return null; }
        };
        tarea.setOnSucceeded(e -> {
            lblMensajeRol.setText("Rol actualizado y auditado.");
            tabsModulo.getSelectionModel().select(0);
            actualizarLista();
        });
        tarea.setOnFailed(e -> lblMensajeRol.setText(tarea.getException() instanceof RuntimeException
                ? tarea.getException().getMessage() : "No se pudo cambiar el rol."));
        ejecutar(tarea);
    }

    @FXML private void cancelar() { tabsModulo.getSelectionModel().select(0); }

    private void ejecutar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "roles-db"); hilo.setDaemon(true); hilo.start();
    }
}
