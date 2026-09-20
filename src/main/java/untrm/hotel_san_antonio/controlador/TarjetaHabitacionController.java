package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.modelo.Habitacion;

import java.util.function.Consumer;

/** Controlador de tarjeta_habitacion.fxml: muestra una habitacion y avisa cuando se hace clic. */
public class TarjetaHabitacionController {

    @FXML private VBox raiz;
    @FXML private Label lblNumero;
    @FXML private Label lblTipo;
    @FXML private Label lblEstado;
    @FXML private Label lblHuesped;

    private Habitacion habitacion;
    private Consumer<Habitacion> alHacerClic;

    public void setHabitacion(Habitacion h, Consumer<Habitacion> alHacerClic) {
        this.habitacion = h;
        this.alHacerClic = alHacerClic;

        lblNumero.setText(h.getNumero());
        int capacidad = h.getTipo().getCapacidad();
        lblTipo.setText(h.getTipo().getNombre() + " · " + capacidad + (capacidad == 1 ? " persona" : " personas"));
        lblEstado.setText(textoEstado(h.getEstado()));

        String textoHuesped = "";
        if ("OCUPADA".equals(h.getEstado()) && h.getHuespedActual() != null) {
            textoHuesped = h.getHuespedActual();
        } else if ("DISPONIBLE".equals(h.getEstado()) && h.getReservaHoy() != null) {
            textoHuesped = "Llega hoy: " + h.getReservaHoy();
        }
        boolean conHuesped = !textoHuesped.isEmpty();
        lblHuesped.setText(textoHuesped);
        lblHuesped.setVisible(conHuesped);
        lblHuesped.setManaged(conHuesped);

        raiz.getStyleClass().removeIf(c -> c.startsWith("habitacion-"));
        raiz.getStyleClass().add(claseEstado(h.getEstado()));
    }

    public VBox getRaiz() {
        return raiz;
    }

    @FXML
    private void onClic() {
        if (alHacerClic != null && habitacion != null) {
            alHacerClic.accept(habitacion);
        }
    }

    private String claseEstado(String estado) {
        switch (estado) {
            case "DISPONIBLE": return "habitacion-disponible";
            case "OCUPADA": return "habitacion-ocupada";
            case "LIMPIEZA": return "habitacion-limpieza";
            default: return "habitacion-mantenimiento";
        }
    }

    private String textoEstado(String estado) {
        switch (estado) {
            case "DISPONIBLE": return "Disponible";
            case "OCUPADA": return "Ocupada";
            case "LIMPIEZA": return "Limpieza";
            default: return "Mantenimiento";
        }
    }
}
