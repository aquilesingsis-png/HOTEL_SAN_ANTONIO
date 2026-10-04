package untrm.hotel_san_antonio.controlador;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Pantalla Reservas: reune el Calendario y la Lista con un solo buscador (cliente, DNI, codigo o
 * habitacion). Crear una reserva se hace en "Nueva reserva"; aqui se consultan, se cancelan y se
 * cambian de habitacion (desde la ventana de detalle).
 */
public class ReservasController {

    @FXML private TextField txtBuscar;
    @FXML private ToggleButton btnCalendario;
    @FXML private ToggleButton btnLista;
    @FXML private ToggleGroup grupoVista;
    @FXML private VBox calendario;
    @FXML private VBox lista;
    @FXML private CalendarioOcupacionController calendarioController;
    @FXML private ReservasProgramadasController listaController;

    private final PauseTransition espera = new PauseTransition(Duration.millis(300));

    @FXML
    public void initialize() {
        grupoVista.selectedToggleProperty().addListener((observable, anterior, actual) -> {
            if (actual == null && anterior != null) {
                anterior.setSelected(true);
            }
        });
        // se busca cuando el usuario deja de escribir un momento, no con cada letra
        espera.setOnFinished(evento -> {
            calendarioController.buscarTexto(txtBuscar.getText());
            listaController.buscarTexto(txtBuscar.getText());
        });
        txtBuscar.textProperty().addListener((observable, anterior, actual) -> espera.playFromStart());
        mostrarCalendario();
    }

    @FXML
    private void mostrarCalendario() {
        btnCalendario.setSelected(true);
        cambiarVista(true);
    }

    @FXML
    private void mostrarLista() {
        btnLista.setSelected(true);
        cambiarVista(false);
    }

    private void cambiarVista(boolean calendarioVisible) {
        calendario.setVisible(calendarioVisible);
        calendario.setManaged(calendarioVisible);
        lista.setVisible(!calendarioVisible);
        lista.setManaged(!calendarioVisible);
    }
}
