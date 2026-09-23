package untrm.hotel_san_antonio.util;

import javafx.scene.control.Button;

/**
 * El resaltado al pasar el mouse sobre el boton principal (dorado) no se puede lograr con un
 * estilo inline fijo (no existe ":hover" fuera de un archivo .css), asi que se hace aqui,
 * cambiando el estilo del boton al entrar/salir el mouse.
 */
public final class EstiloBoton {

    private static final String PRIMARIO = "-fx-background-color: #B8862D; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-background-radius: 4px; -fx-padding: 8px 20px; -fx-cursor: hand;";
    private static final String PRIMARIO_HOVER = "-fx-background-color: #D4A84C; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-background-radius: 4px; -fx-padding: 8px 20px; -fx-cursor: hand;";

    private EstiloBoton() {
    }

    public static void aplicarHoverPrimario(Button boton) {
        boton.setOnMouseEntered(e -> boton.setStyle(PRIMARIO_HOVER));
        boton.setOnMouseExited(e -> boton.setStyle(PRIMARIO));
    }
}
