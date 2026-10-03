package untrm.hotel_san_antonio.controlador;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.dao.ModulosDAO;
import untrm.hotel_san_antonio.dao.ReportesDAO;
import untrm.hotel_san_antonio.dao.ReportesDAO.Resultado;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloGlobal;
import untrm.hotel_san_antonio.util.ExportarArchivo;
import untrm.hotel_san_antonio.util.PdfTabla;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.TablaExportable;
import untrm.hotel_san_antonio.util.XlsxTabla;

/**
 * Reportes del Administrador en una sola pantalla: se elige el reporte (ocupacion, ingresos o ventas de
 * tienda), el periodo y como agrupar, y se ven los totales, el grafico y la tabla. Se puede guardar en
 * Excel o en PDF.
 */
public class ReportesController {

    private static final String OCUPACION = "Ocupación";
    private static final String INGRESOS = "Ingresos";
    private static final String VENTAS = "Ventas de tienda";
    private static final String TODOS = "Todos";
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private ChoiceBox<String> cmbReporte;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Label lblFiltro1;
    @FXML private ChoiceBox<String> cmbFiltro1;
    @FXML private VBox boxFiltro2;
    @FXML private Label lblFiltro2;
    @FXML private ChoiceBox<String> cmbFiltro2;
    @FXML private VBox boxBuscar;
    @FXML private TextField txtBuscar;
    @FXML private ChoiceBox<String> cmbAgrupar;

    @FXML private Label lblTotal1;
    @FXML private Label lblValor1;
    @FXML private Label lblTotal2;
    @FXML private Label lblValor2;
    @FXML private Label lblTotal3;
    @FXML private Label lblValor3;

    @FXML private Label lblTituloGrafico;
    @FXML private BarChart<String, Number> grafico;
    @FXML private Label lblCantidad;
    @FXML private TableView<List<String>> tabla;

    private final ReportesDAO dao = new ReportesDAO();
    private final ModulosDAO modulosDAO = new ModulosDAO();
    private Resultado ultimo;
    private String ultimoReporte;
    private String ultimaAgrupacion;
    private boolean configurando;

