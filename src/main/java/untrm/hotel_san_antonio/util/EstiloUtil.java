package untrm.hotel_san_antonio.util;

import javafx.scene.control.Control;

/**
 * Ayudante para los pocos casos en que hace falta tocar una pieza interna de un control
 * (por ejemplo, el "viewport" de un ScrollPane o el "arrow" de un ComboBox) para que se vea
 * bien sin un archivo .css. Esa pieza interna solo existe una vez que el control arma su piel
 * (skin), lo que no siempre ha pasado todavia cuando corre initialize().
 */
public final class EstiloUtil {

    private EstiloUtil() {
    }

    /** Ejecuta "accion" apenas el control tenga su piel lista (ya, si la tiene; si no, en cuanto la arme). */
    public static void alArmarPiel(Control control, Runnable accion) {
        if (control.getSkin() != null) {
            accion.run();
        } else {
            control.skinProperty().addListener(new javafx.beans.value.ChangeListener<javafx.scene.control.Skin<?>>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.control.Skin<?>> obs,
                                     javafx.scene.control.Skin<?> antes, javafx.scene.control.Skin<?> ahora) {
                    accion.run();
                    control.skinProperty().removeListener(this);
                }
            });
        }
    }
}
