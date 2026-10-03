package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import untrm.hotel_san_antonio.dao.AlmacenDAO;
import untrm.hotel_san_antonio.dao.AlmacenDAO.Item;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloGlobal;
import untrm.hotel_san_antonio.util.ExportarArchivo;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.PdfTabla;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.TablaExportable;
import untrm.hotel_san_antonio.util.XlsxTabla;

/**
 * Almacén: una sola lista con todos los productos, su precio, su stock y si hay que reponerlos.
 * Desde aquí se crea y edita un producto, se ajusta el stock y se gestionan las categorías.
 */
public class AlmacenController {

    private static final String BASE = "/untrm/hotel_san_antonio/fxml/nuevos/almacen/";
    static final String RUTA_PRODUCTO = BASE + "producto_form.fxml";
    static final String RUTA_STOCK = BASE + "stock_form.fxml";
    static final String RUTA_CATEGORIAS = BASE + "categorias_form.fxml";

    private static final String TODOS = "Todos";
    private static final String TODAS = "Todas";
    private static final String NORMAL = "Normal";
    private static final String BAJO = "Stock bajo";
    private static final String AGOTADO = "Agotado";

    @FXML private TextField txtBuscar;
    @FXML private ChoiceBox<String> cmbCategoria;
    @FXML private ChoiceBox<String> cmbEstado;
    @FXML private ChoiceBox<String> cmbSituacion;
    @FXML private ChoiceBox<String> cmbUmbral;

    @FXML private Label lblActivos;
    @FXML private Label lblUnidades;
    @FXML private Label lblBajo;
    @FXML private Label lblAgotados;

    @FXML private Button btnEditar;
    @FXML private Button btnStock;
    @FXML private TableView<Item> tabla;
    @FXML private TableColumn<Item, String> colCodigo;
    @FXML private TableColumn<Item, String> colBarras;
    @FXML private TableColumn<Item, String> colProducto;
    @FXML private TableColumn<Item, String> colMarca;
    @FXML private TableColumn<Item, String> colCategoria;
    @FXML private TableColumn<Item, String> colPrecio;
    @FXML private TableColumn<Item, String> colStock;
    @FXML private TableColumn<Item, String> colSituacion;
    @FXML private TableColumn<Item, String> colEstado;
    @FXML private Label lblCantidad;

    private final AlmacenDAO dao = new AlmacenDAO();
    private List<Item> inventario = List.of();
    private List<Item> visibles = List.of();
    private boolean cargando;

