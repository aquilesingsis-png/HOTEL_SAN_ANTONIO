package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import untrm.hotel_san_antonio.util.Alertas;

/**
 * Pantalla que reune varias pantallas de Caja como pestanas (Movimientos y totales, Cierre y arqueo).
 * El FXML dice en el userData de cada pestana que modulo muestra; cada una se carga la primera vez
 * que se abre, para no consultar todo de golpe.
 */
public class PestanasModulosController {

    @FXML private TabPane pestanas;

    @FXML
    public void initialize() {
        pestanas.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> cargar(ahora));
        cargar(pestanas.getSelectionModel().getSelectedItem());
    }

    private void cargar(Tab pestana) {
        if (pestana == null || pestana.getContent() != null || !(pestana.getUserData() instanceof String modulo)) {
            return;
        }
        try {
            pestana.setContent(ModulosNuevosController.cargarModulo(modulo));
        } catch (IOException | RuntimeException error) {
            Alertas.mostrarError("Caja", "No se pudo abrir \"" + pestana.getText() + "\".\n\n" + error.getMessage());
        }
    }
}
