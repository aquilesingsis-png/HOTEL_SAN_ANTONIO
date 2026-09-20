package untrm.hotel_san_antonio.util;

import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Marca en rojo el campo que tiene un dato incorrecto (en vez de mostrar un mensaje aparte).
 * El motivo queda como ayuda al pasar el mouse por encima. Necesita la clase CSS "campo-invalido".
 */
public final class CampoValidacion {

    private static final String CLASE = "campo-invalido";

    private CampoValidacion() {
    }

    public static void marcar(Control campo, String motivo) {
        if (!campo.getStyleClass().contains(CLASE)) {
            campo.getStyleClass().add(CLASE);
        }
        Tooltip ayuda = new Tooltip(motivo);
        ayuda.setShowDelay(Duration.millis(150));
        campo.setTooltip(ayuda);
    }

    public static void limpiar(Control campo) {
        campo.getStyleClass().remove(CLASE);
        campo.setTooltip(null);
    }

    public static void limpiar(Control... campos) {
        for (Control campo : campos) {
            limpiar(campo);
        }
    }

    /** Quita la marca roja apenas la persona vuelve a editar ese campo. */
    public static void limpiarAlEditar(Control... campos) {
        for (Control campo : campos) {
            if (campo instanceof TextInputControl) {
                ((TextInputControl) campo).textProperty().addListener((obs, antes, ahora) -> limpiar(campo));
            } else if (campo instanceof ComboBoxBase) {
                ((ComboBoxBase<?>) campo).valueProperty().addListener((obs, antes, ahora) -> limpiar(campo));
            } else if (campo instanceof Spinner) {
                ((Spinner<?>) campo).valueProperty().addListener((obs, antes, ahora) -> limpiar(campo));
            }
        }
    }
}
