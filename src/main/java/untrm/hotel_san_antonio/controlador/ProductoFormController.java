package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import untrm.hotel_san_antonio.dao.AlmacenDAO;
import untrm.hotel_san_antonio.dao.AlmacenDAO.CategoriaFila;
import untrm.hotel_san_antonio.dao.AlmacenDAO.Item;
import untrm.hotel_san_antonio.util.Alertas;

/** Ventana para crear o editar un producto del almacén (solo el Administrador). */
public class ProductoFormController {

    @FXML private Label lblTitulo;
    @FXML private TextField txtBarras;
    @FXML private TextField txtNombre;
    @FXML private TextField txtMarca;
    @FXML private ChoiceBox<CategoriaFila> cmbCategoria;
    @FXML private TextField txtPrecio;
    @FXML private TextField txtStock;
    @FXML private Label lblNotaStock;
    @FXML private ChoiceBox<String> cmbEstado;
    @FXML private Label lblMensaje;

    private final AlmacenDAO dao = new AlmacenDAO();
    private Item editando;
    private Runnable alGuardar;

    @FXML
    public void initialize() {
        cmbEstado.getItems().setAll("Activo", "Inactivo");
        cmbEstado.setValue("Activo");
        cmbCategoria.setConverter(new StringConverter<>() {
            @Override
            public String toString(CategoriaFila fila) {
                return fila == null ? "" : fila.nombre();
            }

            @Override
            public CategoriaFila fromString(String texto) {
                return null;
            }
        });
        try {
            cmbCategoria.getItems().setAll(dao.categorias());
        } catch (SQLException | SecurityException error) {
            lblMensaje.setText("No se pudieron cargar las categorías.");
        }
    }

    /**
     * @param producto el producto a editar, o null para uno nuevo
     * @param alGuardar se ejecuta al guardar, para que la lista del almacén se actualice
     */
    public void iniciar(Item producto, Runnable alGuardar) {
        this.editando = producto;
        this.alGuardar = alGuardar;
        if (producto == null) {
            lblTitulo.setText("Nuevo producto");
            lblNotaStock.setText("Unidades con las que empieza en el almacén.");
            return;
        }
        lblTitulo.setText("Editar producto " + producto.codigo());
        txtBarras.setText(producto.codigoBarra());
        txtNombre.setText(producto.nombre());
        txtMarca.setText(producto.marca());
        for (CategoriaFila fila : cmbCategoria.getItems()) {
            if (fila.id() == producto.idCategoria()) {
                cmbCategoria.setValue(fila);
            }
        }
        txtPrecio.setText(producto.precio().toPlainString());
        txtStock.setText(String.valueOf(producto.stock()));
        txtStock.setDisable(true);
        lblNotaStock.setText("El stock se cambia con «Ajustar stock» para que quede registrado.");
        cmbEstado.setValue(producto.activo() ? "Activo" : "Inactivo");
    }

    @FXML
    private void guardar() {
        lblMensaje.setText("");
        CategoriaFila categoria = cmbCategoria.getValue();
        if (categoria == null) {
            lblMensaje.setText("Seleccione una categoría.");
            return;
        }
        BigDecimal precio;
        try {
            precio = new BigDecimal(txtPrecio.getText().trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            lblMensaje.setText("Ingrese un precio válido, por ejemplo 3.50.");
            return;
        }
        int stock = 0;
        if (editando == null) {
            try {
                stock = txtStock.getText().isBlank() ? 0 : Integer.parseInt(txtStock.getText().trim());
            } catch (NumberFormatException error) {
                lblMensaje.setText("El stock inicial debe ser un número entero.");
                return;
            }
        }
        try {
            dao.guardarProducto(editando == null ? null : editando.id(), txtBarras.getText(), txtNombre.getText(),
                    txtMarca.getText(), categoria.id(), precio, stock, "Activo".equals(cmbEstado.getValue()));
            if (alGuardar != null) {
                alGuardar.run();
            }
            cerrar();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            lblMensaje.setText(error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo guardar el producto.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }
}
