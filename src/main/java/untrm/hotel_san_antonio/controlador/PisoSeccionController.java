package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Controlador de piso_seccion.fxml: titulo del piso y contenedor de sus tarjetas. */
public class PisoSeccionController {

    @FXML private VBox raiz;
    @FXML private Label lblTitulo;
    @FXML private Label lblCantidad;
    @FXML private FlowPane tarjetas;

    /** Prepara la seccion (descarta la tarjeta de muestra del FXML). */
    public void iniciar(int piso, int cantidad) {
        lblTitulo.setText("Piso " + piso);
        lblCantidad.setText(cantidad + (cantidad == 1 ? " habitación" : " habitaciones"));
        tarjetas.getChildren().clear();
    }

    public void agregarTarjeta(Node tarjeta) {
        tarjetas.getChildren().add(tarjeta);
    }

    public VBox getRaiz() {
        return raiz;
    }
}
