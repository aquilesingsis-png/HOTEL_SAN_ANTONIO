package untrm.hotel_san_antonio.controlador.Reserva;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.modelo.Huesped;

/** Carga únicamente filas dinámicas a partir del FXML editable en Scene Builder. */
public class HuespedesAdicionales {
    private static final String FXML = "/untrm/hotel_san_antonio/fxml/Reserva/huesped_adicional.fxml";
    private final VBox contenedor;
    private final List<HuespedAdicionalController> controles = new ArrayList<>();

    public HuespedesAdicionales(VBox contenedor) {
        this.contenedor = contenedor;
    }

    public void actualizar(int totalHuespedes) {
        int cantidad = Math.max(0, totalHuespedes - 1);
        while (controles.size() > cantidad) {
            controles.remove(controles.size() - 1);
            contenedor.getChildren().remove(contenedor.getChildren().size() - 1);
        }
        while (controles.size() < cantidad) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML));
                Parent fila = loader.load();
                HuespedAdicionalController control = loader.getController();
                control.numerar(controles.size() + 2);
                controles.add(control);
                contenedor.getChildren().add(fila);
            } catch (IOException error) {
                throw new IllegalStateException("No se pudo cargar el formulario de acompañantes.", error);
            }
        }
        contenedor.setVisible(cantidad > 0);
        contenedor.setManaged(cantidad > 0);
    }

    public List<Huesped> obtener(Huesped titular) {
        List<Huesped> todos = new ArrayList<>();
        todos.add(titular);
        for (HuespedAdicionalController control : controles) {
            todos.add(control.obtener());
        }
        return todos;
    }
}
