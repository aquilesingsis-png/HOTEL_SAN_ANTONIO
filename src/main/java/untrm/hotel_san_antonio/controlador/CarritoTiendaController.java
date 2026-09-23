package untrm.hotel_san_antonio.controlador;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import untrm.hotel_san_antonio.dao.CategoriaDAO;
import untrm.hotel_san_antonio.dao.ProductoDAO;
import untrm.hotel_san_antonio.modelo.Categoria;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.Producto;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.servicio.VentaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.SesionActual;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;

public class CarritoTiendaController {
    @FXML private TextField txtBuscar, txtBuscarDni, txtDni, txtNombreCliente, txtDireccion, txtTelefono, txtMontoRecibido;
    @FXML private TextArea txtObservaciones;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private ComboBox<EstadiaActiva> cmbHabitacion;
    @FXML private ToggleButton btnBoleta, btnFactura, btnEfectivo, btnTarjeta, btnYape, btnTransferencia;
    @FXML private ToggleGroup tipoComprobante, formaPago;
    @FXML private Label lblDatosCliente, lblSubtotal, lblIgv, lblTotal, lblVuelto, lblMostrando;

    @FXML private TableView<Producto> tablaProductos;
    @FXML private TableColumn<Producto,String> colCodigo, colProducto, colCategoria;
    @FXML private TableColumn<Producto,BigDecimal> colPrecio;
    @FXML private TableColumn<Producto,Integer> colStock;
    @FXML private TableColumn<Producto,Void> colAcciones;

    @FXML private TableView<DetalleVenta> tablaCarrito;
    @FXML private TableColumn<DetalleVenta,String> colCarritoProducto;
    @FXML private TableColumn<DetalleVenta,Integer> colCantidad;
    @FXML private TableColumn<DetalleVenta,BigDecimal> colCarritoPrecio, colSubtotal;

    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final VentaService ventaService = new VentaService();
    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final ObservableList<DetalleVenta> carrito = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarTablas();
        cargarCategorias();
        cargarHabitaciones();
        cargarProductos();
        actualizarTotales();

