package untrm.hotel_san_antonio.controlador;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.servicio.UsuarioService;
import untrm.hotel_san_antonio.servicio.UsuarioService.Cambio;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloGlobal;

/** Ventana con el historial de cambios de usuarios: altas, ediciones, cambios de rol y recuperaciones. */
public class HistorialUsuariosController {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private TextField txtBuscar;
    @FXML private TableView<Cambio> tabla;
    @FXML private TableColumn<Cambio, String> colFecha;
    @FXML private TableColumn<Cambio, String> colAfectado;
    @FXML private TableColumn<Cambio, String> colAccion;
    @FXML private TableColumn<Cambio, String> colDetalle;
    @FXML private TableColumn<Cambio, String> colRealizadoPor;
    @FXML private Label lblCantidad;

    private final UsuarioService servicio = new UsuarioService();
    private List<Cambio> todos = List.of();

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fecha().format(FORMATO)));
        colAfectado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().afectado()));
        colAccion.setCellValueFactory(d -> new SimpleStringProperty(accion(d.getValue().accion())));
        colDetalle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().detalle()));
        colRealizadoPor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().realizadoPor()));
        for (TableColumn<Cambio, String> columna : List.of(colFecha, colAfectado, colAccion, colDetalle, colRealizadoPor)) {
            columna.setStyle("-fx-alignment: CENTER-LEFT;");
        }
        txtBuscar.textProperty().addListener((o, antes, ahora) -> filtrar());
        EstiloGlobal.restilizarEncabezados(tabla);
        try {
            todos = servicio.listarHistorial();
        } catch (SQLException | SecurityException error) {
            Alertas.mostrarError("Historial", "No se pudo cargar el historial.\n\n" + error.getMessage());
        }
        filtrar();
    }

    private void filtrar() {
        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        List<Cambio> resultado = new ArrayList<>();
        for (Cambio c : todos) {
            String fila = (c.afectado() + " " + accion(c.accion()) + " " + c.detalle() + " " + c.realizadoPor())
                    .toLowerCase(Locale.ROOT);
            if (texto.isEmpty() || fila.contains(texto)) resultado.add(c);
        }
        tabla.getItems().setAll(resultado);
        lblCantidad.setText("TOTAL DE REGISTROS: " + resultado.size());
    }

    private String accion(String codigo) {
        return switch (codigo) {
            case "USUARIO_CREAR" -> "Creación de usuario";
            case "USUARIO_EDITAR" -> "Edición de datos";
            case "USUARIO_ROL" -> "Cambio de rol";
            case "RECUPERACION_EMITIR" -> "Código de recuperación emitido";
            case "CONTRASENA_RESTABLECIDA" -> "Contraseña restablecida";
            case "USUARIO_BLOQUEADO" -> "Cuenta bloqueada por intentos";
            case "USUARIO_DESBLOQUEAR" -> "Cuenta desbloqueada";
            case "RECUPERACION_BLOQUEADA" -> "Código de recuperación anulado";
            default -> codigo;
        };
    }

    @FXML
    private void cerrar() {
        ((Stage) lblCantidad.getScene().getWindow()).close();
    }
}
