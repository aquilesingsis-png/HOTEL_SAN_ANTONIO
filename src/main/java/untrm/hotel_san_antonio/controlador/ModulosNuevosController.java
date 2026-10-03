package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
        cargar();
    }

    private void ejecutar(String accion) {
        try {
            if (accion.contains("FiltroLimpiar")) { limpiarFiltros(); cargar(); return; }
            if (accion.startsWith("btnFiltro") || accion.contains("Actualizar")
                    || accion.contains("Consultar")) {
                cargar(); return;
            }
            if (accion.contains("ListaNuevo")) { nuevo(); return; }
            if (accion.contains("ListaEditar")) {
                editarSeleccionado(); return;
            }
            if (accion.contains("VistaPrevia")) { tab(1); return; }
            if (accion.contains("VerDetalle")) {
                verDetalle(); return;
            }
            if (accion.contains("ListaExportar")) { exportarCsv(); return; }
            if (accion.contains("ListaImprimir")) { guardarPdf(); return; }
            if (accion.contains("FormCancelar")) { tab(0); return; }
            if (accion.contains("FormLimpiar")) {
                limpiarFormulario();
                return;
            }
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
            List<Registro> registros = dao.consultar(modulo);
            List<Registro> filtrados = registros.stream().filter(this::coincideFiltros).toList();
            visibles.setAll(filtrados);
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
                        || id.contains("Agrupar")) continue;
                if (id.equals("filtroEstado") && modulo.equals("usuarios/editar_usuario")) {
                    if (!fila.celdas().get(5).equalsIgnoreCase(elegido)) return false;
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
    }

    private void nuevo() throws SQLException {
        editando = null;
        limpiarFormulario();
        String nombre = SesionActual.getUsuario().getNombreCompleto();
        campo("txtResponsable", nombre);
        campo("txtTurnoCaja", "Mañana");
        DatePicker fecha = nodo("dpFecha", DatePicker.class);
        if (fecha != null) fecha.setValue(LocalDate.now());
        DatePicker cierre = nodo("dpFechaDelCierre", DatePicker.class);
        if (cierre != null) cierre.setValue(LocalDate.now());
        DatePicker pago = nodo("dpFechaDelPago", DatePicker.class);
        if (pago != null) pago.setValue(LocalDate.now());
        tab(1);
    }

    private void editarSeleccionado() {
        Registro fila = seleccion();
        editando = fila.id();
        List<String> c = fila.celdas();
        switch (modulo) {
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

    @SuppressWarnings("unchecked")
    private void inicializarCombos() {
        opciones("cmbEstado", "Activo", "Inactivo");
        opciones("cmbTipo", "Ingreso", "Egreso");
        opciones("cmbConcepto", "Otros ingresos", "Otros egresos", "Ajuste de caja");
        opciones("cmbMetodoDePago", "Efectivo", "Tarjeta", "Transferencia", "Yape");
        opciones("cmbTipoDePago", "Adelanto", "Saldo", "Completo");
        ComboBox<String> roles = nodo("filtroRol", ComboBox.class);
        if (roles != null && !roles.getItems().contains("Limpieza")) roles.getItems().add("Limpieza");
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

    private void guardar(String accion) throws SQLException {
        switch (modulo) {
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

    private void desactivarUsuario() throws SQLException {
        if (editando == null) editarSeleccionado();
        if (!Alertas.confirmar("Desactivar usuario", "¿Desactivar la cuenta seleccionada?")) return;
        dao.editarUsuario(editando, texto("txtNombres"), texto("txtApellidos"), false);
        editando = null;
        tab(0);
        cargar();
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
        String titulo = modulo.replace('/', ' ');
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
        return modulo.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