        txtBuscar.textProperty().addListener((o,a,n) -> cargarProductos());
        cmbCategoria.valueProperty().addListener((o,a,n) -> cargarProductos());
        txtMontoRecibido.textProperty().addListener((o,a,n) -> actualizarVuelto());
        tipoComprobante.selectedToggleProperty().addListener((o,a,n) -> actualizarTipoComprobante());
        formaPago.selectedToggleProperty().addListener((o,a,n) -> actualizarFormaPago());
        actualizarTipoComprobante();
        actualizarFormaPago();
    }

    private void configurarTablas() {
        tablaProductos.setItems(productos);
        tablaCarrito.setItems(carrito);
        colCodigo.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.format("P%03d", c.getValue().getIdProducto())));
        colProducto.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getNombre()));
        colCategoria.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getNombre()));
        colPrecio.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getPrecio()));
        colStock.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStock()));
        formatearMoneda(colPrecio);

        colAcciones.setCellFactory(col -> new TableCell<Producto,Void>() {
            private final Button boton = new Button("Agregar");
            { boton.getStyleClass().add("btn-agregar-tabla"); boton.setOnAction(e -> agregarProducto(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : boton); }
        });

        colCarritoProducto.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getNombreProducto()));
        colCantidad.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getCantidad()));
        colCarritoPrecio.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getPrecioUnitario()));
        colSubtotal.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getSubtotal()));
        formatearMoneda(colCarritoPrecio);
        formatearMoneda(colSubtotal);
    }

    private <S> void formatearMoneda(TableColumn<S,BigDecimal> columna) {
        columna.setCellFactory(c -> new TableCell<S,BigDecimal>() {
            @Override protected void updateItem(BigDecimal valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : "S/ " + valor.setScale(2, RoundingMode.HALF_UP));
            }
        });
    }

    private void cargarCategorias() {
        try {
            ObservableList<Categoria> lista = FXCollections.observableArrayList();
            lista.add(new Categoria(0, "Todas las categorías"));
            lista.addAll(categoriaDAO.listar());
            cmbCategoria.setItems(lista); // se asigna una sola vez: evita categorías duplicadas
            cmbCategoria.getSelectionModel().selectFirst();
        } catch (SQLException e) { Alertas.mostrarError("Tiendita", "No se pudieron cargar las categorías.\n" + e.getMessage()); }
    }

    private void cargarProductos() {
        try {
            Categoria c = cmbCategoria.getValue();
            Integer id = c == null || c.getIdCategoria() == 0 ? null : c.getIdCategoria();
            productos.setAll(productoDAO.buscarActivos(txtBuscar.getText(), id));
            lblMostrando.setText("Mostrando 1 - " + productos.size() + " de " + productos.size() + " productos");
        } catch (SQLException e) { Alertas.mostrarError("Tiendita", "No se pudieron cargar los productos.\n" + e.getMessage()); }
    }

    private void cargarHabitaciones() {
        ObservableList<EstadiaActiva> lista = FXCollections.observableArrayList();
        String sql = "SELECT r.id_huesped,r.id_habitacion,h.numero,CONCAT(hu.nombres,' ',hu.apellidos) huesped FROM reserva r INNER JOIN habitacion h ON h.id_habitacion=r.id_habitacion INNER JOIN huesped hu ON hu.id_huesped=r.id_huesped WHERE r.estado='CHECKIN' ORDER BY h.numero";
        try (Connection cn=ConexionBD.conectar(); PreparedStatement ps=cn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) lista.add(new EstadiaActiva(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)));
            cmbHabitacion.setItems(lista);
            cmbHabitacion.setConverter(new StringConverter<EstadiaActiva>() { public String toString(EstadiaActiva e){return e==null?"":e.toString();} public EstadiaActiva fromString(String s){return null;} });
        } catch(SQLException e) { Alertas.mostrarError("Tiendita", "No se pudieron cargar las habitaciones ocupadas.\n"+e.getMessage()); }
    }

    @FXML private void onBuscarProducto(){ cargarProductos(); }
    @FXML private void onBuscarCliente(){ Alertas.mostrarInfo("Cliente", "La consulta automática por DNI no se implementará en esta etapa. Puedes ingresar los datos manualmente."); }

    private void actualizarTipoComprobante(){ lblDatosCliente.setText(btnFactura.isSelected()?"Datos del cliente (Factura)":"Datos del cliente (Boleta)"); }
    private void actualizarFormaPago(){
        boolean efectivo=btnEfectivo.isSelected();
        txtMontoRecibido.setDisable(!efectivo);
        if(!efectivo){ txtMontoRecibido.clear(); lblVuelto.setText("S/ 0.00"); }
    }

    private void agregarProducto(Producto p) {
        if(p.getStock()<=0){ Alertas.mostrarInfo("Stock", "Este producto no tiene stock disponible."); return; }
        DetalleVenta d=buscarDetalle(p.getIdProducto());
        if(d==null){ d=new DetalleVenta(); d.setIdProducto(p.getIdProducto()); d.setNombreProducto(p.getNombre()); d.setCantidad(1); d.setPrecioUnitario(p.getPrecio()); d.setSubtotal(p.getPrecio()); carrito.add(d); }
        else { if(d.getCantidad()>=p.getStock()){Alertas.mostrarInfo("Stock","No hay más unidades disponibles.");return;} d.setCantidad(d.getCantidad()+1); recalcular(d); tablaCarrito.refresh(); }
        actualizarTotales();
    }

    @FXML private void onSumarCantidad(){ DetalleVenta d=tablaCarrito.getSelectionModel().getSelectedItem(); if(d==null)return; Producto p=buscarProducto(d.getIdProducto()); if(p!=null && d.getCantidad()>=p.getStock())return; d.setCantidad(d.getCantidad()+1); recalcular(d); tablaCarrito.refresh(); actualizarTotales(); }
    @FXML private void onRestarCantidad(){ DetalleVenta d=tablaCarrito.getSelectionModel().getSelectedItem(); if(d==null)return; if(d.getCantidad()<=1)carrito.remove(d); else {d.setCantidad(d.getCantidad()-1);recalcular(d);} tablaCarrito.refresh();actualizarTotales(); }
    @FXML private void onQuitarProducto(){ DetalleVenta d=tablaCarrito.getSelectionModel().getSelectedItem(); if(d!=null){carrito.remove(d);actualizarTotales();} }
    @FXML private void onVaciarCarrito(){ if(!carrito.isEmpty() && Alertas.confirmar("Vaciar carrito","¿Deseas retirar todos los productos?")){carrito.clear();actualizarTotales();} }
    @FXML private void onCancelar(){ if(carrito.isEmpty() || Alertas.confirmar("Cancelar","¿Deseas cancelar la venta actual?")){carrito.clear();limpiarCliente();actualizarTotales();} }

    @FXML private void onConfirmarVenta(){
        // Validaciones sencillas antes de registrar la venta.
        // Se muestran una por una para que el usuario sepa exactamente qué debe corregir.
        String dni = txtDni.getText() == null ? "" : txtDni.getText().trim();
        String nombre = txtNombreCliente.getText() == null ? "" : txtNombreCliente.getText().trim();

        if (dni.isEmpty()) {
            Alertas.mostrarInfo("Datos incompletos", "Ingrese el DNI del cliente.");
            txtDni.requestFocus();
            return;
        }
        if (!dni.matches("\\d{8}")) {
            Alertas.mostrarInfo("DNI inválido", "El DNI debe contener exactamente 8 dígitos.");
            txtDni.requestFocus();
            return;
        }
        if (nombre.isEmpty()) {
            Alertas.mostrarInfo("Datos incompletos", "Ingrese el nombre completo del cliente.");
            txtNombreCliente.requestFocus();
            return;
        }
        if(carrito.isEmpty()){
            Alertas.mostrarInfo("Carrito vacío","Agrega al menos un producto antes de emitir el comprobante.");
            return;
        }
        if(SesionActual.getUsuario()==null){
            Alertas.mostrarError("Sesión","No existe un usuario autenticado.");
            return;
        }
        if (formaPago.getSelectedToggle() == null) {
            Alertas.mostrarInfo("Forma de pago", "Seleccione una forma de pago.");
            return;
        }
        if(btnEfectivo.isSelected()) {
            String montoTexto = txtMontoRecibido.getText() == null ? "" : txtMontoRecibido.getText().replace("S/", "").trim();
            if (montoTexto.isEmpty()) {
                Alertas.mostrarInfo("Pago incompleto", "Ingrese el monto recibido.");
                txtMontoRecibido.requestFocus();
                return;
            }
            try {
                BigDecimal monto = new BigDecimal(montoTexto);
                if (monto.compareTo(totalCarrito()) < 0) {
                    Alertas.mostrarInfo("Pago insuficiente", "El monto recibido es menor al total de la venta.");
                    txtMontoRecibido.requestFocus();
                    return;
                }
            } catch (NumberFormatException ex) {
                Alertas.mostrarInfo("Monto inválido", "Ingrese un monto recibido válido, por ejemplo: 20.00");
                txtMontoRecibido.requestFocus();
                return;
            }
        }
        VentaTienda venta=new VentaTienda(); venta.setIdUsuario(SesionActual.getUsuario().getIdUsuario()); venta.setIdComprobante(null);
        EstadiaActiva e=cmbHabitacion.getValue();
        if(e!=null){venta.setIdHuesped(e.idHuesped);venta.setIdHabitacion(e.idHabitacion);venta.setClienteExterno(null);} else {venta.setIdHuesped(null);venta.setIdHabitacion(null);String n=txtNombreCliente.getText()==null?"":txtNombreCliente.getText().trim();venta.setClienteExterno(n.isEmpty()?"Cliente varios":n);}
        if(!Alertas.confirmar("Emitir comprobante","Total: "+lblTotal.getText()+"\n¿Registrar la venta?"))return;
        try {int id=ventaService.registrarVenta(venta,new ArrayList<>(carrito));Alertas.mostrarInfo("Venta registrada","Venta N.º "+id+" registrada correctamente.");carrito.clear();limpiarCliente();actualizarTotales();cargarProductos();}
        catch(Exception ex){Alertas.mostrarError("Venta",ex.getMessage());}
    }

    private void actualizarTotales(){
        BigDecimal total=totalCarrito();
        BigDecimal subtotal=total.divide(new BigDecimal("1.18"),2,RoundingMode.HALF_UP);
        BigDecimal igv=total.subtract(subtotal);
        lblSubtotal.setText(moneda(subtotal)); lblIgv.setText(moneda(igv)); lblTotal.setText(moneda(total)); actualizarVuelto();
    }
    private void actualizarVuelto(){ BigDecimal vuelto=obtenerMontoRecibido().subtract(totalCarrito()); if(vuelto.signum()<0)vuelto=BigDecimal.ZERO; lblVuelto.setText(moneda(vuelto)); }
    private BigDecimal obtenerMontoRecibido(){ try{return new BigDecimal(txtMontoRecibido.getText().replace("S/","").trim());}catch(Exception e){return BigDecimal.ZERO;} }
    private BigDecimal totalCarrito(){ BigDecimal t=BigDecimal.ZERO; for(DetalleVenta d:carrito)if(d.getSubtotal()!=null)t=t.add(d.getSubtotal()); return t.setScale(2,RoundingMode.HALF_UP); }
    private String moneda(BigDecimal v){return "S/ "+v.setScale(2,RoundingMode.HALF_UP);}
    private void recalcular(DetalleVenta d){d.setSubtotal(d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())));}
    private DetalleVenta buscarDetalle(int id){for(DetalleVenta d:carrito)if(d.getIdProducto()==id)return d;return null;}
    private Producto buscarProducto(int id){for(Producto p:productos)if(p.getIdProducto()==id)return p;return null;}
    private void limpiarCliente(){txtBuscarDni.clear();txtDni.clear();txtNombreCliente.clear();txtDireccion.clear();txtTelefono.clear();txtObservaciones.clear();txtMontoRecibido.clear();cmbHabitacion.getSelectionModel().clearSelection();}
    @FXML private void onCerrar(){((Stage)tablaCarrito.getScene().getWindow()).close();}

    private static class EstadiaActiva { final int idHuesped,idHabitacion; final String numero,huesped; EstadiaActiva(int h,int hab,String n,String hu){idHuesped=h;idHabitacion=hab;numero=n;huesped=hu;} public String toString(){return "Hab. "+numero+" — "+huesped;} }
}