    @FXML
    public void initialize() {
        if (!SesionActual.esAdministrador()) {
            Alertas.mostrarError("Reportes", "Solo el Administrador puede ver los reportes.");
            return;
        }
        cmbReporte.getItems().setAll(OCUPACION, INGRESOS, VENTAS);
        grafico.setAnimated(false);
        grafico.setLegendVisible(false);
        restablecerFechas();
        cmbReporte.valueProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null && !configurando) {
                configurar(ahora);
                generar();
            }
        });
        txtBuscar.setOnAction(evento -> generar());
        cmbReporte.setValue(OCUPACION); // dispara configurar() y generar()
    }

    // --------------------------------------------------------------------- filtros

    private void restablecerFechas() {
        dpHasta.setValue(LocalDate.now());
        dpDesde.setValue(LocalDate.now().minusDays(29));
    }

    /** Pone los filtros que corresponden al reporte elegido. */
    private void configurar(String reporte) {
        configurando = true;
        try {
            boxFiltro2.setVisible(false);
            boxFiltro2.setManaged(false);
            boxBuscar.setVisible(false);
            boxBuscar.setManaged(false);
            txtBuscar.clear();
            switch (reporte) {
                case OCUPACION -> {
                    lblFiltro1.setText("Tipo de habitación");
                    cmbFiltro1.getItems().setAll(TODOS);
                    cmbFiltro1.getItems().addAll(listaSegura(() -> modulosDAO.tiposHabitacion()));
                    cmbAgrupar.getItems().setAll("Día", "Semana", "Mes");
                }
                case INGRESOS -> {
                    lblFiltro1.setText("Origen");
                    cmbFiltro1.getItems().setAll(TODOS, "Alojamiento", "Tienda");
                    lblFiltro2.setText("Medio de pago");
                    cmbFiltro2.getItems().setAll(TODOS, "Efectivo", "Yape", "Transferencia", "Tarjeta");
                    cmbFiltro2.setValue(TODOS);
                    boxFiltro2.setVisible(true);
                    boxFiltro2.setManaged(true);
                    cmbAgrupar.getItems().setAll("Día", "Mes", "Medio de pago", "Origen");
                }
                default -> {
                    lblFiltro1.setText("Categoría");
                    cmbFiltro1.getItems().setAll(TODOS);
                    cmbFiltro1.getItems().addAll(listaSegura(() -> modulosDAO.categorias()));
                    boxBuscar.setVisible(true);
                    boxBuscar.setManaged(true);
                    cmbAgrupar.getItems().setAll("Producto", "Categoría", "Día", "Mes");
                }
            }
            cmbFiltro1.setValue(TODOS);
            cmbAgrupar.setValue(cmbAgrupar.getItems().get(0));
        } finally {
            configurando = false;
        }
    }

    private interface Origen {
        List<String> obtener() throws SQLException;
    }

    private List<String> listaSegura(Origen origen) {
        try {
            return origen.obtener();
        } catch (SQLException error) {
            Alertas.mostrarError("Reportes", "No se pudieron cargar las opciones.\n\n" + error.getMessage());
            return List.of();
        }
    }

    @FXML
    private void limpiar() {
        restablecerFechas();
        configurar(cmbReporte.getValue());
        generar();
    }

    // -------------------------------------------------------------------- reporte

    @FXML
    private void generar() {
        String reporte = cmbReporte.getValue();
        if (reporte == null) {
            return;
        }
        String agrupacion = cmbAgrupar.getValue() == null ? "" : cmbAgrupar.getValue();
        try {
            Resultado r = switch (reporte) {
                case OCUPACION -> dao.ocupacion(dpDesde.getValue(), dpHasta.getValue(),
                        elegido(cmbFiltro1), agrupacion);
                case INGRESOS -> dao.ingresos(dpDesde.getValue(), dpHasta.getValue(), elegido(cmbFiltro1),
                        metodo(), agrupacion);
                default -> dao.ventas(dpDesde.getValue(), dpHasta.getValue(), elegido(cmbFiltro1),
                        txtBuscar.getText(), agrupacion);
            };
            ultimo = r;
            ultimoReporte = reporte;
            ultimaAgrupacion = agrupacion;
            mostrar(r);
        } catch (IllegalArgumentException | SecurityException error) {
            Alertas.mostrarAdvertencia("Reportes", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo generar el reporte.\n\n" + error.getMessage());
        }
    }

    private String elegido(ChoiceBox<String> caja) {
        String valor = caja.getValue();
        return valor == null || TODOS.equals(valor) || valor.startsWith("Todas") ? null : valor;
    }

    private String metodo() {
        String valor = elegido(cmbFiltro2);
        return valor == null ? null : valor.toUpperCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------- pantalla

    private void mostrar(Resultado r) {
        List<String[]> totales = r.totales();
        lblTotal1.setText(totales.get(0)[0]);
        lblValor1.setText(totales.get(0)[1]);
        lblTotal2.setText(totales.get(1)[0]);
        lblValor2.setText(totales.get(1)[1]);
        lblTotal3.setText(totales.get(2)[0]);
        lblValor3.setText(totales.get(2)[1]);

        tabla.getColumns().clear();
        for (int i = 0; i < r.encabezados().size(); i++) {
            final int indice = i;
            TableColumn<List<String>, String> columna = new TableColumn<>(r.encabezados().get(i));
            columna.setCellValueFactory(d -> new SimpleStringProperty(
                    indice < d.getValue().size() ? d.getValue().get(indice) : ""));
            columna.setPrefWidth(i == 0 ? 290 : 150);
            columna.setStyle(r.numericas().contains(i) ? "-fx-alignment: CENTER-RIGHT;" : "-fx-alignment: CENTER-LEFT;");
            tabla.getColumns().add(columna);
        }
        tabla.getItems().setAll(r.filas());
        EstiloGlobal.restilizarEncabezados(tabla);
        lblCantidad.setText(r.filas().size() + (r.filas().size() == 1 ? " fila" : " filas"));

        lblTituloGrafico.setText(r.tituloGrafico());
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (int i = 0; i < r.etiquetas().size(); i++) {
            serie.getData().add(new XYChart.Data<>(corto(r.etiquetas().get(i)), r.valores().get(i)));
        }
        grafico.getData().setAll(serie);
        pintarGrafico(serie, r.etiquetas());
    }

    /** Barras doradas, con el nombre completo al pasar el mouse. */
    private void pintarGrafico(XYChart.Series<String, Number> serie, List<String> completas) {
        for (int i = 0; i < serie.getData().size(); i++) {
            XYChart.Data<String, Number> dato = serie.getData().get(i);
            String completa = completas.get(i);
            Runnable estilizar = () -> {
                Node barra = dato.getNode();
                if (barra != null) {
                    barra.setStyle("-fx-bar-fill: #B8862D;");
                    Tooltip.install(barra, new Tooltip(completa + ": " + dato.getYValue()));
                }
            };
            if (dato.getNode() != null) {
                estilizar.run();
            } else {
                dato.nodeProperty().addListener((obs, antes, nodo) -> estilizar.run());
            }
        }
        javafx.application.Platform.runLater(() -> {
            Node fondo = grafico.lookup(".chart-plot-background");
            if (fondo != null) {
                fondo.setStyle("-fx-background-color: #FBF9F5;");
            }
        });
    }

    private String corto(String texto) {
        // en un producto se muestra solo el nombre, sin el codigo entre parentesis
        String base = texto.contains("  (") ? texto.substring(0, texto.indexOf("  (")) : texto;
        return base.length() > 16 ? base.substring(0, 15) + "…" : base;
    }

    // ------------------------------------------------------------------ exportar

    @FXML
    private void exportarExcel() {
        exportar("xlsx", "Libro de Excel", () -> XlsxTabla.generar(armarTabla()));
    }

    @FXML
    private void exportarPdf() {
        exportar("pdf", "Documento PDF", () -> PdfTabla.generar(armarTabla()));
    }

    private void exportar(String extension, String descripcion, ExportarArchivo.Generador generador) {
        if (ultimo == null || ultimo.filas().isEmpty()) {
            Alertas.mostrarInfo("Exportar", "No hay datos para exportar. Genere un reporte con filas.");
            return;
        }
        String nombre = "reporte_" + ultimoReporte.toLowerCase(java.util.Locale.ROOT).replace(' ', '_') + "_"
                + dpDesde.getValue() + "_a_" + dpHasta.getValue();
        ExportarArchivo.guardar(tabla.getScene().getWindow(), nombre, extension, descripcion, generador);
    }

    private TablaExportable armarTabla() {
        List<String> descripcion = new ArrayList<>();
        descripcion.add("Período: " + dpDesde.getValue().format(FORMATO) + " al " + dpHasta.getValue().format(FORMATO));
        StringBuilder filtros = new StringBuilder("Agrupado por: " + ultimaAgrupacion);
        if (!lblFiltro1.getText().isBlank() && cmbFiltro1.getValue() != null) {
            filtros.append("  ·  ").append(lblFiltro1.getText()).append(": ").append(cmbFiltro1.getValue());
        }
        if (boxFiltro2.isVisible() && cmbFiltro2.getValue() != null) {
            filtros.append("  ·  ").append(lblFiltro2.getText()).append(": ").append(cmbFiltro2.getValue());
        }
        if (boxBuscar.isVisible() && txtBuscar.getText() != null && !txtBuscar.getText().isBlank()) {
            filtros.append("  ·  Producto: ").append(txtBuscar.getText().trim());
        }
        descripcion.add(filtros.toString());
        descripcion.add("Filas: " + ultimo.filas().size() + "  ·  Emitido por: "
                + SesionActual.getUsuario().getNombreCompleto());

        double[] pesos = new double[ultimo.encabezados().size()];
        for (int i = 0; i < pesos.length; i++) {
            pesos[i] = i == 0 ? 2.4 : 1.2;
        }
        List<String[]> resumen = new ArrayList<>(ultimo.totales());
        return new TablaExportable("Reporte de " + ultimoReporte.toLowerCase(java.util.Locale.ROOT), ultimoReporte,
                descripcion, ultimo.encabezados(), ultimo.filas(), ultimo.numericas(), pesos, resumen);
    }
}