    @FXML
    public void initialize() {
        cmbEstado.getItems().setAll(TODOS, "Activo", "Inactivo");
        cmbSituacion.getItems().setAll(TODAS, NORMAL, BAJO, AGOTADO);
        cmbUmbral.getItems().setAll("5 unidades", "10 unidades", "20 unidades");
        restablecerFiltros();

        colCodigo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().codigo()));
        colBarras.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().codigoBarra()));
        colProducto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nombre()));
        colMarca.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().marca()));
        colCategoria.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().categoria()));
        colPrecio.setCellValueFactory(d -> new SimpleStringProperty(precio(d.getValue().precio())));
        colStock.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().stock())));
        colSituacion.setCellValueFactory(d -> new SimpleStringProperty(situacion(d.getValue())));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().activo() ? "Activo" : "Inactivo"));
        colSituacion.setCellFactory(columna -> celdaSituacion());
        for (TableColumn<Item, String> columna : List.of(colCodigo, colBarras, colProducto, colMarca,
                colCategoria, colEstado)) {
            columna.setStyle("-fx-alignment: CENTER-LEFT;");
        }
        colPrecio.setStyle("-fx-alignment: CENTER-RIGHT;");
        colStock.setStyle("-fx-alignment: CENTER-RIGHT;");

        // los filtros trabajan al instante, sin botón Buscar
        txtBuscar.textProperty().addListener((o, antes, ahora) -> filtrar());
        for (ChoiceBox<String> caja : List.of(cmbCategoria, cmbEstado, cmbSituacion, cmbUmbral)) {
            caja.valueProperty().addListener((o, antes, ahora) -> filtrar());
        }
        tabla.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) -> {
            btnEditar.setDisable(ahora == null);
            btnStock.setDisable(ahora == null || !ahora.activo());
        });
        tabla.setOnMouseClicked(evento -> {
            if (evento.getButton() == MouseButton.PRIMARY && evento.getClickCount() == 2) editar();
        });
        btnEditar.setDisable(true);
        EstiloGlobal.restilizarEncabezados(tabla);
        btnStock.setDisable(true);
        cargar();
    }

    // ------------------------------------------------------------------- acciones

    @FXML
    private void cargar() {
        try {
            inventario = dao.listar();
        } catch (SQLException error) {
            inventario = List.of();
            Alertas.mostrarError("Error de base de datos", "No se pudo cargar el almacén.\n\n" + error.getMessage());
        } catch (SecurityException error) {
            inventario = List.of();
            Alertas.mostrarError("Almacén", error.getMessage());
        }
        recargarCategorias();
        filtrar();
    }

    @FXML
    private void limpiar() {
        restablecerFiltros();
        filtrar();
    }

    @FXML
    private void nuevo() {
        abrirProducto(null);
    }

    @FXML
    private void editar() {
        Item elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido != null) abrirProducto(elegido);
    }

    @FXML
    private void ajustarStock() {
        Item elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido == null) return;
        try {
            Navegacion.<StockFormController>abrirModal(RUTA_STOCK, "Ajustar stock",
                    controlador -> controlador.iniciar(elegido, this::cargar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void categorias() {
        try {
            Navegacion.<CategoriasAlmacenController>abrirModal(RUTA_CATEGORIAS, "Categorías de productos",
                    controlador -> controlador.iniciar(this::cargar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir las categorías.\n\n" + error.getMessage());
        }
    }

    private void abrirProducto(Item producto) {
        try {
            Navegacion.<ProductoFormController>abrirModal(RUTA_PRODUCTO,
                    producto == null ? "Nuevo producto" : "Editar producto",
                    controlador -> controlador.iniciar(producto, this::cargar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + error.getMessage());
        }
    }

    // ------------------------------------------------------------------- pantalla

    private void restablecerFiltros() {
        cargando = true;
        txtBuscar.clear();
        cmbCategoria.setValue(TODAS);
        cmbEstado.setValue(TODOS);
        cmbSituacion.setValue(TODAS);
        cmbUmbral.setValue("10 unidades");
        cargando = false;
    }

    /** Vuelve a llenar el filtro de categorías conservando la que estaba elegida. */
    private void recargarCategorias() {
        String elegida = cmbCategoria.getValue();
        List<String> nombres = new ArrayList<>();
        nombres.add(TODAS);
        try {
            for (AlmacenDAO.CategoriaFila fila : dao.categorias()) nombres.add(fila.nombre());
        } catch (SQLException | SecurityException error) {
            // el listado sigue funcionando sin el filtro de categorías
        }
        cargando = true;
        cmbCategoria.getItems().setAll(nombres);
        cmbCategoria.setValue(elegida != null && nombres.contains(elegida) ? elegida : TODAS);
        cargando = false;
    }

    private int umbral() {
        String valor = cmbUmbral.getValue();
        return valor == null ? 10 : Integer.parseInt(valor.split(" ")[0]);
    }

    private String situacion(Item item) {
        if (!item.activo()) return "—";
        if (item.stock() == 0) return AGOTADO;
        return item.stock() <= umbral() ? BAJO : NORMAL;
    }

    private void filtrar() {
        if (cargando) return;
        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        List<Item> resultado = new ArrayList<>();
        for (Item item : inventario) {
            if (!texto.isEmpty() && !(item.nombre().toLowerCase(Locale.ROOT).contains(texto)
                    || item.marca().toLowerCase(Locale.ROOT).contains(texto)
                    || item.codigoBarra().toLowerCase(Locale.ROOT).contains(texto)
                    || item.codigo().toLowerCase(Locale.ROOT).contains(texto))) continue;
            if (!TODAS.equals(cmbCategoria.getValue()) && !item.categoria().equals(cmbCategoria.getValue())) continue;
            String estado = cmbEstado.getValue();
            if ("Activo".equals(estado) && !item.activo() || "Inactivo".equals(estado) && item.activo()) continue;
            String pedida = cmbSituacion.getValue();
            if (pedida != null && !TODAS.equals(pedida) && !pedida.equals(situacion(item))) continue;
            resultado.add(item);
        }
        visibles = resultado;
        Item elegido = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().setAll(visibles);
        tabla.refresh();
        if (elegido != null) {
            for (Item item : visibles) {
                if (item.id() == elegido.id()) tabla.getSelectionModel().select(item);
            }
        }
        lblCantidad.setText("TOTAL DE PRODUCTOS: " + visibles.size());
        resumen();
    }

    /** Las tarjetas resumen todo el almacén, no solo lo que dejan ver los filtros. */
    private void resumen() {
        int activos = 0;
        int unidades = 0;
        int bajos = 0;
        int agotados = 0;
        for (Item item : inventario) {
            if (!item.activo()) continue;
            activos++;
            unidades += item.stock();
            if (item.stock() == 0) agotados++;
            else if (item.stock() <= umbral()) bajos++;
        }
        lblActivos.setText(String.valueOf(activos));
        lblUnidades.setText(String.valueOf(unidades));
        lblBajo.setText(String.valueOf(bajos));
        lblAgotados.setText(String.valueOf(agotados));
    }

    /** Agotado en rojo, stock bajo en ámbar y normal en verde. */
    private TableCell<Item, String> celdaSituacion() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                setText(vacio ? null : texto);
                setAlignment(Pos.CENTER_LEFT);
                if (vacio || texto == null) {
                    setStyle("");
                } else if (AGOTADO.equals(texto)) {
                    setStyle("-fx-text-fill: #B3261E; -fx-font-weight: bold;");
                } else if (BAJO.equals(texto)) {
                    setStyle("-fx-text-fill: #B26A00; -fx-font-weight: bold;");
                } else if (NORMAL.equals(texto)) {
                    setStyle("-fx-text-fill: #1F7A3E; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #6A5F52;");
                }
            }
        };
    }

    // ------------------------------------------------------------------- exportar

    @FXML
    private void exportarExcel() {
        exportar("xlsx", "Libro de Excel", () -> XlsxTabla.generar(armarTabla()));
    }

    @FXML
    private void exportarPdf() {
        exportar("pdf", "Documento PDF", () -> PdfTabla.generar(armarTabla()));
    }

    private void exportar(String extension, String descripcion, ExportarArchivo.Generador generador) {
        if (visibles.isEmpty()) {
            Alertas.mostrarInfo("Exportar", "No hay productos para exportar. Cambia los filtros.");
            return;
        }
        ExportarArchivo.guardar(tabla.getScene().getWindow(), "almacen_" + LocalDate.now(), extension,
                descripcion, generador);
    }

    private TablaExportable armarTabla() {
        List<String> descripcion = List.of(
                "Categoría: " + cmbCategoria.getValue() + "  ·  Estado: " + cmbEstado.getValue()
                        + "  ·  Situación: " + cmbSituacion.getValue() + "  (stock bajo: hasta " + umbral() + " unidades)",
                "Productos: " + visibles.size() + "  ·  Fecha: " + LocalDate.now() + "  ·  Emitido por: "
                        + SesionActual.getUsuario().getNombreCompleto());
        List<List<String>> filas = new ArrayList<>();
        int unidades = 0;
        BigDecimal valor = BigDecimal.ZERO;
        for (Item item : visibles) {
            filas.add(List.of(item.codigo(), item.codigoBarra(), item.nombre(), item.marca(), item.categoria(),
                    item.precio().toPlainString(), String.valueOf(item.stock()), situacion(item),
                    item.activo() ? "Activo" : "Inactivo"));
            if (item.activo()) {
                unidades += item.stock();
                valor = valor.add(item.precio().multiply(BigDecimal.valueOf(item.stock())));
            }
        }
        List<String[]> resumen = List.of(
                new String[] {"Unidades en stock (activos)", String.valueOf(unidades)},
                new String[] {"Valor del stock a precio de venta", precio(valor)});
        return new TablaExportable("Almacén - productos", "Almacén", descripcion,
                List.of("Código", "Código de barras", "Producto", "Marca", "Categoría", "Precio (S/)", "Stock",
                        "Situación", "Estado"),
                filas, Set.of(5, 6), new double[] {0.8, 1.4, 3.0, 1.4, 1.5, 1.0, 0.8, 1.1, 0.9}, resumen);
    }

    private String precio(BigDecimal valor) {
        return String.format(Locale.US, "%.2f", valor);
    }
}
