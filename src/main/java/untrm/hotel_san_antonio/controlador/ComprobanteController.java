/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 *
 * @author YAXON
 */
public class ComprobanteController {
    @FXML private ComboBox<String> cmbOrigen;
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
    @FXML private TableView<?> tblDetalle; 
    @FXML private Label lblTotal;
    @FXML private Label lblPagado;
    @FXML private Label lblSaldo;
    @FXML private Button btnVistaPrevia;
    @FXML private Button btnImprimir;
    @FXML private Button btnCancelar;
    @FXML private Button btnGenerar;

  
    @FXML
    public void initialize() {
        //FALTA COMPLETAR...
    }
}
