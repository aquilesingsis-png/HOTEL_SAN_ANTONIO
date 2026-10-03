/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import untrm.hotel_san_antonio.dao.EmpresaDAO;
import untrm.hotel_san_antonio.dao.HuespedDAO;
import untrm.hotel_san_antonio.modelo.Comprobante;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.servicio.ComprobanteService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.ConsultaApi;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Validador;

/**
 * Genera el comprobante de una estadia con check-in (alojamiento + consumos de tienda
 * pendientes) o de una venta de tienda suelta que todavia no tenia comprobante. No hace el
 * check-out: eso se hace aparte, en Habitaciones, una vez que ya no hay saldo pendiente.
 */
public class ComprobanteController {

    @FXML private ComboBox<String> cmbOrigen;
    @FXML private Label lblEtiquetaReferencia;
    @FXML private TextField txtReferencia;
    @FXML private Button btnCargarOperacion;
    @FXML private ComboBox<String> cmbTipoComprobante;
    @FXML private TextField txtNumero;
    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<String> cmbTipoDocumento;
    @FXML private TextField txtDocumento;
    @FXML private Button btnBuscarCliente;
    @FXML private TextField txtCliente;
    @FXML private TextField txtDireccion;
    @FXML private TableView<FilaDetalle> tblDetalle;
    @FXML private TableColumn<FilaDetalle, String> colConcepto;
    @FXML private TableColumn<FilaDetalle, String> colCantidad;
    @FXML private TableColumn<FilaDetalle, String> colPrecio;
    @FXML private TableColumn<FilaDetalle, String> colImporte;
    @FXML private Label lblTotal;
    @FXML private Label lblEstadoCuenta;
    @FXML private Label lblPagado;
    @FXML private Label lblSaldo;
    @FXML private Button btnVistaPrevia;
    @FXML private Button btnImprimir;
    @FXML private Button btnCancelar;
    @FXML private Button btnGenerar;

    private final ComprobanteService comprobanteService = new ComprobanteService();
    private final HuespedDAO huespedDAO = new HuespedDAO();
    private final EmpresaDAO empresaDAO = new EmpresaDAO();

    private CuentaHabitacion cuentaActual;
    private ComprobanteService.VentaPendiente ventaActual;

