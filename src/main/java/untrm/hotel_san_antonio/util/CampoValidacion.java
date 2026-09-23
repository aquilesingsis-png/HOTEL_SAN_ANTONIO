package untrm.hotel_san_antonio.util;

import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Marca en rojo el campo que tiene un dato incorrecto (en vez de mostrar un mensaje aparte).
 * El motivo queda como ayuda al pasar el mouse por encima. No usa un archivo .css: el borde
 * rojo se agrega/quita directamente sobre el estilo inline del control (control.getStyle()).
 */
public final class CampoValidacion {

    private static final String ESTILO_INVALIDO = "-fx-border-color: #DC2626; -fx-border-width: 1.5px;";

    private CampoValidacion() {
    }

    public static void marcar(Control campo, String motivo) {
        String estiloActual = campo.getStyle() == null ? "" : campo.getStyle();
        if (!estiloActual.contains(ESTILO_INVALIDO)) {
            campo.setStyle(estiloActual + " " + ESTILO_INVALIDO);
        }
        Tooltip ayuda = new Tooltip(motivo);
        ayuda.setShowDelay(Duration.millis(150));
        campo.setTooltip(ayuda);
    }

    public static void limpiar(Control campo) {
        String estiloActual = campo.getStyle();
        if (estiloActual != null && estiloActual.contains(ESTILO_INVALIDO)) {
            campo.setStyle(estiloActual.replace(" " + ESTILO_INVALIDO, "").replace(ESTILO_INVALIDO, ""));
        }
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
