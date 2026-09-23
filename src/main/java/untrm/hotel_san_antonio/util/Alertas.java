package untrm.hotel_san_antonio.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

/**
 * Dialogos de confirmacion/error/informacion consistentes en toda la app. No necesitan archivo
 * .fxml (son parte del propio JavaFX), pero sin ayuda se ven con el look por defecto de Windows
 * (el icono azul de pregunta, botones grises "Aceptar"/"Cancelar"): aqui se les da el mismo estilo
 * marron/dorado del resto de la app, para que no desentonen.
 */
public class Alertas {

    private static final String FONDO = "-fx-background-color: #FBF8F3; -fx-font-family: 'Segoe UI';";

    private static final String BOTON_PRIMARIO = "-fx-background-color: #A87425; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8px 22px; -fx-cursor: hand;";
    private static final String BOTON_PRIMARIO_HOVER = "-fx-background-color: #8B5E1D; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8px 22px; -fx-cursor: hand;";
    private static final String BOTON_SECUNDARIO = "-fx-background-color: #F2EFE9; -fx-text-fill: #3B210F; "
            + "-fx-background-radius: 6px; -fx-padding: 8px 22px; -fx-cursor: hand;";
    private static final String BOTON_SECUNDARIO_HOVER = "-fx-background-color: #E7E1D6; -fx-text-fill: #3B210F; "
            + "-fx-background-radius: 6px; -fx-padding: 8px 22px; -fx-cursor: hand;";

    public static void mostrarError(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        estilizar(alerta.getDialogPane(), "#B64832");
        alerta.showAndWait();
    }

    public static void mostrarAdvertencia(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        estilizar(alerta.getDialogPane(), "#D9A53B");
        alerta.showAndWait();
    }

    public static void mostrarInfo(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        estilizar(alerta.getDialogPane(), "#1C9748");
        alerta.showAndWait();
    }

    public static boolean confirmar(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        estilizar(alerta.getDialogPane(), "#B8862D");
        Optional<ButtonType> resultado = alerta.showAndWait();
        return resultado.isPresent() && resultado.get() == ButtonType.OK;
    }

    /** Pide una linea de texto corta (ej. el motivo de un mantenimiento). Null si se cancelo o quedo vacio. */
    public static String pedirTexto(String titulo, String mensaje, String promptText) {
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(null);
        dialogo.setContentText(mensaje);
        dialogo.getEditor().setPromptText(promptText);
        estilizar(dialogo.getDialogPane(), "#B8862D");
        return dialogo.showAndWait().map(String::trim).filter(texto -> !texto.isEmpty()).orElse(null);
    }

    /** Le da a cualquier Alert o TextInputDialog el mismo look que el resto de la app. */
    private static void estilizar(DialogPane panel, String colorAcento) {
        panel.setStyle(FONDO + " -fx-border-color: " + colorAcento + "; -fx-border-width: 0 0 0 5px;");
        panel.lookupAll(".content.label").forEach(nodo -> nodo.setStyle("-fx-font-size: 13px; -fx-text-fill: #2C2118;"));

        // El icono de pregunta/exclamacion/error de Windows no combina con el resto de la app.
        panel.setGraphic(null);

        for (ButtonType tipoBoton : panel.getButtonTypes()) {
            if (panel.lookupButton(tipoBoton) instanceof Button boton) {
                boolean esPrimario = tipoBoton == ButtonType.OK || tipoBoton == ButtonType.YES
                        || tipoBoton.getButtonData().isDefaultButton();
                String normal = esPrimario ? BOTON_PRIMARIO : BOTON_SECUNDARIO;
                String hover = esPrimario ? BOTON_PRIMARIO_HOVER : BOTON_SECUNDARIO_HOVER;
                boton.setStyle(normal);
                boton.setOnMouseEntered(e -> boton.setStyle(hover));
                boton.setOnMouseExited(e -> boton.setStyle(normal));
            }
        }
    }
}
