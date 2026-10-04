package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import untrm.hotel_san_antonio.dao.ProductoDAO;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.Producto;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.servicio.VentaService;
import untrm.hotel_san_antonio.util.EstiloGlobal;
import untrm.hotel_san_antonio.util.SesionActual;

/** Ventana para registrar ventas del carrito de un día en que el sistema no se usó (cobradas al momento). */
public class VentaPasadaFormController {

    /** Una línea de la venta: producto y cantidad. */
    private static final class Linea {
        final Producto producto;
        int cantidad;

        Linea(Producto producto, int cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
        }

        BigDecimal subtotal() {
            return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        }
    }

    @FXML private Label lblDia;
    @FXML private ChoiceBox<Producto> cmbProducto;
    @FXML private TextField txtCantidad;
    @FXML private TableView<Linea> tabla;
    @FXML private TableColumn<Linea, String> colProducto;
    @FXML private TableColumn<Linea, String> colCantidad;
    @FXML private TableColumn<Linea, String> colPrecio;
    @FXML private TableColumn<Linea, String> colSubtotal;
    @FXML private Label lblTotal;
    @FXML private ChoiceBox<String> cmbMetodo;
    @FXML private TextField txtCliente;
    @FXML private TextField txtMotivo;
    @FXML private Button btnRegistrar;
    @FXML private Label lblMensaje;

    private final ProductoDAO productoDAO = new ProductoDAO();
    private final VentaService ventaService = new VentaService();
    private final List<Linea> lineas = new ArrayList<>();
    private LocalDate dia;
    private Runnable alGuardar;

    @FXML
    public void initialize() {
        cmbMetodo.getItems().setAll("EFECTIVO", "YAPE", "TRANSFERENCIA", "TARJETA");
        cmbMetodo.setValue("EFECTIVO");
        txtCantidad.setText("1");
        txtMotivo.setText("Sistema sin uso (corte)");
        cmbProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                return p == null ? "" : String.format(Locale.US, "%s — S/ %.2f (stock %d)", p.getNombre(),
                        p.getPrecio(), p.getStock());
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });
        colProducto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().producto.getNombre()));
        colCantidad.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().cantidad)));
        colPrecio.setCellValueFactory(d -> new SimpleStringProperty(
                String.format(Locale.US, "%.2f", d.getValue().producto.getPrecio())));
        colSubtotal.setCellValueFactory(d -> new SimpleStringProperty(
                String.format(Locale.US, "%.2f", d.getValue().subtotal())));
        colProducto.setStyle("-fx-alignment: CENTER-LEFT;");
        colCantidad.setStyle("-fx-alignment: CENTER-RIGHT;");
        colPrecio.setStyle("-fx-alignment: CENTER-RIGHT;");
        colSubtotal.setStyle("-fx-alignment: CENTER-RIGHT;");
        EstiloGlobal.restilizarEncabezados(tabla);
        try {
            cmbProducto.getItems().setAll(productoDAO.buscarActivos(null, null));
        } catch (SQLException error) {
            lblMensaje.setText("No se pudieron cargar los productos.");
        }
        mostrarTotal();
    }

    /**
     * @param dia       el día pasado al que pertenecen las ventas
     * @param alGuardar se ejecuta después de registrar una venta, para actualizar el resumen del día
     */
    public void iniciar(LocalDate dia, Runnable alGuardar) {
        this.dia = dia;
        this.alGuardar = alGuardar;
        lblDia.setText("Ventas del " + dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @FXML
    private void agregar() {
        lblMensaje.setText("");
        Producto producto = cmbProducto.getValue();
        if (producto == null) {
            lblMensaje.setText("Elija un producto.");
            return;
        }
        int cantidad;
        try {
            cantidad = Integer.parseInt(txtCantidad.getText().trim());
        } catch (NumberFormatException error) {
            lblMensaje.setText("La cantidad debe ser un número entero.");
            return;
        }
        if (cantidad <= 0) {
            lblMensaje.setText("La cantidad debe ser mayor que cero.");
            return;
        }
        for (Linea existente : lineas) {
            if (existente.producto.getIdProducto() == producto.getIdProducto()) {
                existente.cantidad += cantidad;
                refrescar();
                return;
            }
        }
        lineas.add(new Linea(producto, cantidad));
        refrescar();
    }

    @FXML
    private void quitar() {
        Linea elegida = tabla.getSelectionModel().getSelectedItem();
        if (elegida != null) {
            lineas.remove(elegida);
            refrescar();
        }
    }

    @FXML
    private void registrar() {
        lblMensaje.setText("");
        if (lineas.isEmpty()) {
            lblMensaje.setText("Agregue al menos un producto.");
            return;
        }
        VentaTienda venta = new VentaTienda();
        venta.setIdUsuario(SesionActual.getUsuario().getIdUsuario());
        venta.setMetodoPago(cmbMetodo.getValue());
        venta.setClienteExterno(txtCliente.getText());
        List<DetalleVenta> detalles = new ArrayList<>();
        for (Linea l : lineas) {
            DetalleVenta d = new DetalleVenta();
            d.setIdProducto(l.producto.getIdProducto());
            d.setNombreProducto(l.producto.getNombre());
            d.setCantidad(l.cantidad);
            d.setPrecioUnitario(l.producto.getPrecio());
            detalles.add(d);
        }
        String motivo = txtMotivo.getText();
        btnRegistrar.setDisable(true);
        Task<Integer> tarea = new Task<>() {
            @Override protected Integer call() throws Exception {
                return ventaService.registrarVentaPasada(venta, detalles, dia, motivo);
            }
        };
        tarea.setOnSucceeded(e -> {
            btnRegistrar.setDisable(false);
            lineas.clear();
            refrescar();
            recargarProductos();
            lblMensaje.setText("Venta registrada para el " + dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
            if (alGuardar != null) {
                alGuardar.run();
            }
        });
        tarea.setOnFailed(e -> {
            btnRegistrar.setDisable(false);
            Throwable error = tarea.getException();
            lblMensaje.setText(error == null || error.getMessage() == null
                    ? "No se pudo registrar la venta." : error.getMessage());
        });
        Thread hilo = new Thread(tarea, "venta-pasada");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void cerrar() {
        ((Stage) lblMensaje.getScene().getWindow()).close();
    }

    private void refrescar() {
        tabla.getItems().setAll(lineas);
        mostrarTotal();
    }

    private void mostrarTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (Linea l : lineas) {
            total = total.add(l.subtotal());
        }
        lblTotal.setText(String.format(Locale.US, "Total: S/ %.2f", total));
    }

    /** El stock cambió con la venta: se vuelve a leer para que la lista muestre lo que queda. */
    private void recargarProductos() {
        try {
            cmbProducto.getItems().setAll(productoDAO.buscarActivos(null, null));
        } catch (SQLException error) {
            // la lista anterior sigue sirviendo; el servicio vuelve a validar el stock al registrar
        }
    }
}
