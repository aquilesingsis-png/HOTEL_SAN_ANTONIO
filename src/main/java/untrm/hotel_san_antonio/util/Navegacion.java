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

    private static final String EN_CONSTRUCCION = "/untrm/hotel_san_antonio/fxml/principal/en_construccion.fxml";

    private static Stage stagePrincipal;
    private static javafx.scene.layout.Pane centro;

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

    /**
     * Cambia solo el contenido del marco principal (menu lateral y encabezado se quedan).
     * Si el FXML todavia no existe (modulo aun sin construir) muestra la pantalla "en construccion".
     */
    public static void mostrar(String rutaFxml) throws IOException {
        java.net.URL url = existe(rutaFxml) ? Navegacion.class.getResource(rutaFxml)
                : Navegacion.class.getResource(EN_CONSTRUCCION);
        centro.getChildren().setAll((Parent) FXMLLoader.load(url));
    }

    /** true si el archivo FXML existe en los recursos (los modulos pendientes todavia no lo tienen). */
    public static boolean existe(String rutaFxml) {
        return Navegacion.class.getResource(rutaFxml) != null;
    }

    /** El marco principal registra aqui el panel donde se muestran las pantallas. */
    public static void setContenedorCentro(javafx.scene.layout.Pane contenedor) {
        centro = contenedor;
    }

    /**
     * Abre una ventana modal y le entrega su controlador a "configurador" para pasarle
     * datos (por ejemplo, la habitacion clicada). La ventana toma un tamaño proporcional
     * a la ventana principal, asi se adapta a cualquier resolucion de escritorio.
     */
    public static <T> void abrirModal(String rutaFxml, String titulo, java.util.function.Consumer<T> configurador)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(Navegacion.class.getResource(rutaFxml));
        Parent raiz = loader.load();
        if (configurador != null) {
            configurador.accept(loader.<T>getController());
        }

        double ancho = Math.max(900, stagePrincipal.getWidth() * 0.72);
        double alto = Math.max(640, stagePrincipal.getHeight() * 0.88);

        Stage modal = new Stage();
        modal.setTitle(titulo);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(stagePrincipal);
        modal.setMinWidth(860);
        modal.setMinHeight(600);
        modal.setScene(new Scene(raiz, ancho, alto));
        modal.showAndWait();
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
