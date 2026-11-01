package untrm.hotel_san_antonio.controlador;

import java.sql.SQLException;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.modelo.servicio.AlmacenService;
import untrm.hotel_san_antonio.modelo.CategoriaResumen;
import untrm.hotel_san_antonio.controlador.util.Alertas;
import untrm.hotel_san_antonio.controlador.util.Consulta;

/** Ventana para crear, renombrar y eliminar las categorías de productos. */
public class CategoriasAlmacenController {

    @FXML private TableView<CategoriaResumen> tabla;
    @FXML private TableColumn<CategoriaResumen, String> colNombre;
    @FXML private TableColumn<CategoriaResumen, String> colProductos;
    @FXML private TextField txtNombre;
    @FXML private Label lblMensaje;

    private final AlmacenService almacenService = new AlmacenService();
    private final Consulta<List<CategoriaResumen>> consulta = new Consulta<>();
    private Runnable alCerrar;
    private boolean huboCambios;

    @FXML
    public void initialize() {
        colNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nombre()));
        colProductos.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().productos())));
        colNombre.setStyle("-fx-alignment: CENTER-LEFT;");
        colProductos.setStyle("-fx-alignment: CENTER-RIGHT;");
        tabla.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) -> {
            if (ahora != null) {
                txtNombre.setText(ahora.nombre());
            }
        });
        cargar();
    }

    /** @param alCerrar se ejecuta al cerrar la ventana si hubo cambios, para actualizar el almacén */
    public void iniciar(Runnable alCerrar) {
        this.alCerrar = alCerrar;
    }

    private void cargar() {
        consulta.lanzar(tabla, () -> almacenService.categorias(),
                lista -> tabla.getItems().setAll(lista),
                error -> lblMensaje.setText("No se pudieron cargar las categorías."));
    }

    @FXML
    private void nueva() {
        tabla.getSelectionModel().clearSelection();
        txtNombre.clear();
        txtNombre.requestFocus();
        lblMensaje.setText("");
    }

    /** Guarda como nueva si no hay ninguna elegida en la tabla; si hay una elegida, la renombra. */
    @FXML
    private void guardar() {
        lblMensaje.setText("");
        CategoriaResumen elegida = tabla.getSelectionModel().getSelectedItem();
        try {
            almacenService.guardarCategoria(elegida == null ? null : elegida.id(), txtNombre.getText());
            huboCambios = true;
            cargar();
            nueva();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar la categoría.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void eliminar() {
        CategoriaResumen elegida = tabla.getSelectionModel().getSelectedItem();
        if (elegida == null) {
            lblMensaje.setText("Elija primero una categoría de la tabla.");
            return;
        }
        if (!Alertas.confirmar("Eliminar categoría", "¿Eliminar «" + elegida.nombre()
                + "»? Solo se puede si no tiene productos.")) {
            return;
        }
        try {
            almacenService.eliminarCategoria(elegida.id());
            huboCambios = true;
            cargar();
            nueva();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo eliminar la categoría.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        if (huboCambios && alCerrar != null) {
            alCerrar.run();
        }
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
