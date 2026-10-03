package untrm.hotel_san_antonio.controlador;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import untrm.hotel_san_antonio.dao.CajaDAO;
import untrm.hotel_san_antonio.dao.CajaDAO.Movimiento;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.PdfTabla;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.TablaExportable;
import untrm.hotel_san_antonio.util.XlsxTabla;

/**
 * Caja: una sola lista con todo el dinero que entra y sale (alojamiento, carrito, movimientos manuales
 * y gastos). Los filtros de arriba dejan ver solo una parte, por ejemplo solo lo del carrito.
 */
public class LibroCajaController {

    static final String RUTA_FORMULARIO = "/untrm/hotel_san_antonio/fxml/nuevos/caja/movimiento_caja_form.fxml";
    private static final String RUTA_CIERRE = "/untrm/hotel_san_antonio/fxml/nuevos/caja/cierre_arqueo.fxml";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int FILAS_POR_PAGINA = 15;
    private static final String TODOS = "Todos";

    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private ChoiceBox<String> cmbOrigen;
    @FXML private ChoiceBox<String> cmbTipo;
    @FXML private ChoiceBox<String> cmbMetodo;
    @FXML private TextField txtBuscar;
    @FXML private Button btnNuevo;
    @FXML private Button btnCerrarCaja;

    @FXML private Label lblIngresos;
    @FXML private Label lblEgresos;
    @FXML private Label lblNeto;
    @FXML private Label lblEfectivo;

    @FXML private TableView<Movimiento> tabla;
    @FXML private TableColumn<Movimiento, String> colFecha;
    @FXML private TableColumn<Movimiento, String> colTipo;
    @FXML private TableColumn<Movimiento, String> colConcepto;
    @FXML private TableColumn<Movimiento, String> colMonto;
    @FXML private TableColumn<Movimiento, String> colMetodo;
    @FXML private TableColumn<Movimiento, String> colCaja;
    @FXML private TableColumn<Movimiento, String> colDocumento;
    @FXML private TableColumn<Movimiento, String> colCliente;
    @FXML private TableColumn<Movimiento, String> colResponsable;
    @FXML private Label lblCantidad;
    @FXML private Label lblPagina;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;

    private final CajaDAO cajaDAO = new CajaDAO();
    private List<Movimiento> resultado = List.of();
    private int pagina = 1;

