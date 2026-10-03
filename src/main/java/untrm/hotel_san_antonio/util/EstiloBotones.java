package untrm.hotel_san_antonio.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;

/** Efectos de botones que un style="" fijo no da: marrón al pulsar o elegir, y el hover del botón principal. */
public final class EstiloBotones {
    private static final String PULSADO = "-fx-background-color: #704313; -fx-text-fill: white;";
    private static final String ELEGIDO = "-fx-background-color: #704313; -fx-text-fill: white;";

    private static final String PRIMARIO = "-fx-background-color: #B8862D; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-background-radius: 4px; -fx-padding: 8px 20px; -fx-cursor: hand;";
    private static final String PRIMARIO_HOVER = "-fx-background-color: #D4A84C; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-background-radius: 4px; -fx-padding: 8px 20px; -fx-cursor: hand;";

    private EstiloBotones() { }

    /** Resalta el boton dorado principal al pasar el mouse (un style="" fijo no tiene :hover). */
    public static void hoverPrimario(Button boton) {
        boton.setOnMouseEntered(e -> boton.setStyle(PRIMARIO_HOVER));
        boton.setOnMouseExited(e -> boton.setStyle(PRIMARIO));
    }

    public static void instalar(Scene escena) {
        instalarEnRaiz(escena, escena.getRoot());
        escena.rootProperty().addListener((obs, antes, ahora) -> instalarEnRaiz(escena, ahora));
    }

    private static void instalarEnRaiz(Scene escena, Node raiz) {
        // El filtro ve incluso los clics que un control consume internamente.
        raiz.addEventFilter(MouseEvent.MOUSE_PRESSED, evento -> {
            ButtonBase boton = buscarBoton(evento.getTarget());
            if (boton == null || boton.isDisabled()) return;
            Pulsacion pulsacion = new Pulsacion(boton);
            escena.getProperties().put(EstiloBotones.class, pulsacion);
            Platform.runLater(() -> {
                if (escena.getProperties().get(EstiloBotones.class) != pulsacion) return;
                pulsacion.estiloAnterior = boton.getStyle();
                pulsacion.estiloPulsado = pulsacion.estiloAnterior + ";" + PULSADO;
                boton.setStyle(pulsacion.estiloPulsado);
            });
        });
        raiz.addEventFilter(MouseEvent.MOUSE_RELEASED, evento -> {
            Object valor = escena.getProperties().remove(EstiloBotones.class);
            if (!(valor instanceof Pulsacion pulsacion)) return;
            Platform.runLater(() -> {
                ButtonBase boton = pulsacion.boton;
                // Si el controlador cambió el estilo según la acción, se conserva.
                if (pulsacion.estiloPulsado == null
                        || !boton.getStyle().equals(pulsacion.estiloPulsado)) return;
                String base = quitarMarcaPropia(pulsacion.estiloAnterior);
                if (boton instanceof ToggleButton toggle && toggle.isSelected()) {
                    boton.setStyle(base + ";" + ELEGIDO);
                } else {
                    boton.setStyle(base);
                }
            });
        });
    }

    private static ButtonBase buscarBoton(Object destino) {
        if (!(destino instanceof Node nodo)) return null;
        while (nodo != null) {
            if (nodo instanceof Button || nodo instanceof ToggleButton) {
                return (ButtonBase) nodo;
            }
            nodo = nodo.getParent();
        }
        return null;
    }

    private static String quitarMarcaPropia(String estilo) {
        String marca = ";" + ELEGIDO;
        return estilo.endsWith(marca) ? estilo.substring(0, estilo.length() - marca.length()) : estilo;
    }

    private static final class Pulsacion {
        private final ButtonBase boton;
        private String estiloAnterior;
        private String estiloPulsado;

        private Pulsacion(ButtonBase boton) { this.boton = boton; }
    }
}