    @FXML
    public void initialize() {

        cmbOrigen.setItems(FXCollections.observableArrayList("Cuenta de habitación", "Venta de tienda"));
        cmbOrigen.setValue("Cuenta de habitación");
        cmbOrigen.valueProperty().addListener((obs, antes, ahora) -> actualizarOrigen());

        cmbTipoComprobante.setItems(FXCollections.observableArrayList("Boleta", "Nota de venta", "Factura"));
        cmbTipoComprobante.setValue("Boleta");

        cmbTipoDocumento.setItems(FXCollections.observableArrayList("DNI", "RUC"));
        cmbTipoDocumento.setValue("DNI");

        dpFecha.setValue(LocalDate.now());

        colConcepto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConcepto()));
        colCantidad.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getCantidad())));
        colPrecio.setCellValueFactory(d -> new SimpleStringProperty(formatoMoneda(d.getValue().getPrecio())));
        colImporte.setCellValueFactory(d -> new SimpleStringProperty(formatoMoneda(d.getValue().getImporte())));

        actualizarOrigen();
        limpiarOperacion();
    }

    private void actualizarOrigen() {
        boolean tienda = "Venta de tienda".equals(cmbOrigen.getValue());
        lblEtiquetaReferencia.setText(tienda ? "N.º de venta" : "Referencia");
        txtReferencia.setPromptText(tienda ? "Ej. 5" : "Ej. R-0005 o 207");
        txtReferencia.clear();
        limpiarOperacion();
    }

    // =========================================================
    // CARGAR OPERACIÓN
    // =========================================================

    @FXML
    private void onCargarOperacion() {
        String referencia = txtReferencia.getText() == null ? "" : txtReferencia.getText().trim();
        if (referencia.isEmpty()) {
            Alertas.mostrarInfo("Referencia requerida",
                    "Ingresa el código de la reserva, el número de habitación o el número de venta.");
            return;
        }

        boolean esVentaTienda = "Venta de tienda".equals(cmbOrigen.getValue());
        try {
            if (esVentaTienda) {
                cargarVenta(referencia);
            } else {
                cargarEstadia(referencia);
            }
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cargar la operación.\n\n" + e.getMessage());
        }
    }

    private void cargarEstadia(String referencia) throws SQLException {
        CuentaHabitacion cuenta = comprobanteService.cargarCuentaPorReferencia(referencia);
        if (cuenta == null) {
            limpiarOperacion();
            Alertas.mostrarInfo("No encontrado",
                    "No se encontró una estadía activa (con check-in hecho) para \"" + referencia + "\".");
            return;
        }
        cuentaActual = cuenta;
        ventaActual = null;

        ObservableList<FilaDetalle> filas = FXCollections.observableArrayList();
        for (CuentaHabitacion.Linea l : cuenta.getCargos()) {
            filas.add(new FilaDetalle(l.getArea() + " · " + l.getDetalle(), l.getCantidad(), l.getPrecioValor(), l.getSubtotalValor()));
        }
        tblDetalle.setItems(filas);

        BigDecimal saldo = cuenta.getSaldo().max(BigDecimal.ZERO);
        mostrarTotales(cuenta.getTotalCuenta(), cuenta.getTotalPagos(), saldo);

        txtDocumento.setText(cuenta.getNumDocumento());
        cmbTipoDocumento.setValue("DNI".equals(cuenta.getTipoDocumento()) ? "DNI" : "RUC");
        txtCliente.setText(cuenta.getEmpresa() != null ? cuenta.getEmpresa() : cuenta.getHuesped());
        txtDireccion.clear();

        txtNumero.clear();
        btnImprimir.setDisable(true);
    }

    private void cargarVenta(String referencia) throws SQLException {
        ComprobanteService.VentaPendiente resultado = comprobanteService.cargarVentaPorReferencia(referencia);
        if (resultado == null) {
            limpiarOperacion();
            Alertas.mostrarInfo("No encontrado",
                    "No se encontró una venta pendiente de comprobante con el número \"" + referencia + "\".");
            return;
        }
        ventaActual = resultado;
        cuentaActual = null;

        ObservableList<FilaDetalle> filas = FXCollections.observableArrayList();
        for (DetalleVenta d : resultado.detalle) {
            filas.add(new FilaDetalle(d.getNombreProducto(), d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()));
        }
        tblDetalle.setItems(filas);

        BigDecimal total = resultado.venta.getTotal();
        mostrarTotales(total, total, BigDecimal.ZERO); // una venta de tienda siempre se cobra al toque

        txtDocumento.clear();
        cmbTipoDocumento.setValue("DNI");
        txtCliente.setText(resultado.venta.getClienteExterno() == null || resultado.venta.getClienteExterno().isBlank()
                ? "Cliente varios" : resultado.venta.getClienteExterno());
        txtDireccion.clear();

        txtNumero.clear();
        btnImprimir.setDisable(true);
    }

    private void mostrarTotales(BigDecimal total, BigDecimal pagado, BigDecimal saldo) {
        lblTotal.setText(formatoMoneda(total));
        lblPagado.setText(formatoMoneda(pagado));
        lblSaldo.setText(formatoMoneda(saldo));
        boolean pagadoCompleto = saldo.signum() == 0;
        lblEstadoCuenta.setText(pagadoCompleto ? "Pagado" : "Con saldo pendiente");
        lblEstadoCuenta.setStyle("-fx-background-color: " + (pagadoCompleto ? "#E6F6EA" : "#FCE3E1")
                + "; -fx-padding: 3 8; -fx-background-radius: 3; -fx-text-fill: "
                + (pagadoCompleto ? "#1C9748" : "#B64832") + ";");
    }

    // =========================================================
    // BUSCAR CLIENTE (DNI en RENIEC, RUC en la SUNAT)
    // =========================================================

    @FXML
    private void onBuscarCliente() {
        String documento = txtDocumento.getText() == null ? "" : txtDocumento.getText().trim();
        if (documento.isEmpty()) {
            Alertas.mostrarInfo("Buscar cliente", "Ingresa un documento para buscar.");
            return;
        }
        if ("RUC".equals(cmbTipoDocumento.getValue())) {
            buscarEmpresa(documento);
        } else {
            buscarPersona(documento);
        }
    }

    private void buscarPersona(String dni) {
        if (!Validador.esDniValido(dni)) {
            Alertas.mostrarInfo("DNI inválido", "Ingresa un DNI válido de 8 dígitos.");
            return;
        }
        try {
            Huesped local = huespedDAO.buscarPorDocumento("DNI", dni);
            if (local != null) {
                txtCliente.setText((local.getNombres() + " " + local.getApellidos()).trim());
                return;
            }
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar al cliente.\n\n" + e.getMessage());
            return;
        }

        ConsultaApi.dni(dni, btnBuscarCliente,
                h -> txtCliente.setText((h.getNombres() + " " + h.getApellidos()).trim()),
                () -> Alertas.mostrarInfo("Cliente no encontrado", "No se encontraron datos para ese DNI en RENIEC."),
                motivo -> Alertas.mostrarInfo("RENIEC", "No se pudo consultar RENIEC (" + motivo + ")."));
    }

    private void buscarEmpresa(String ruc) {
        if (!Validador.esRucValido(ruc)) {
            Alertas.mostrarInfo("RUC inválido", "Ingresa un RUC válido de 11 dígitos.");
            return;
        }
        Empresa local;
        try (Connection con = ConexionBD.conectar()) {
            local = empresaDAO.buscarPorRuc(con, ruc);
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo buscar la empresa.\n\n" + e.getMessage());
            return;
        }
        if (local != null) {
            txtCliente.setText(local.getRazonSocial());
            txtDireccion.setText(local.getDireccion() == null ? "" : local.getDireccion());
            return;
        }

        ConsultaApi.ruc(ruc, btnBuscarCliente,
                emp -> {
                    txtCliente.setText(emp.getRazonSocial());
                    txtDireccion.setText(emp.getDireccion() == null ? "" : emp.getDireccion());
                },
                () -> Alertas.mostrarInfo("Empresa no encontrada", "No se encontraron datos para ese RUC en la SUNAT."),
                motivo -> Alertas.mostrarInfo("SUNAT", "No se pudo consultar la SUNAT (" + motivo + ")."));
    }

    // =========================================================
    // GENERAR COMPROBANTE
    // =========================================================

    @FXML
    private void onGenerar() {
        if (cuentaActual == null && ventaActual == null) {
            Alertas.mostrarInfo("Sin operación", "Primero carga una operación con \"Cargar cuenta\".");
            return;
        }

        String tipo = tipoSeleccionado();
        if (tipo == null) {
            Alertas.mostrarInfo("Tipo requerido", "Selecciona el tipo de comprobante.");
            return;
        }

        String documento = txtDocumento.getText() == null ? "" : txtDocumento.getText().trim();
        String nombre = txtCliente.getText() == null ? "" : txtCliente.getText().trim();

        if ("FACTURA".equals(tipo)) {
            // Una factura siempre identifica a quien se le factura: RUC, razon social y direccion.
            if (!Validador.esRucValido(documento)) {
                Alertas.mostrarInfo("RUC requerido", "Para una factura, ingresa un RUC válido de 11 dígitos.");
                return;
            }
            if (nombre.isEmpty()) {
                Alertas.mostrarInfo("Datos incompletos", "Ingresa la razón social de la empresa.");
                return;
            }
            if (txtDireccion.getText() == null || txtDireccion.getText().trim().isEmpty()) {
                Alertas.mostrarInfo("Datos incompletos", "La dirección es obligatoria para una factura.");
                return;
            }
        }
        // Boleta y Nota de venta: el cliente puede preferir no dar sus datos (queda "Cliente varios").

        try {
            Comprobante generado = cuentaActual != null
                    ? comprobanteService.generarParaEstadia(cuentaActual, tipo)
                    : comprobanteService.generarParaVenta(ventaActual.venta, tipo);

            txtNumero.setText(generado.getNumero());
            btnImprimir.setDisable(false);

            Alertas.mostrarInfo("Comprobante generado", "Se generó el comprobante " + generado.getNumero()
                    + " por " + formatoMoneda(generado.getMontoTotal()) + ".");
        } catch (IllegalStateException e) {
            Alertas.mostrarInfo("No se puede generar", e.getMessage());
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo generar el comprobante.\n\n" + e.getMessage());
        }
    }

    private String tipoSeleccionado() {
        String valor = cmbTipoComprobante.getValue();
        if (valor == null) {
            return null;
        }
        return switch (valor) {
            case "Boleta" -> "BOLETA";
            case "Factura" -> "FACTURA";
            case "Nota de venta" -> "NOTA_VENTA";
            default -> null;
        };
    }

    // =========================================================
    // VISTA PREVIA / IMPRIMIR / CANCELAR
    // =========================================================

    @FXML
    private void onVistaPrevia() {
        if (cuentaActual == null && ventaActual == null) {
            Alertas.mostrarInfo("Vista previa", "Primero carga una operación con \"Cargar cuenta\".");
            return;
        }

        Alertas.mostrarInfo("Vista previa del comprobante", textoComprobante());
    }

    private String textoComprobante() {
        StringBuilder texto = new StringBuilder();
        texto.append(cmbTipoComprobante.getValue()).append('\n');
        if (!txtNumero.getText().isBlank()) texto.append("Número: ").append(txtNumero.getText()).append('\n');
        if (dpFecha.getValue() != null) texto.append("Fecha: ").append(dpFecha.getValue()).append('\n');
        String cliente = txtCliente.getText() == null || txtCliente.getText().isBlank() ? "Cliente varios" : txtCliente.getText();
        texto.append("Cliente: ").append(cliente).append('\n');
        if (txtDocumento.getText() != null && !txtDocumento.getText().isBlank()) {
            texto.append(cmbTipoDocumento.getValue()).append(": ").append(txtDocumento.getText()).append('\n');
        }
        if (txtDireccion.getText() != null && !txtDireccion.getText().isBlank())
            texto.append("Dirección: ").append(txtDireccion.getText().trim()).append('\n');
        texto.append('\n');
        for (FilaDetalle f : tblDetalle.getItems()) {
            texto.append(String.format(Locale.US, "%s  x%d  %s%n", f.getConcepto(), f.getCantidad(), formatoMoneda(f.getImporte())));
        }
        texto.append("\nTotal: ").append(lblTotal.getText());
        texto.append("\nPagado: ").append(lblPagado.getText());
        texto.append("\nSaldo: ").append(lblSaldo.getText());

        return texto.toString();
    }

    @FXML
    private void onImprimir() {
        if (txtNumero.getText() == null || txtNumero.getText().isBlank()) {
            Alertas.mostrarInfo("Guardar PDF", "Primero genere el comprobante.");
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar comprobante en PDF");
        selector.setInitialFileName("comprobante_" + txtNumero.getText().replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        java.io.File elegido = selector.showSaveDialog(btnImprimir.getScene().getWindow());
        if (elegido == null) return;
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            destino = destino.resolveSibling(destino.getFileName() + ".pdf");
        }
        try {
            Files.write(destino, crearPdf("HOTEL SAN ANTONIO\n" + textoComprobante()));
            Alertas.mostrarInfo("PDF guardado", "Comprobante guardado en:\n" + destino.toAbsolutePath());
        } catch (IOException error) {
            Alertas.mostrarError("Guardar PDF", "No se pudo guardar el comprobante.\n" + error.getMessage());
        }
    }

    /** PDF A4 de texto, sin depender de una impresora o de bibliotecas externas. */
    static byte[] crearPdf(String texto) throws IOException {
        Charset codificacion = Charset.forName("windows-1252");
        List<String> lineas = new ArrayList<>();
        for (String linea : texto.split("\\R", -1)) {
            String restante = linea;
            while (restante.length() > 82) {
                int corte = restante.lastIndexOf(' ', 82);
                if (corte < 20) corte = 82;
                lineas.add(restante.substring(0, corte));
                restante = restante.substring(corte).stripLeading();
            }
            lineas.add(restante);
        }
        int paginas = Math.max(1, (lineas.size() + 47) / 48);
        String[] objetos = new String[4 + paginas * 2];
        objetos[1] = "<< /Type /Catalog /Pages 2 0 R >>";
        objetos[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>";
        StringBuilder hijos = new StringBuilder();
        for (int pagina = 0; pagina < paginas; pagina++) {
            int idPagina = 4 + pagina * 2;
            int idContenido = idPagina + 1;
            hijos.append(idPagina).append(" 0 R ");
            objetos[idPagina] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + idContenido + " 0 R >>";
            StringBuilder contenido = new StringBuilder("BT /F1 11 Tf 48 790 Td 15 TL\n");
            int fin = Math.min(lineas.size(), (pagina + 1) * 48);
            for (int i = pagina * 48; i < fin; i++) {
                String limpia = lineas.get(i).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
                contenido.append('(').append(limpia).append(") Tj T*\n");
            }
            contenido.append("ET\n");
            byte[] bytesContenido = contenido.toString().getBytes(codificacion);
            objetos[idContenido] = "<< /Length " + bytesContenido.length + " >>\nstream\n"
                    + contenido + "endstream";
        }
        objetos[2] = "<< /Type /Pages /Kids [ " + hijos + "] /Count " + paginas + " >>";
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write("%PDF-1.4\n".getBytes(codificacion));
        int[] posiciones = new int[objetos.length];
        for (int i = 1; i < objetos.length; i++) {
            posiciones[i] = salida.size();
            salida.write((i + " 0 obj\n" + objetos[i] + "\nendobj\n").getBytes(codificacion));
        }
        int inicioIndice = salida.size();
        salida.write(("xref\n0 " + objetos.length + "\n0000000000 65535 f \n").getBytes(codificacion));
        for (int i = 1; i < objetos.length; i++) {
            salida.write(String.format(Locale.ROOT, "%010d 00000 n \n", posiciones[i]).getBytes(codificacion));
        }
        salida.write(("trailer\n<< /Size " + objetos.length + " /Root 1 0 R >>\nstartxref\n"
                + inicioIndice + "\n%%EOF\n").getBytes(codificacion));
        return salida.toByteArray();
    }

    @FXML
    private void onCancelar() {
        txtReferencia.clear();
        cmbTipoComprobante.setValue("Boleta");
        cmbTipoDocumento.setValue("DNI");
        txtDocumento.clear();
        txtCliente.clear();
        txtDireccion.clear();
        limpiarOperacion();
    }

    private void limpiarOperacion() {
        cuentaActual = null;
        ventaActual = null;
        tblDetalle.getItems().clear();
        lblTotal.setText(formatoMoneda(BigDecimal.ZERO));
        lblPagado.setText(formatoMoneda(BigDecimal.ZERO));
        lblSaldo.setText(formatoMoneda(BigDecimal.ZERO));
        lblEstadoCuenta.setText("Sin operación cargada");
        lblEstadoCuenta.setStyle("-fx-background-color: #F3EEE7; -fx-padding: 3 8; -fx-background-radius: 3; -fx-text-fill: #999999;");
        txtNumero.clear();
        btnImprimir.setDisable(true);
    }

    private String formatoMoneda(BigDecimal monto) {
        return "S/ " + monto.setScale(2, RoundingMode.HALF_UP);
    }

    /** Una fila de la tabla "Detalle de la operación" (alojamiento, consumo de tienda o producto vendido). */
    public static class FilaDetalle {
        private final String concepto;
        private final int cantidad;
        private final BigDecimal precio;
        private final BigDecimal importe;

        public FilaDetalle(String concepto, int cantidad, BigDecimal precio, BigDecimal importe) {
            this.concepto = concepto;
            this.cantidad = cantidad;
            this.precio = precio;
            this.importe = importe;
        }

        public String getConcepto() { return concepto; }
        public int getCantidad() { return cantidad; }
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getImporte() { return importe; }
    }
}