    @FXML
    public void initialize() {
        cmbOrigen.getItems().setAll(TODOS, "Alojamiento", "Carrito", "Manual", "Gasto");
        cmbTipo.getItems().setAll(TODOS, "Ingreso", "Egreso", "Cargo a habitación");
        cmbMetodo.getItems().setAll(TODOS, "Efectivo", "Yape", "Transferencia", "Tarjeta");
        restablecerFiltros();

        // Solo el Administrador registra movimientos a mano y cierra la caja
        boolean admin = SesionActual.esAdministrador();
        btnNuevo.setVisible(admin);
        btnNuevo.setManaged(admin);
        btnCerrarCaja.setVisible(admin);
        btnCerrarCaja.setManaged(admin);

        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fecha().format(FORMATO_FECHA)));
        colTipo.setCellValueFactory(d -> new SimpleStringProperty(textoTipo(d.getValue().tipo())));
        colConcepto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().concepto()));
        colMonto.setCellValueFactory(d -> new SimpleStringProperty(
                String.format(Locale.US, "%.2f", d.getValue().monto())));
        colMetodo.setCellValueFactory(d -> new SimpleStringProperty(textoMetodo(d.getValue().metodo())));
        colCaja.setCellValueFactory(d -> new SimpleStringProperty(textoCaja(d.getValue().metodo())));
        colDocumento.setCellValueFactory(d -> new SimpleStringProperty(vacioSiNulo(d.getValue().documento())));
        colCliente.setCellValueFactory(d -> new SimpleStringProperty(vacioSiNulo(d.getValue().cliente())));
        colResponsable.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().responsable()));
        colTipo.setCellFactory(columna -> celdaDeColor());
        // todas las celdas centradas en vertical; el monto, a la derecha
        for (TableColumn<Movimiento, String> columna : List.of(colFecha, colConcepto, colMetodo, colCaja,
                colDocumento, colCliente, colResponsable)) {
            columna.setStyle("-fx-alignment: CENTER-LEFT;");
        }
        colMonto.setStyle("-fx-alignment: CENTER-RIGHT;");
        txtBuscar.setOnAction(evento -> buscar());
        buscar();
    }

    @FXML
    private void buscar() {
        try {
            resultado = cajaDAO.listar(dpDesde.getValue(), dpHasta.getValue(), tipoElegido(), metodoElegido(),
                    origenElegido(), txtBuscar.getText());
        } catch (SQLException error) {
            resultado = List.of();
            Alertas.mostrarError("Error de base de datos", "No se pudo cargar la caja.\n\n" + error.getMessage());
        } catch (SecurityException error) {
            resultado = List.of();
            Alertas.mostrarError("Caja", error.getMessage());
        }
        pagina = 1;
        mostrar();
    }

    @FXML
    private void limpiar() {
        restablecerFiltros();
        buscar();
    }

    @FXML
    private void nuevo() {
        try {
            Navegacion.<MovimientoCajaFormController>abrirModal(RUTA_FORMULARIO, "Nuevo movimiento de caja",
                    controlador -> controlador.iniciar(this::buscar));
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void cerrarCaja() {
        try {
            Navegacion.mostrar(RUTA_CIERRE);
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el cierre de caja.\n\n" + error.getMessage());
        }
    }

    @FXML
    private void paginaAnterior() {
        if (pagina > 1) {
            pagina--;
            mostrarPagina();
        }
    }

    @FXML
    private void paginaSiguiente() {
        if (pagina < totalPaginas()) {
            pagina++;
            mostrarPagina();
        }
    }

    // ------------------------------------------------------------------ pantalla

    private void restablecerFiltros() {
        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);
        cmbOrigen.setValue(TODOS);
        cmbTipo.setValue(TODOS);
        cmbMetodo.setValue(TODOS);
        txtBuscar.clear();
    }

    /** Ingresos, egresos y efectivo en caja de todo lo que se esta viendo (no solo de la pagina). */
    private BigDecimal[] totales() {
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        for (Movimiento m : resultado) {
            boolean enEfectivo = "EFECTIVO".equals(m.metodo());
            if ("INGRESO".equals(m.tipo())) {
                ingresos = ingresos.add(m.monto());
                efectivo = enEfectivo ? efectivo.add(m.monto()) : efectivo;
            } else if ("EGRESO".equals(m.tipo())) {
                egresos = egresos.add(m.monto());
                efectivo = enEfectivo ? efectivo.subtract(m.monto()) : efectivo;
            }
        }
        return new BigDecimal[] {ingresos, egresos, efectivo};
    }

    private void mostrar() {
        BigDecimal[] total = totales();
        lblIngresos.setText(dinero(total[0]));
        lblEgresos.setText(dinero(total[1]));
        lblNeto.setText(dinero(total[0].subtract(total[1])));
        lblEfectivo.setText(dinero(total[2]));
        mostrarPagina();
    }

    // ------------------------------------------------------------------ exportar

    @FXML
    private void exportarExcel() {
        guardarArchivo("xlsx", "Libro de Excel", XlsxTabla::generar);
    }

    @FXML
    private void exportarPdf() {
        guardarArchivo("pdf", "Documento PDF", PdfTabla::generar);
    }

    private interface Generador {
        byte[] generar(TablaExportable tabla) throws IOException;
    }

    /** Guarda todo lo filtrado (no solo la pagina que se ve) en el formato elegido. */
    private void guardarArchivo(String extension, String descripcion, Generador generador) {
        if (resultado.isEmpty()) {
            Alertas.mostrarInfo("Exportar", "No hay movimientos para exportar. Cambia los filtros.");
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar caja");
        selector.setInitialFileName("caja_" + (dpDesde.getValue() == null ? "" : dpDesde.getValue() + "_a_")
                + (dpHasta.getValue() == null ? LocalDate.now() : dpHasta.getValue()) + "." + extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(descripcion, "*." + extension));
        File elegido = selector.showSaveDialog(tabla.getScene().getWindow());
        if (elegido == null) {
            return;
        }
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            destino = destino.resolveSibling(destino.getFileName() + "." + extension);
        }
        try {
            Files.write(destino, generador.generar(armarTabla()));
            Alertas.mostrarInfo("Archivo guardado", "Se guardó en:\n" + destino.toAbsolutePath());
        } catch (IOException error) {
            Alertas.mostrarError("Exportar", "No se pudo guardar el archivo.\n\n" + error.getMessage());
        }
    }

    private TablaExportable armarTabla() {
        DateTimeFormatter dia = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> descripcion = new ArrayList<>();
        descripcion.add("Período: " + (dpDesde.getValue() == null ? "desde el inicio" : dpDesde.getValue().format(dia))
                + " al " + (dpHasta.getValue() == null ? "hoy" : dpHasta.getValue().format(dia)));
        String busqueda = txtBuscar.getText() == null || txtBuscar.getText().isBlank()
                ? "" : "  ·  Búsqueda: " + txtBuscar.getText().trim();
        descripcion.add("Origen: " + cmbOrigen.getValue() + "  ·  Tipo: " + cmbTipo.getValue()
                + "  ·  Medio de pago: " + cmbMetodo.getValue() + busqueda);
        descripcion.add("Registros: " + resultado.size() + "  ·  Emitido por: "
                + SesionActual.getUsuario().getNombreCompleto());

        List<List<String>> filas = new ArrayList<>();
        for (Movimiento m : resultado) {
            filas.add(List.of(m.fecha().format(FORMATO_FECHA), textoTipo(m.tipo()), m.concepto(),
                    String.format(Locale.US, "%.2f", m.monto()), textoMetodo(m.metodo()), textoCaja(m.metodo()),
                    vacioSiNulo(m.documento()), vacioSiNulo(m.cliente()), m.responsable()));
        }
        BigDecimal[] total = totales();
        List<String[]> resumen = List.of(
                new String[] {"Ingresos", dinero(total[0])},
                new String[] {"Egresos", dinero(total[1])},
                new String[] {"Neto", dinero(total[0].subtract(total[1]))},
                new String[] {"Efectivo en caja", dinero(total[2])});
        return new TablaExportable("Caja - movimientos", descripcion,
                List.of("Fecha", "Tipo", "Concepto", "Monto (S/)", "Medio de pago", "Caja / Banco",
                        "Documento", "Cliente", "Registrado por"),
                filas, Set.of(3), new double[] {1.25, 1.1, 4.2, 0.9, 1.1, 1.2, 1.1, 1.7, 1.5}, resumen);
    }

    private void mostrarPagina() {
        int total = totalPaginas();
        pagina = Math.min(pagina, total);
        int desde = (pagina - 1) * FILAS_POR_PAGINA;
        int hasta = Math.min(desde + FILAS_POR_PAGINA, resultado.size());
        tabla.getItems().setAll(desde < hasta ? resultado.subList(desde, hasta) : List.of());
        lblPagina.setText(pagina + " / " + total);
        btnAnterior.setDisable(pagina <= 1);
        btnSiguiente.setDisable(pagina >= total);
        lblCantidad.setText("TOTAL DE REGISTROS: " + resultado.size());
    }

    private int totalPaginas() {
        return Math.max(1, (int) Math.ceil(resultado.size() / (double) FILAS_POR_PAGINA));
    }

    /** El tipo se pinta: ingreso en verde, egreso en rojo, cargo a habitacion en gris. */
    private TableCell<Movimiento, String> celdaDeColor() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                setText(vacio ? null : texto);
                setAlignment(Pos.CENTER_LEFT);
                if (vacio || texto == null) {
                    setStyle("");
                } else if ("Ingreso".equals(texto)) {
                    setStyle("-fx-text-fill: #1F7A3E; -fx-font-weight: bold;");
                } else if ("Egreso".equals(texto)) {
                    setStyle("-fx-text-fill: #B3261E; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #6A5F52;");
                }
            }
        };
    }

    // ------------------------------------------------------------------- filtros

    private String tipoElegido() {
        return switch (cmbTipo.getValue() == null ? TODOS : cmbTipo.getValue()) {
            case "Ingreso" -> "INGRESO";
            case "Egreso" -> "EGRESO";
            case "Cargo a habitación" -> "CARGO";
            default -> null;
        };
    }

    private String metodoElegido() {
        String valor = cmbMetodo.getValue();
        return valor == null || TODOS.equals(valor) ? null : valor.toUpperCase(Locale.ROOT);
    }

    private String origenElegido() {
        String valor = cmbOrigen.getValue();
        return valor == null || TODOS.equals(valor) ? null : valor;
    }

    // ---------------------------------------------------------------------- textos

    private String textoTipo(String tipo) {
        return switch (tipo) {
            case "INGRESO" -> "Ingreso";
            case "EGRESO" -> "Egreso";
            default -> "Cargo a habitación";
        };
    }

    private String textoMetodo(String metodo) {
        return switch (metodo == null ? "" : metodo) {
            case "EFECTIVO" -> "Efectivo";
            case "YAPE" -> "Yape";
            case "TRANSFERENCIA" -> "Transferencia";
            case "TARJETA" -> "Tarjeta";
            case "CARGO A HABITACION" -> "A la cuenta";
            default -> "No registrado";
        };
    }

    /** Donde queda el dinero: el efectivo en la caja; Yape, transferencia y tarjeta en el banco. */
    private String textoCaja(String metodo) {
        return switch (metodo == null ? "" : metodo) {
            case "EFECTIVO" -> "Caja (efectivo)";
            case "YAPE", "TRANSFERENCIA", "TARJETA" -> "Banco / billetera";
            default -> "—";
        };
    }

    private String vacioSiNulo(String texto) {
        return texto == null ? "" : texto;
    }

    private String dinero(BigDecimal monto) {
        return String.format(Locale.US, "S/ %.2f", monto);
    }
}
