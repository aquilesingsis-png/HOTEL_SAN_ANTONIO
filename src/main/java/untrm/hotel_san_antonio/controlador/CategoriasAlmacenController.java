package untrm.hotel_san_antonio.controlador;

import java.sql.SQLException;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.dao.AlmacenDAO;
import untrm.hotel_san_antonio.dao.AlmacenDAO.CategoriaFila;
import untrm.hotel_san_antonio.util.Alertas;

/** Ventana para crear, renombrar y eliminar las categorías de productos. */
public class CategoriasAlmacenController {

    @FXML private TableView<CategoriaFila> tabla;
    @FXML private TableColumn<CategoriaFila, String> colNombre;
    @FXML private TableColumn<CategoriaFila, String> colProductos;
    @FXML private TextField txtNombre;
    @FXML private Label lblMensaje;

    private final AlmacenDAO dao = new AlmacenDAO();
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
        try {
            tabla.getItems().setAll(dao.categorias());
        } catch (SQLException | SecurityException error) {
            lblMensaje.setText("No se pudieron cargar las categorías.");
        }
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
        CategoriaFila elegida = tabla.getSelectionModel().getSelectedItem();
        try {
            dao.guardarCategoria(elegida == null ? null : elegida.id(), txtNombre.getText());
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
        CategoriaFila elegida = tabla.getSelectionModel().getSelectedItem();
        if (elegida == null) {
            lblMensaje.setText("Elija primero una categoría de la tabla.");
            return;
        }
        if (!Alertas.confirmar("Eliminar categoría", "¿Eliminar «" + elegida.nombre()
                + "»? Solo se puede si no tiene productos.")) {
            return;
        }
        try {
            dao.eliminarCategoria(elegida.id());
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
