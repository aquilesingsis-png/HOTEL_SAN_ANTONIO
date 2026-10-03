package untrm.hotel_san_antonio.util;

import javafx.scene.control.TextInputControl;

/**
 * Deja un campo de texto "de solo lectura": no se puede escribir, pero si seleccionar y copiar, y se ve
 * con fondo gris claro. Se usa para los nombres y apellidos que vienen de RENIEC o de la base local.
 */
public final class SoloLectura {

    private static final String FONDO = " -fx-background-color: #F3EEE7;";

    private SoloLectura() {
    }

    public static void aplicar(TextInputControl campo, boolean soloLectura) {
        campo.setEditable(!soloLectura);
        String estilo = campo.getStyle().replace(FONDO, "");
        campo.setStyle(soloLectura ? estilo + FONDO : estilo);
    }
}
