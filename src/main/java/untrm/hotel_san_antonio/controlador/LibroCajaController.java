package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.math.BigDecimal;
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
import untrm.hotel_san_antonio.dao.CajaDAO;
import untrm.hotel_san_antonio.dao.CajaDAO.Movimiento;
import untrm.hotel_san_antonio.servicio.VentaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Consulta;
import untrm.hotel_san_antonio.util.ExportarArchivo;
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
    @FXML private Button btnAnularVenta;

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
    @FXML private Label lblAviso;
    @FXML private Label lblPagina;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;

    private final CajaDAO cajaDAO = new CajaDAO();
    private final VentaService ventaService = new VentaService();
    private List<Movimiento> resultado = List.of();
    private CajaDAO.Totales totalesActuales = new CajaDAO.Totales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    private boolean recortado;
    private final Consulta<Carga> consulta = new Consulta<>();
    private int pagina = 1;

    @FXML
    public void initialize() {
        cmbOrigen.getItems().setAll(TODOS, "Alojamiento", "Carrito", "Manual", "Gasto");
        cmbTipo.getItems().setAll(TODOS, "Ingreso", "Egreso", "Cargo a habitación", "Anulada");
        cmbMetodo.getItems().setAll(TODOS, "Efectivo", "Yape", "Transferencia", "Tarjeta");
        restablecerFiltros();

        // Solo el Administrador registra movimientos a mano; cada turno (recepcion o administrador) cierra su caja
        boolean admin = SesionActual.esAdministrador();
        btnNuevo.setVisible(admin);
        btnNuevo.setManaged(admin);

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
        // solo se puede anular una venta del carrito que todavia no este anulada
        btnAnularVenta.setDisable(true);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) ->
                btnAnularVenta.setDisable(!esVentaAnulable(ahora)));
        buscar();
    }

    /** Lo que trae una busqueda: la lista (con uno de mas que el limite, para saber si hay mas) y los totales completos. */
    private record Carga(List<Movimiento> lista, CajaDAO.Totales totales) { }

    @FXML
    private void buscar() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        String tipo = tipoElegido();
        String metodo = metodoElegido();
        String origen = origenElegido();
        String texto = txtBuscar.getText();
        consulta.lanzar(tabla,
                () -> new Carga(cajaDAO.listar(desde, hasta, tipo, metodo, origen, texto),
                        cajaDAO.totales(desde, hasta, tipo, metodo, origen, texto)),
                carga -> mostrarCarga(carga.lista(), carga.totales()),
                error -> {
                    mostrarCarga(List.of(), new CajaDAO.Totales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                    if (error instanceof SecurityException) {
                        Alertas.mostrarError("Caja", error.getMessage());
                    } else {
                        Alertas.mostrarError("Error de base de datos", "No se pudo cargar la caja.\n\n" + error.getMessage());
                    }
                });
    }

    private void mostrarCarga(List<Movimiento> lista, CajaDAO.Totales totales) {
        totalesActuales = totales;
        // la lista trae uno de mas que el limite: asi se sabe que hay mas y no se muestra en silencio un corte
        recortado = lista.size() > CajaDAO.LIMITE;
        resultado = recortado ? List.copyOf(lista.subList(0, CajaDAO.LIMITE)) : lista;
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

    /** Anula la venta seleccionada: pide el motivo, avisa lo que pasa y deja todo en la auditoria. */
    @FXML
    private void anularVenta() {
        Movimiento fila = tabla.getSelectionModel().getSelectedItem();
        if (!esVentaAnulable(fila)) {
            Alertas.mostrarInfo("Anular venta", "Selecciona en la lista una venta del carrito que no esté anulada.");
            return;
        }
        String motivo = Alertas.pedirTexto("Anular venta N.º " + fila.idOrigen(),
                "Venta N.º " + fila.idOrigen() + " · " + dinero(fila.monto()) + "\nEscribe el motivo de la anulación:",
                "Ej. el cliente devolvió el producto");
        if (motivo == null) {
            return;
        }
        if (!Alertas.confirmar("Anular venta", "¿Anular la venta N.º " + fila.idOrigen() + " por " + dinero(fila.monto())
                + "?\n\nLos productos vuelven al stock y la venta deja de contar en caja. Si tiene boleta o factura, "
                + "también se anula.\nEsto no se puede deshacer.")) {
            return;
        }
        try {
            VentaService.Anulacion anulada = ventaService.anular(fila.idOrigen(), motivo);
            buscar();
            StringBuilder aviso = new StringBuilder("Se anuló la venta N.º " + anulada.idVenta() + ". Volvieron "
                    + anulada.unidades() + (anulada.unidades() == 1 ? " unidad" : " unidades") + " al stock.");
            if (anulada.comprobanteAnulado() != null) {
                aviso.append("\nTambién se anuló el comprobante ").append(anulada.comprobanteAnulado()).append('.');
            }
            if (anulada.aDevolver().signum() > 0) {
                aviso.append("\n\nOjo: el huésped ya había pagado ").append(dinero(anulada.aDevolver()))
                        .append(" de este consumo. Devuélvele ese dinero y regístralo como egreso en Caja (+ Nuevo movimiento).");
            }
            Alertas.mostrarInfo("Venta anulada", aviso.toString());
        } catch (IllegalStateException | IllegalArgumentException | SecurityException error) {
            Alertas.mostrarInfo("No se puede anular", error.getMessage());
        } catch (SQLException error) {
            Alertas.mostrarError("Error de base de datos", "No se pudo anular la venta.\n\n" + error.getMessage());
        }
    }

    private boolean esVentaAnulable(Movimiento fila) {
        return fila != null && "Carrito".equals(fila.origen()) && !"ANULADA".equals(fila.tipo());
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
        return new BigDecimal[] {totalesActuales.ingresos(), totalesActuales.egresos(), totalesActuales.efectivo()};
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
        exportar("xlsx", "Libro de Excel", () -> XlsxTabla.generar(armarTabla()));
    }

    @FXML
    private void exportarPdf() {
        exportar("pdf", "Documento PDF", () -> PdfTabla.generar(armarTabla()));
    }

    /** Guarda todo lo filtrado (no solo la pagina que se ve) en el formato elegido. */
    private void exportar(String extension, String descripcion, ExportarArchivo.Generador generador) {
        if (resultado.isEmpty()) {
            Alertas.mostrarInfo("Exportar", "No hay movimientos para exportar. Cambia los filtros.");
            return;
        }
        String nombre = "caja_" + (dpDesde.getValue() == null ? "" : dpDesde.getValue() + "_a_")
                + (dpHasta.getValue() == null ? LocalDate.now() : dpHasta.getValue());
        ExportarArchivo.guardar(tabla.getScene().getWindow(), nombre, extension, descripcion, generador);
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
        return new TablaExportable("Caja - movimientos", "Caja", descripcion,
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
        lblCantidad.setText("TOTAL DE REGISTROS: " + resultado.size() + (recortado ? " (hay más)" : ""));
        lblAviso.setText("Hay más de " + CajaDAO.LIMITE + " movimientos con estos filtros: la lista muestra solo los "
                + CajaDAO.LIMITE + " más recientes. Los totales de arriba sí incluyen todos. Acota las fechas para ver el resto.");
        lblAviso.setVisible(recortado);
        lblAviso.setManaged(recortado);
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
                } else if ("Anulada".equals(texto)) {
                    setStyle("-fx-text-fill: #8A8076; -fx-font-style: italic;");
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
            case "Anulada" -> "ANULADA";
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
            case "ANULADA" -> "Anulada";
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
