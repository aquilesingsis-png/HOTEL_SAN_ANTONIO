package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import untrm.hotel_san_antonio.dao.ModulosDAO;
import untrm.hotel_san_antonio.dao.ModulosDAO.Registro;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.SesionActual;

/** Activa las vistas FXML que antes eran maquetas sin manejadores. */
public class ModulosNuevosController {
    private final String modulo;
    private final Map<String, Object> nodos;
    private final ModulosDAO dao = new ModulosDAO();
    private final ObservableList<Registro> visibles = FXCollections.observableArrayList();
    private final TableView<Registro> tabla;
    private Integer editando;

    @SuppressWarnings("unchecked")
    public ModulosNuevosController(String modulo, Map<String, Object> nodos) {
        this.modulo = modulo;
        this.nodos = nodos;
        this.tabla = (TableView<Registro>) nodos.get("tblRegistros");
    }

    public void iniciar() {
        if (tabla == null) throw new IllegalStateException("Falta la tabla del módulo " + modulo);
        tabla.setItems(visibles);
        for (int i = 0; i < tabla.getColumns().size(); i++) {
            final int indice = i;
            @SuppressWarnings("unchecked") TableColumn<Registro, String> columna =
                    (TableColumn<Registro, String>) tabla.getColumns().get(i);
            columna.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                    indice < c.getValue().celdas().size() ? c.getValue().celdas().get(indice) : ""));
        }
        for (var entrada : nodos.entrySet()) {
            if (entrada.getValue() instanceof Button boton && entrada.getKey().startsWith("btn")) {
                String accion = entrada.getKey();
                boton.setOnAction(evento -> ejecutar(accion));
            }
        }
        inicializarCombos();
        if (modulo.equals("reportes/ocupacion")) establecerPeriodoOcupacion();
        configurarCalculos();
        cargar();
    }

    private void ejecutar(String accion) {
        try {
            if (accion.contains("FiltroLimpiar")) { limpiarFiltros(); cargar(); return; }
            if (accion.contains("GenerarReporte")) { generarReporte(); return; }
            if (accion.startsWith("btnFiltro") || accion.contains("Actualizar")
                    || accion.contains("Consultar")) {
                cargar(); return;
            }
            if (accion.contains("ListaNuevo")) { nuevo(); return; }
            if (accion.contains("ListaEditar") || accion.contains("SeleccionarProducto")) {
                editarSeleccionado(); return;
            }
            if (accion.contains("VistaPrevia")) { tab(1); return; }
            if (accion.contains("VerDetalle")) {
                verDetalle(); return;
            }
            if (accion.contains("ListaExportar")) {
                if (modulo.equals("reportes/reportes_personalizados") && "PDF".equals(combo("cmbFormato")))
                    guardarPdf();
                else exportarCsv();
                return;
            }
            if (accion.contains("ListaImprimir")) { guardarPdf(); return; }
            if (accion.contains("FormCancelar")) { tab(0); return; }
            if (accion.contains("FormLimpiar")) {
                if (modulo.equals("caja/arqueo")) limpiarConteo();
                else {
                    limpiarFormulario();
                    if (modulo.equals("reportes/reportes_personalizados")) {
                        seleccionarCombo("cmbFuente", "Todos");
                        seleccionarCombo("cmbAgruparPor", "Día");
                        seleccionarCombo("cmbFormato", "PDF");
                    }
                }
                return;
            }
            if (accion.contains("EliminarCategoria")) { eliminarCategoria(); return; }
            if (accion.contains("DesactivarUsuario")) { desactivarUsuario(); return; }
            if (accion.contains("FormGuardar") || accion.contains("FormRegistrarPago")
                    || accion.contains("FormConfirmarCierre")) {
                guardar(accion); return;
            }
            Alertas.mostrarError("Acción", "No se reconoce la acción " + accion + ".");
        } catch (SQLException | IOException | IllegalArgumentException | IllegalStateException error) {
            Alertas.mostrarError("Operación", error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }

    private void cargar() {
        try {
            validarFechas();
            int umbral = umbral();
            LocalDate dia = fecha("filtroFecha");
            List<Registro> registros = modulo.equals("reportes/ocupacion")
                    ? dao.ocupacionDiaria(fecha("filtroDesde"), fecha("filtroHasta"))
                    : dao.consultar(modulo, umbral, dia == null ? LocalDate.now() : dia);
            List<Registro> filtrados = registros.stream().filter(this::coincideFiltros).toList();
            visibles.setAll(modulo.equals("reportes/ocupacion")
                    ? agruparOcupacion(filtrados) : filtrados);
            actualizarMetricas();
            actualizarGrafico();
        } catch (SQLException | RuntimeException error) {
            visibles.clear();
            Alertas.mostrarError("Consulta", "No se pudieron cargar los datos de " + modulo
                    + ".\n" + error.getMessage());
        }
    }

    private void validarFechas() {
        LocalDate desde = fecha("filtroDesde");
        LocalDate hasta = fecha("filtroHasta");
        if (desde != null && hasta != null && hasta.isBefore(desde))
            throw new IllegalArgumentException("La fecha final debe ser igual o posterior a la inicial.");
    }

    private List<Registro> agruparOcupacion(List<Registro> dias) {
        String modo = combo("filtroAgruparPor");
        if (modo.isBlank() || modo.equals("Día")) return dias;
        Map<String, BigDecimal[]> sumas = new LinkedHashMap<>();
        Map<String, LocalDate> fechas = new LinkedHashMap<>();
        for (Registro fila : dias) {
            LocalDate inicio = modo.equals("Semana")
                    ? fila.fecha().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : fila.fecha().withDayOfMonth(1);
            String clave = inicio + "|" + fila.celdas().get(1);
            BigDecimal[] valores = sumas.computeIfAbsent(clave, k -> new BigDecimal[] {
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            fechas.put(clave, inicio);
            for (int i = 0; i < 4; i++) valores[i] = valores[i].add(numero(fila, i < 2 ? i + 2 : i + 3));
        }
        List<Registro> resultado = new ArrayList<>();
        for (var entrada : sumas.entrySet()) {
            BigDecimal[] v = entrada.getValue();
            String porcentaje = v[0].signum() == 0 ? "0.0" : v[1]
                    .multiply(BigDecimal.valueOf(100)).divide(v[0], 1,
                            java.math.RoundingMode.HALF_UP).toPlainString();
            LocalDate inicio = fechas.get(entrada.getKey());
            String tipo = entrada.getKey().substring(entrada.getKey().indexOf('|') + 1);
            resultado.add(new Registro(0, List.of(inicio.toString(), tipo,
                    v[0].toPlainString(), v[1].toPlainString(), porcentaje,
                    v[2].toPlainString(), v[3].toPlainString()), inicio));
        }
        resultado.sort((a, b) -> b.fecha().compareTo(a.fecha()));
        return resultado;
    }

    private int umbral() {
        String valor = combo("filtroUmbralDeRevision");
        String digitos = valor.replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? 10 : Math.min(100000, Integer.parseInt(digitos));
    }

    private boolean coincideFiltros(Registro fila) {
        String textoFila = String.join(" ", fila.celdas()).toLowerCase(Locale.ROOT);
        for (var entrada : nodos.entrySet()) {
            String id = entrada.getKey();
            if (!id.startsWith("filtro")) continue;
            Object control = entrada.getValue();
            if (control instanceof TextField campo) {
                String valor = campo.getText() == null ? "" : campo.getText().trim().toLowerCase(Locale.ROOT);
                if (!valor.isEmpty() && !textoFila.contains(valor)) return false;
            } else if (control instanceof ComboBox<?> caja) {
                Object valor = caja.getValue();
                if (valor == null) continue;
                String elegido = valor.toString().trim().toLowerCase(Locale.ROOT);
                if (elegido.isEmpty() || elegido.startsWith("tod") || elegido.startsWith("seleccion")
                        || id.contains("Umbral") || id.contains("Agrupar")) continue;
                if (id.equals("filtroDisponibilidad")) {
                    boolean conStock = numero(fila, 3).signum() > 0;
                    if (conStock != elegido.equals("con stock")) return false;
                    continue;
                }
                if (id.equals("filtroPrioridad")) {
                    boolean agotado = numero(fila, 3).signum() == 0;
                    if (agotado != elegido.equals("agotado")) return false;
                    continue;
                }
                if (id.equals("filtroEstado") && modulo.equals("caja/cierre_caja")) {
                    boolean borrador = fila.celdas().get(7).equals("BORRADOR");
                    if (elegido.equals("abierto") && !borrador) return false;
                    if (elegido.equals("cerrado") && borrador) return false;
                    if (elegido.equals("con diferencia") && numero(fila, 6).signum() == 0) return false;
                    continue;
                }
                if (id.equals("filtroEstado") && (modulo.equals("almacen/productos")
                        || modulo.equals("almacen/precios") || modulo.equals("usuarios/editar_usuario"))) {
                    int indiceEstado = modulo.equals("almacen/productos") ? 7 : 5;
                    if (!fila.celdas().get(indiceEstado).equalsIgnoreCase(elegido)) return false;
                    continue;
                }
                if (id.equals("filtroOrigen") && modulo.equals("caja/totales_dia")) {
                    int indice = elegido.equals("tienda") ? 2 : 1;
                    if (numero(fila, indice).signum() == 0) return false;
                    continue;
                }
                if (id.equals("filtroAccion") && modulo.equals("usuarios/historial_usuarios")) {
                    String accion = fila.celdas().get(2);
                    String detalle = fila.celdas().get(6).toLowerCase(Locale.ROOT);
                    boolean coincide = switch (elegido) {
                        case "creación de usuario" -> accion.equals("USUARIO_CREAR");
                        case "edición de datos" -> accion.equals("USUARIO_EDITAR");
                        case "cambio de rol" -> accion.equals("USUARIO_ROL");
                        case "activación" -> accion.equals("USUARIO_EDITAR") && detalle.contains("/ activo");
                        case "desactivación" -> accion.equals("USUARIO_EDITAR") && detalle.contains("/ inactivo");
                        default -> false;
                    };
                    if (!coincide) return false;
                    continue;
                }
                if (!textoFila.contains(elegido)) return false;
            }
        }
        LocalDate fechaFila = fila.fecha();
        if (fechaFila != null) {
            LocalDate desde = fecha("filtroDesde");
            LocalDate hasta = fecha("filtroHasta");
            LocalDate dia = fecha("filtroFecha");
            if (desde != null && fechaFila.isBefore(desde)) return false;
            if (hasta != null && fechaFila.isAfter(hasta)) return false;
            if (dia != null && !fechaFila.equals(dia)) return false;
        }
        return true;
    }

    private void limpiarFiltros() {
        for (var entrada : nodos.entrySet()) {
            if (!entrada.getKey().startsWith("filtro")) continue;
            Object control = entrada.getValue();
            if (control instanceof TextField texto) texto.clear();
            else if (control instanceof DatePicker fecha) fecha.setValue(null);
            else if (control instanceof ComboBox<?> caja) caja.getSelectionModel().clearSelection();
        }
        if (modulo.equals("reportes/ocupacion")) establecerPeriodoOcupacion();
    }

    private void establecerPeriodoOcupacion() {
        DatePicker desde = nodo("filtroDesde", DatePicker.class);
        DatePicker hasta = nodo("filtroHasta", DatePicker.class);
        if (desde != null) desde.setValue(LocalDate.now().minusDays(29));
        if (hasta != null) hasta.setValue(LocalDate.now());
    }

    private void nuevo() throws SQLException {
        editando = null;
        limpiarFormulario();
        String nombre = SesionActual.getUsuario().getNombreCompleto();
        campo("txtResponsable", nombre);
        campo("txtTurnoCaja", "Mañana");
        if (modulo.equals("caja/arqueo"))
            campo("txtEfectivoEsperadoS", dao.efectivoHoy().max(BigDecimal.ZERO).toPlainString());
        DatePicker fecha = nodo("dpFecha", DatePicker.class);
        if (fecha != null) fecha.setValue(LocalDate.now());
        DatePicker cierre = nodo("dpFechaDelCierre", DatePicker.class);
        if (cierre != null) cierre.setValue(LocalDate.now());
        if (modulo.equals("caja/cierre_caja")) actualizarResumenCierre();
        DatePicker pago = nodo("dpFechaDelPago", DatePicker.class);
        if (pago != null) pago.setValue(LocalDate.now());
        if (modulo.equals("almacen/productos")) {
            Spinner<?> stock = nodo("spnStockInicial", Spinner.class);
            if (stock != null) stock.setDisable(false);
        }
        tab(1);
    }

    private void editarSeleccionado() {
        Registro fila = seleccion();
        editando = fila.id();
        List<String> c = fila.celdas();
        switch (modulo) {
            case "almacen/categorias" -> {
                campo("txtCodigo", c.get(0)); campo("txtNombreDeLaCategoria", c.get(1));
            }
            case "almacen/precios" -> {
                campo("txtCodigoDelProducto", c.get(0)); campo("txtProducto", c.get(1));
                campo("txtPrecioActualS", c.get(4)); campo("txtNuevoPrecioS", c.get(4));
            }
            case "almacen/productos" -> {
                campo("txtCodigo", c.get(0)); campo("txtCodigoDeBarras", c.get(1));
                campo("txtNombreDelProducto", c.get(2)); campo("txtMarca", c.get(3));
                seleccionarCombo("cmbCategoria", c.get(4)); campo("txtPrecioDeVentaS", c.get(5));
                seleccionarCombo("cmbEstado", c.get(7));
                Spinner<?> stock = nodo("spnStockInicial", Spinner.class);
                if (stock != null) stock.setDisable(true); // Cambios de stock pasan por Movimientos.
            }
            case "usuarios/editar_usuario" -> {
                campo("txtCodigo", c.get(0)); campo("txtNombres", c.get(1));
                campo("txtApellidos", c.get(2)); campo("txtNombreDeUsuario", c.get(3));
                campo("txtRolActual", c.get(4)); seleccionarCombo("cmbEstado", c.get(5));
                campo("txtFechaDeCreacion", c.get(6));
            }
            default -> {
                Alertas.mostrarInfo("Selección", "Este módulo permite consultar el detalle seleccionado.");
                verDetalle(); return;
            }
        }
        tab(1);
    }

    private void verDetalle() {
        Registro fila = seleccion();
        if (modulo.equals("almacen/stock")) {
            campo("txtCodigoDelProducto", fila.celdas().get(0));
            campo("txtProducto", fila.celdas().get(1));
            campo("txtStockActual", fila.celdas().get(3));
            DatePicker fecha = nodo("dpFecha", DatePicker.class);
            if (fecha != null) fecha.setValue(LocalDate.now());
            tab(1);
            return;
        }
        StringBuilder detalle = new StringBuilder();
        for (int i = 0; i < tabla.getColumns().size() && i < fila.celdas().size(); i++) {
            detalle.append(tabla.getColumns().get(i).getText()).append(": ")
                    .append(fila.celdas().get(i)).append('\n');
        }
        Alertas.mostrarInfo("Detalle", detalle.toString());
    }

    private Registro seleccion() {
        Registro fila = tabla.getSelectionModel().getSelectedItem();
        if (fila == null) throw new IllegalArgumentException("Seleccione primero un registro de la tabla.");
        return fila;
    }

    private void tab(int indice) {
        TabPane tabs = nodo("tabsModulo", TabPane.class);
        if (tabs != null && tabs.getTabs().size() > indice) tabs.getSelectionModel().select(indice);
    }

    private void limpiarFormulario() {
        editando = null;
        for (var entrada : nodos.entrySet()) {
            String id = entrada.getKey();
            if (!id.startsWith("txt") && !id.startsWith("dp") && !id.startsWith("cmb")
                    && !id.startsWith("spn") && !id.startsWith("chk")) continue;
            Object control = entrada.getValue();
            if (control instanceof TextField texto && texto.isEditable()) texto.clear();
            else if (control instanceof TextArea area && area.isEditable()) area.clear();
            else if (control instanceof DatePicker fecha) fecha.setValue(null);
            else if (control instanceof ComboBox<?> caja) caja.getSelectionModel().clearSelection();
            else if (control instanceof CheckBox check) check.setSelected(false);
        }
    }

    private void limpiarConteo() {
        for (var entrada : nodos.entrySet()) {
            if (entrada.getKey().startsWith("spn") && entrada.getValue() instanceof Spinner<?> spinner) {
                spinner.getEditor().setText("0");
            }
        }
        campo("txtObservaciones", "");
        recalcularArqueo();
    }

    private void generarReporte() throws SQLException {
        if (!modulo.equals("reportes/reportes_personalizados")) { cargar(); return; }
        LocalDate desde = fecha("dpDesde");
        LocalDate hasta = fecha("dpHasta");
        if (desde != null && hasta != null && hasta.isBefore(desde))
            throw new IllegalArgumentException("La fecha final debe ser igual o posterior a la inicial.");
        List<Registro> registros = dao.reportePersonalizado(combo("cmbFuente"),
                combo("cmbAgruparPor"), desde, hasta);
        visibles.setAll(registros);
        String orden = combo("cmbOrden");
        if ("Ascendente".equals(orden)) FXCollections.sort(visibles,
                (a, b) -> a.celdas().get(0).compareTo(b.celdas().get(0)));
        else FXCollections.sort(visibles,
                (a, b) -> b.celdas().get(0).compareTo(a.celdas().get(0)));
        CheckBox totales = nodo("chkIncluirTotales", CheckBox.class);
        if (totales != null && totales.isSelected() && !visibles.isEmpty()) {
            visibles.add(new Registro(0, List.of("TOTAL", "", "",
                    sumar(3).toPlainString(), sumar(4).toPlainString()), null));
        }
        boolean seleccionada = false;
        for (String id : List.of("chkPeriodo", "chkOrigen", "chkConcepto", "chkCantidad", "chkImporteS")) {
            CheckBox check = nodo(id, CheckBox.class);
            seleccionada |= check != null && check.isSelected();
        }
        String[] checks = {"chkPeriodo", "chkOrigen", "chkConcepto", "chkCantidad", "chkImporteS"};
        for (int i = 0; i < checks.length; i++) {
            CheckBox check = nodo(checks[i], CheckBox.class);
            tabla.getColumns().get(i).setVisible(!seleccionada || check == null || check.isSelected());
        }
        tab(1);
    }

    @SuppressWarnings("unchecked")
    private void inicializarCombos() {
        try {
            List<String> categorias = dao.categorias();
            for (String id : List.of("filtroCategoria", "cmbCategoria")) {
                ComboBox<String> caja = nodo(id, ComboBox.class);
                if (caja != null) {
                    caja.getItems().clear();
                    if (id.startsWith("filtro")) caja.getItems().add("Todas las categorías");
                    caja.getItems().addAll(categorias);
                }
            }
        } catch (SQLException error) {
            Alertas.mostrarError("Categorías", error.getMessage());
        }
        opciones("cmbEstado", "Activo", "Inactivo");
        opciones("cmbTipoDeMovimiento", "Entrada", "Salida", "Ajuste");
        opciones("cmbMotivo", "Compra", "Consumo", "Merma", "Corrección");
        opciones("cmbTipo", "Ingreso", "Egreso");
        opciones("cmbConcepto", "Otros ingresos", "Otros egresos", "Ajuste de caja");
        opciones("cmbMetodoDePago", "Efectivo", "Tarjeta", "Transferencia", "Yape");
        opciones("cmbTipoDePago", "Adelanto", "Saldo", "Completo");
        opciones("filtroUmbralDeRevision", "5 unidades", "10 unidades", "20 unidades");
        if (modulo.equals("reportes/reportes_personalizados")) {
            ComboBox<String> fuentes = nodo("cmbFuente", ComboBox.class);
            if (fuentes != null) fuentes.getItems().setAll("Todos", "Alojamiento", "Tienda");
            seleccionarCombo("cmbFuente", "Todos");
            seleccionarCombo("cmbFormato", "PDF");
            seleccionarCombo("cmbAgruparPor", "Día");
        }
        if (modulo.equals("reportes/ocupacion")) {
            try {
                ComboBox<String> tipos = nodo("filtroTipoDeHabitacion", ComboBox.class);
                if (tipos != null) {
                    for (String tipo : dao.tiposHabitacion()) if (!tipos.getItems().contains(tipo)) tipos.getItems().add(tipo);
                }
            } catch (SQLException error) { Alertas.mostrarError("Habitaciones", error.getMessage()); }
        }
        ComboBox<String> roles = nodo("filtroRol", ComboBox.class);
        if (roles != null && !roles.getItems().contains("Limpieza")) roles.getItems().add("Limpieza");
        if (modulo.equals("caja/totales_dia")) {
            try {
                ComboBox<String> responsables = nodo("filtroResponsable", ComboBox.class);
                if (responsables != null) {
                    for (String persona : dao.responsables())
                        if (!responsables.getItems().contains(persona)) responsables.getItems().add(persona);
                }
            } catch (SQLException error) { Alertas.mostrarError("Personal", error.getMessage()); }
        }
    }

    @SuppressWarnings("unchecked")
    private void opciones(String id, String... valores) {
        ComboBox<String> caja = nodo(id, ComboBox.class);
        if (caja != null && caja.getItems().isEmpty()) caja.getItems().addAll(valores);
    }

    @SuppressWarnings("unchecked")
    private void seleccionarCombo(String id, String valor) {
        ComboBox<String> caja = nodo(id, ComboBox.class);
        if (caja != null) caja.setValue(valor);
    }

    private <T> T nodo(String id, Class<T> tipo) {
        Object valor = nodos.get(id);
        return tipo.isInstance(valor) ? tipo.cast(valor) : null;
    }

    private String texto(String id) {
        Object valor = nodos.get(id);
        if (valor instanceof TextField campo) return campo.getText() == null ? "" : campo.getText().trim();
        if (valor instanceof TextArea area) return area.getText() == null ? "" : area.getText().trim();
        return "";
    }

    private void campo(String id, String valor) {
        TextField campo = nodo(id, TextField.class);
        if (campo != null) campo.setText(valor == null ? "" : valor);
    }

    private String combo(String id) {
        ComboBox<?> caja = nodo(id, ComboBox.class);
        return caja == null || caja.getValue() == null ? "" : caja.getValue().toString();
    }

    private LocalDate fecha(String id) {
        DatePicker selector = nodo(id, DatePicker.class);
        return selector == null ? null : selector.getValue();
    }

    private BigDecimal dinero(String id) {
        String valor = texto(id).replace("S/", "").replace(",", ".").trim();
        if (valor.isEmpty() || "—".equals(valor)) throw new IllegalArgumentException("Ingrese " + id + ".");
        try { return new BigDecimal(valor); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Importe inválido en " + id + "."); }
    }

    private int cantidad(String id) {
        Spinner<?> spinner = nodo(id, Spinner.class);
        if (spinner == null) throw new IllegalArgumentException("Falta el campo " + id + ".");
        try { return Integer.parseInt(spinner.getEditor().getText().trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Cantidad inválida en " + id + "."); }
    }

    private String normalizar(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT).replace('Í', 'I');
    }

    private void guardar(String accion) throws SQLException {
        switch (modulo) {
            case "almacen/categorias" -> dao.guardarCategoria(editando, texto("txtNombreDeLaCategoria"));
            case "almacen/precios" -> {
                if (editando == null) throw new IllegalArgumentException("Seleccione un producto de la tabla.");
                dao.cambiarPrecio(editando, dinero("txtNuevoPrecioS"));
            }
            case "almacen/productos" -> dao.guardarProducto(editando,
                    texto("txtCodigoDeBarras"), texto("txtNombreDelProducto"), texto("txtMarca"),
                    combo("cmbCategoria"), dinero("txtPrecioDeVentaS"),
                    editando == null ? cantidad("spnStockInicial") : 0,
                    !"Inactivo".equalsIgnoreCase(combo("cmbEstado")));
            case "almacen/stock" -> {
                exigirFechaActual("dpFecha");
                dao.moverStock(dao.idProductoPorCodigo(texto("txtCodigoDelProducto")),
                        normalizar(combo("cmbTipoDeMovimiento")), cantidad("spnCantidad"),
                        combo("cmbMotivo"), texto("txtReferenciaSustento"), texto("txtObservaciones"));
            }
            case "caja/movimientos" -> {
                exigirFechaActual("dpFecha");
                dao.guardarMovimientoCaja(normalizar(combo("cmbTipo")),
                        combo("cmbConcepto"), normalizar(combo("cmbMetodoDePago")),
                        dinero("txtMontoS"), texto("txtReferenciaSustento"),
                        texto("txtObservaciones"), texto("txtTurnoCaja"));
            }
            case "caja/arqueo" -> {
                exigirFechaActual("dpFecha");
                recalcularArqueo();
                dao.guardarArqueo(texto("txtTurnoCaja"), dinero("txtEfectivoEsperadoS"),
                        dinero("txtTotalContadoS"), texto("txtObservaciones"));
            }
            case "caja/cierre_caja" -> {
                actualizarResumenCierre();
                boolean confirmar = accion.contains("ConfirmarCierre");
                CheckBox revisado = nodo("chkHeRevisadoLosImportesDelCierre", CheckBox.class);
                if (confirmar && (revisado == null || !revisado.isSelected()))
                    throw new IllegalArgumentException("Marque la confirmación después de revisar los importes.");
                LocalDate dia = fecha("dpFechaDelCierre");
                String horaCierre = texto("txtHoraDeCierre");
                if (confirmar && horaCierre.isBlank())
                    throw new IllegalArgumentException("Indique la hora de cierre en formato HH:mm.");
                if (!horaCierre.isBlank()) {
                    try { java.time.LocalTime.parse(horaCierre); }
                    catch (java.time.format.DateTimeParseException error) {
                        throw new IllegalArgumentException("La hora de cierre debe tener formato HH:mm.");
                    }
                }
                String observacion = (horaCierre.isBlank() ? "" : "Hora de cierre: " + horaCierre + "\n")
                        + texto("txtObservacionesDelCierre");
                dao.guardarCierre(editando, new ModulosDAO.CierreDatos(dia, texto("txtTurnoCaja"),
                        dinero("txtFondoInicialS"), dinero("txtCobrosEnEfectivoS"),
                        dinero("txtOtrosIngresosDeEfectivoS"), dinero("txtSalidasDeEfectivoS"),
                        dinero("txtEfectivoEsperadoS"), dinero("txtEfectivoContadoS"),
                        dinero("txtEfectivoEntregadoS"), dinero("txtFondoParaSiguienteTurnoS"),
                        observacion), confirmar);
            }
            case "caja/registrar_pagos" -> {
                LocalDate fechaPago = fecha("dpFechaDelPago");
                if (fechaPago != null && !fechaPago.equals(LocalDate.now()))
                    throw new IllegalArgumentException("Este formulario registra pagos del día actual.");
                dao.registrarPago(codigoReserva(texto("txtCodigoDeReserva")),
                        normalizar(combo("cmbMetodoDePago")), dinero("txtMontoAPagarS"));
            }
            case "usuarios/editar_usuario" -> {
                if (editando == null) throw new IllegalArgumentException("Seleccione primero un usuario.");
                dao.editarUsuario(editando, texto("txtNombres"), texto("txtApellidos"),
                        !"Inactivo".equalsIgnoreCase(combo("cmbEstado")));
            }
            default -> throw new IllegalArgumentException("Este formulario no guarda datos.");
        }
        Alertas.mostrarInfo("Guardado", "La operación se registró correctamente.");
        editando = null;
        tab(0);
        cargar();
    }

    private int codigoReserva(String codigo) {
        String digitos = codigo.replaceAll("\\D", "");
        if (digitos.isEmpty()) throw new IllegalArgumentException("Ingrese el código de la reserva.");
        return Integer.parseInt(digitos);
    }

    private void exigirFechaActual(String id) {
        LocalDate dia = fecha(id);
        if (dia != null && !dia.equals(LocalDate.now()))
            throw new IllegalArgumentException("Esta operación registra la fecha actual. Cambie la fecha a hoy.");
    }

    private void eliminarCategoria() throws SQLException {
        Registro fila = seleccion();
        if (!Alertas.confirmar("Eliminar categoría",
                "¿Eliminar " + fila.celdas().get(1) + "? Solo se permite si no tiene productos.")) return;
        dao.eliminarCategoria(fila.id());
        cargar();
    }

    private void desactivarUsuario() throws SQLException {
        if (editando == null) editarSeleccionado();
        if (!Alertas.confirmar("Desactivar usuario", "¿Desactivar la cuenta seleccionada?")) return;
        dao.editarUsuario(editando, texto("txtNombres"), texto("txtApellidos"), false);
        editando = null;
        tab(0);
        cargar();
    }

    private void configurarCalculos() {
        if (modulo.equals("almacen/stock")) {
            TextField stock = nodo("txtStockActual", TextField.class);
            ComboBox<?> tipo = nodo("cmbTipoDeMovimiento", ComboBox.class);
            Spinner<?> cantidad = nodo("spnCantidad", Spinner.class);
            if (stock != null) stock.textProperty().addListener((obs, antes, ahora) -> recalcularStock());
            if (tipo != null) tipo.valueProperty().addListener((obs, antes, ahora) -> recalcularStock());
            if (cantidad != null) cantidad.getEditor().textProperty()
                    .addListener((obs, antes, ahora) -> recalcularStock());
        }
        if (modulo.equals("almacen/precios")) {
            for (String id : List.of("txtPrecioActualS", "txtNuevoPrecioS")) {
                TextField precio = nodo(id, TextField.class);
                if (precio != null) precio.textProperty().addListener((obs, antes, ahora) -> recalcularPrecio());
            }
        }
        if (modulo.equals("caja/arqueo")) {
            for (var entrada : nodos.entrySet()) {
                if (entrada.getKey().startsWith("spn") && entrada.getValue() instanceof Spinner<?> spinner) {
                    spinner.getEditor().textProperty().addListener((obs, antes, ahora) -> {
                        try { recalcularArqueo(); } catch (IllegalArgumentException ignored) { }
                    });
                }
            }
        }
        if (modulo.equals("caja/cierre_caja")) {
            for (String id : List.of("txtFondoInicialS", "txtCobrosEnEfectivoS",
                    "txtOtrosIngresosDeEfectivoS", "txtSalidasDeEfectivoS", "txtEfectivoContadoS")) {
                TextField campo = nodo(id, TextField.class);
                if (campo != null) campo.textProperty().addListener((obs, antes, ahora) -> recalcularCierre());
            }
            DatePicker fechaCierre = nodo("dpFechaDelCierre", DatePicker.class);
            if (fechaCierre != null) fechaCierre.valueProperty().addListener((obs, antes, ahora) -> {
                try { if (ahora != null) actualizarResumenCierre(); }
                catch (SQLException error) { Alertas.mostrarError("Caja", error.getMessage()); }
            });
            TextField turno = nodo("txtTurnoCaja", TextField.class);
            if (turno != null) turno.focusedProperty().addListener((obs, antes, enfocado) -> {
                if (!enfocado && fecha("dpFechaDelCierre") != null) {
                    try { actualizarResumenCierre(); }
                    catch (SQLException error) { Alertas.mostrarError("Caja", error.getMessage()); }
                }
            });
        }
        if (modulo.equals("caja/registrar_pagos")) {
            ComboBox<?> tipo = nodo("cmbTipoDePago", ComboBox.class);
            if (tipo != null) tipo.setDisable(true); // El tipo depende del saldo real en MySQL.
            TextField monto = nodo("txtMontoAPagarS", TextField.class);
            if (monto != null) monto.textProperty().addListener((obs, antes, ahora) -> recalcularTipoPago());
            TextField codigo = nodo("txtCodigoDeReserva", TextField.class);
            if (codigo != null) {
                codigo.setOnAction(evento -> cargarReservaPago());
                codigo.focusedProperty().addListener((obs, antes, ahora) -> {
                    if (!ahora && !codigo.getText().isBlank()) cargarReservaPago();
                });
            }
        }
    }

    private void cargarReservaPago() {
        try {
            String[] datos = dao.reservaParaPago(codigoReserva(texto("txtCodigoDeReserva")));
            if (datos == null) throw new IllegalArgumentException("No se encontró la reserva.");
            if (List.of("CANCELADA", "FINALIZADA").contains(datos[5]))
                throw new IllegalArgumentException("Esta reserva no acepta nuevos pagos.");
            campo("txtHuesped", datos[0]); campo("txtDocumento", datos[1]);
            campo("txtHabitacion", datos[2]); campo("txtTotalDeLaCuentaS", datos[3]);
            campo("txtSaldoPendienteS", datos[4]);
            campo("txtResponsable", SesionActual.getUsuario().getNombreCompleto());
            recalcularTipoPago();
        } catch (SQLException | IllegalArgumentException error) {
            Alertas.mostrarError("Reserva", error.getMessage());
        }
    }

    private void recalcularStock() {
        int actual = decimalOpcional("txtStockActual").intValue();
        int unidades;
        try { unidades = cantidad("spnCantidad"); }
        catch (IllegalArgumentException error) { campo("txtExistenciaResultante", "—"); return; }
        String tipo = normalizar(combo("cmbTipoDeMovimiento"));
        int resultado = switch (tipo) {
            case "ENTRADA" -> actual + unidades;
            case "SALIDA" -> actual - unidades;
            case "AJUSTE" -> unidades;
            default -> actual;
        };
        campo("txtExistenciaResultante", resultado < 0 ? "Stock insuficiente" : String.valueOf(resultado));
    }

    private void recalcularPrecio() {
        BigDecimal anterior = decimalOpcional("txtPrecioActualS");
        BigDecimal nuevo = decimalOpcional("txtNuevoPrecioS");
        BigDecimal diferencia = nuevo.subtract(anterior);
        campo("txtVariacionS", diferencia.toPlainString());
        campo("txtVariacion", anterior.signum() == 0 ? "—" : diferencia
                .multiply(BigDecimal.valueOf(100)).divide(anterior, 1,
                        java.math.RoundingMode.HALF_UP).toPlainString() + " %");
    }

    private void recalcularTipoPago() {
        BigDecimal saldo = decimalOpcional("txtSaldoPendienteS");
        BigDecimal total = decimalOpcional("txtTotalDeLaCuentaS");
        BigDecimal pago = decimalOpcional("txtMontoAPagarS");
        String tipo = saldo.compareTo(total) == 0
                ? pago.compareTo(saldo) == 0 && pago.signum() > 0 ? "Completo" : "Adelanto"
                : "Saldo";
        seleccionarCombo("cmbTipoDePago", tipo);
    }

    private void recalcularArqueo() {
        BigDecimal billetes = BigDecimal.ZERO;
        BigDecimal monedas = BigDecimal.ZERO;
        String[][] denominaciones = {
            {"spnBilletesDeS200", "200"}, {"spnBilletesDeS100", "100"},
            {"spnBilletesDeS50", "50"}, {"spnBilletesDeS20", "20"},
            {"spnBilletesDeS10", "10"}, {"spnMonedasDeS500", "5"},
            {"spnMonedasDeS200", "2"}, {"spnMonedasDeS100", "1"},
            {"spnMonedasDeS050", "0.50"}, {"spnMonedasDeS020", "0.20"},
            {"spnMonedasDeS010", "0.10"}
        };
        for (int i = 0; i < denominaciones.length; i++) {
            BigDecimal subtotal = new BigDecimal(denominaciones[i][1])
                    .multiply(BigDecimal.valueOf(cantidad(denominaciones[i][0])));
            if (i < 5) billetes = billetes.add(subtotal); else monedas = monedas.add(subtotal);
        }
        BigDecimal contado = billetes.add(monedas);
        campo("txtTotalBilletesS", billetes.toPlainString());
        campo("txtTotalMonedasS", monedas.toPlainString());
        campo("txtTotalContadoS", contado.toPlainString());
        BigDecimal esperado = decimalOpcional("txtEfectivoEsperadoS");
        campo("txtDiferenciaS", contado.subtract(esperado).toPlainString());
    }

    private void recalcularCierre() {
        BigDecimal esperado = decimalOpcional("txtFondoInicialS")
                .add(decimalOpcional("txtCobrosEnEfectivoS"))
                .add(decimalOpcional("txtOtrosIngresosDeEfectivoS"))
                .subtract(decimalOpcional("txtSalidasDeEfectivoS"));
        campo("txtEfectivoEsperadoS", esperado.toPlainString());
        campo("txtDiferenciaS", decimalOpcional("txtEfectivoContadoS").subtract(esperado).toPlainString());
    }

    private void actualizarResumenCierre() throws SQLException {
        BigDecimal[] importes = dao.resumenEfectivo(fecha("dpFechaDelCierre"), texto("txtTurnoCaja"));
        campo("txtFondoInicialS", importes[0].toPlainString());
        campo("txtCobrosEnEfectivoS", importes[1].toPlainString());
        campo("txtOtrosIngresosDeEfectivoS", importes[2].toPlainString());
        campo("txtSalidasDeEfectivoS", importes[3].toPlainString());
        recalcularCierre();
    }

    private BigDecimal decimalOpcional(String id) {
        String valor = texto(id).replace("S/", "").replace(",", ".").trim();
        try { return valor.isBlank() || "—".equals(valor) ? BigDecimal.ZERO : new BigDecimal(valor); }
        catch (NumberFormatException error) { return BigDecimal.ZERO; }
    }

    private void exportarCsv() throws IOException {
        if (visibles.isEmpty()) throw new IllegalArgumentException("No hay registros para exportar.");
        FileChooser selector = new FileChooser();
        selector.setTitle("Exportar datos"); selector.setInitialFileName(nombreArchivo() + ".csv");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo CSV", "*.csv"));
        java.io.File elegido = selector.showSaveDialog(tabla.getScene().getWindow());
        if (elegido == null) return;
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
            destino = destino.resolveSibling(destino.getFileName() + ".csv");
        StringBuilder contenido = new StringBuilder("\uFEFF");
        List<Integer> columnas = columnasVisibles();
        List<String> encabezados = columnas.stream().map(i -> tabla.getColumns().get(i).getText()).toList();
        contenido.append(csv(encabezados)).append('\n');
        for (Registro registro : visibles) contenido.append(csv(columnas.stream()
                .map(i -> registro.celdas().get(i)).toList())).append('\n');
        Files.writeString(destino, contenido.toString(), StandardCharsets.UTF_8);
        Alertas.mostrarInfo("Exportación", "Archivo guardado en:\n" + destino.toAbsolutePath());
    }

    private String csv(List<String> valores) {
        List<String> escapados = new ArrayList<>();
        for (String valor : valores) escapados.add("\"" + valor.replace("\"", "\"\"") + "\"");
        return String.join(";", escapados);
    }

    private void guardarPdf() throws IOException {
        if (visibles.isEmpty()) throw new IllegalArgumentException("No hay registros para imprimir.");
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar informe en PDF"); selector.setInitialFileName(nombreArchivo() + ".pdf");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        java.io.File elegido = selector.showSaveDialog(tabla.getScene().getWindow());
        if (elegido == null) return;
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            destino = destino.resolveSibling(destino.getFileName() + ".pdf");
        String titulo = modulo.equals("reportes/reportes_personalizados")
                && !texto("txtNombreDelReporte").isBlank() ? texto("txtNombreDelReporte") : modulo.replace('/', ' ');
        StringBuilder contenido = new StringBuilder("HOTEL SAN ANTONIO\n")
                .append(titulo.toUpperCase(Locale.ROOT)).append("\n")
                .append("Fecha: ").append(LocalDate.now()).append("\n\n");
        List<Integer> columnas = columnasVisibles();
        contenido.append(String.join(" | ", columnas.stream()
                .map(i -> tabla.getColumns().get(i).getText()).toList()))
                .append('\n');
        for (Registro registro : visibles) contenido.append(String.join(" | ", columnas.stream()
                .map(i -> registro.celdas().get(i)).toList())).append('\n');
        Files.write(destino, ComprobanteController.crearPdf(contenido.toString()));
        Alertas.mostrarInfo("PDF", "Informe guardado en:\n" + destino.toAbsolutePath());
    }

    private List<Integer> columnasVisibles() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < tabla.getColumns().size(); i++)
            if (tabla.getColumns().get(i).isVisible()) indices.add(i);
        return indices;
    }

    private String nombreArchivo() {
        String nombre = modulo.equals("reportes/reportes_personalizados")
                && !texto("txtNombreDelReporte").isBlank() ? texto("txtNombreDelReporte") : modulo;
        return nombre.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private void actualizarMetricas() {
        int total = visibles.size();
        switch (modulo) {
            case "almacen/alertas_stock" -> {
                etiqueta("lblProductosAgotados", String.valueOf(visibles.stream()
                        .filter(r -> numero(r, 3).signum() == 0).count()));
                etiqueta("lblProductosConStockBajo", String.valueOf(total));
                etiqueta("lblUnidadesPorReponer", sumar(5).toPlainString());
            }
            case "almacen/productos" -> {
                etiqueta("lblProductosActivos", String.valueOf(visibles.stream()
                        .filter(r -> r.celdas().get(7).equals("Activo")).count()));
                etiqueta("lblUnidadesEnStock", sumar(6).toPlainString());
                try { etiqueta("lblCategorias", String.valueOf(dao.categorias().size())); }
                catch (SQLException error) { etiqueta("lblCategorias", "—"); }
            }
            case "almacen/stock" -> {
                etiqueta("lblUnidadesDisponibles", sumar(3).toPlainString());
                etiqueta("lblProductosSinStock", String.valueOf(visibles.stream()
                        .filter(r -> numero(r, 3).signum() == 0).count()));
                etiqueta("lblProductosActivos", String.valueOf(visibles.stream()
                        .filter(r -> r.celdas().get(4).equals("Activo")).count()));
            }
            case "caja/arqueo" -> {
                etiqueta("lblEsperadoS", sumar(4).toPlainString());
                etiqueta("lblContadoS", sumar(5).toPlainString());
                etiqueta("lblDiferenciaS", sumar(6).toPlainString());
            }
            case "caja/cierre_caja" -> {
                etiqueta("lblEfectivoEsperadoS", sumar(4).toPlainString());
                etiqueta("lblEfectivoContadoS", sumar(5).toPlainString());
                etiqueta("lblDiferenciaS", sumar(6).toPlainString());
            }
            case "caja/movimientos" -> {
                etiqueta("lblIngresosS", sumar(6).toPlainString());
                etiqueta("lblEgresosS", sumar(7).toPlainString());
                etiqueta("lblNetoS", sumar(6).subtract(sumar(7)).toPlainString());
            }
            case "caja/registrar_pagos" -> {
                etiqueta("lblTotalCobradoS", sumar(7).toPlainString());
                etiqueta("lblPagosRegistrados", String.valueOf(total));
                etiqueta("lblSaldoDeLaCuentaS", texto("txtSaldoPendienteS"));
            }
            case "caja/totales_dia" -> {
                etiqueta("lblAlojamientoS", sumar(1).toPlainString());
                etiqueta("lblTiendaS", sumar(2).toPlainString());
                etiqueta("lblCobrosDelDiaS", sumar(3).toPlainString());
            }
            case "reportes/ingresos" -> {
                etiqueta("lblCobrosDeAlojamientoS", sumaPorOrigen("Alojamiento").toPlainString());
                etiqueta("lblCobrosDeTiendaS", sumaPorOrigen("Tienda").toPlainString());
                etiqueta("lblTotalCobradoS", sumar(4).toPlainString());
            }
            case "reportes/ocupacion" -> {
                etiqueta("lblNochesDisponibles", sumar(2).toPlainString());
                etiqueta("lblNochesOcupadas", sumar(3).toPlainString());
                BigDecimal disponible = sumar(2);
                etiqueta("lblOcupacion", disponible.signum() == 0 ? "0 %" : sumar(3)
                        .multiply(BigDecimal.valueOf(100)).divide(disponible, 1,
                                java.math.RoundingMode.HALF_UP) + " %");
            }
            case "reportes/ventas" -> {
                etiqueta("lblVentasRegistradas", String.valueOf(total));
                etiqueta("lblUnidadesVendidas", sumar(3).toPlainString());
                etiqueta("lblImporteVendidoS", sumar(5).toPlainString());
            }
            default -> { }
        }
    }

    private BigDecimal sumaPorOrigen(String origen) {
        BigDecimal total = BigDecimal.ZERO;
        for (Registro r : visibles) if (r.celdas().get(1).equals(origen)) total = total.add(numero(r, 4));
        return total;
    }

    private BigDecimal sumar(int columna) {
        BigDecimal total = BigDecimal.ZERO;
        for (Registro r : visibles) total = total.add(numero(r, columna));
        return total;
    }

    private BigDecimal numero(Registro fila, int columna) {
        if (columna >= fila.celdas().size()) return BigDecimal.ZERO;
        try { return new BigDecimal(fila.celdas().get(columna).replace(",", ".")); }
        catch (NumberFormatException error) { return BigDecimal.ZERO; }
    }

    private void etiqueta(String id, String valor) {
        Label label = nodo(id, Label.class);
        if (label != null) label.setText(valor == null || valor.isBlank() ? "—" : valor);
    }

    @SuppressWarnings("unchecked")
    private void actualizarGrafico() {
        BarChart<String, Number> grafico = nodo("graficoResumen", BarChart.class);
        if (grafico == null) return;
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        int limite = Math.min(visibles.size(), 10);
        for (int i = 0; i < limite; i++) {
            Registro fila = visibles.get(i);
            String nombre = fila.celdas().size() > 1 ? fila.celdas().get(1) : String.valueOf(i + 1);
            if (nombre.length() > 18) nombre = nombre.substring(0, 18);
            serie.getData().add(new XYChart.Data<>(nombre, numero(fila, fila.celdas().size() - 1)));
        }
        grafico.getData().setAll(serie);
    }
}
