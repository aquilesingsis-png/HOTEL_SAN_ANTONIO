package untrm.hotel_san_antonio.util;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Utilidad central de navegacion: cambiar la pantalla principal, o abrir
 * una ventana modal (para los formularios/dialogos tipo "Cuenta de
 * Habitacion" o el carrito de la tiendita).
 */
public class Navegacion {

    private static Stage stagePrincipal;

    public static void setStagePrincipal(Stage stage) {
        stagePrincipal = stage;
    }

    /**
     * Reemplaza el contenido de la ventana principal (ej: Login -> Dashboard).
     * Si ya existe una Scene, solo se cambia la raiz (no se crea una Scene
     * nueva) para que el tamaño/posicion que el usuario le dio a la ventana
     * NO se resetee cada vez que navega — asi la app se adapta a cualquier
     * resolucion de escritorio sin "saltar" de tamaño entre pantallas.
     */
    public static void irA(String rutaFxml) throws IOException {
        Parent raiz = FXMLLoader.load(Navegacion.class.getResource(rutaFxml));
        if (stagePrincipal.getScene() == null) {
            stagePrincipal.setScene(new Scene(raiz));
        } else {
            stagePrincipal.getScene().setRoot(raiz);
        }
    }

    /** Abre una ventana modal encima de la actual y espera a que se cierre. */
    public static void abrirModal(String rutaFxml, String titulo) throws IOException {
        Parent raiz = FXMLLoader.load(Navegacion.class.getResource(rutaFxml));
        Stage modal = new Stage();
        modal.setTitle(titulo);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(stagePrincipal);
        modal.setScene(new Scene(raiz));
        modal.showAndWait();
    }
}
