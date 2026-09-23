package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import untrm.hotel_san_antonio.modelo.Habitacion;

import java.util.function.Consumer;

/**
 * Controlador de tarjeta_habitacion.fxml: muestra una habitacion y avisa cuando se hace clic.
 * Sin archivo .css: el color de fondo/borde segun el estado y el resaltado al pasar el mouse
 * se calculan aqui y se aplican con setStyle()/setEffect() (la vista queda solo con la estructura).
 */
public class TarjetaHabitacionController {

    private static final DropShadow SOMBRA_HOVER = new DropShadow(10, 0, 3, Color.rgb(59, 33, 15, 0.22));

    private static final String BASE = "-fx-padding: 14px 16px; -fx-min-width: 180px; -fx-pref-width: 180px; "
            + "-fx-cursor: hand; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-border-width: 2px; ";

    @FXML private VBox raiz;
    @FXML private Label lblNumero;
    @FXML private Label lblTipo;
    @FXML private Circle puntoEstado;
    @FXML private Label lblEstado;
    @FXML private Label lblHuesped;

    private Habitacion habitacion;
    private Consumer<Habitacion> alHacerClic;

    @FXML
    private void initialize() {
        raiz.setOnMouseEntered(e -> raiz.setEffect(SOMBRA_HOVER));
        raiz.setOnMouseExited(e -> raiz.setEffect(null));
    }

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

        raiz.setStyle(BASE + estiloColores(h.getEstado()));
        puntoEstado.setFill(Color.web(colorEstado(h.getEstado())));
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

    private String estiloColores(String estado) {
        String fondo, borde;
        switch (estado) {
            case "DISPONIBLE": fondo = "#DCFCE7"; borde = "#22C55E"; break;
            case "OCUPADA": fondo = "#FEE2E2"; borde = "#EF4444"; break;
            case "LIMPIEZA": fondo = "#FEF3C7"; borde = "#F59E0B"; break;
            default: fondo = "#E2E8F0"; borde = "#64748B"; break;
        }
        return "-fx-background-color: " + fondo + "; -fx-border-color: " + borde + ";";
    }

    private String colorEstado(String estado) {
        switch (estado) {
            case "DISPONIBLE": return "#22C55E";
            case "OCUPADA": return "#EF4444";
            case "LIMPIEZA": return "#F59E0B";
            default: return "#64748B";
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
