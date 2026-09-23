package untrm.hotel_san_antonio;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.util.Navegacion;

/**
 * Clase principal - arranca JavaFX y carga el Login.
 *
 * El tamaño inicial de la ventana se calcula como un % de la resolucion
 * real del monitor (no un valor fijo en pixeles), para que se vea bien
 * tanto en una laptop de 1366x768 como en un monitor de escritorio grande.
 * De ahi en adelante, Navegacion.irA() no vuelve a tocar el tamaño, asi
 * que el usuario puede maximizar/redimensionar y eso se respeta siempre.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Navegacion.setStagePrincipal(stage);

        Parent raiz = FXMLLoader.load(getClass().getResource("/untrm/hotel_san_antonio/fxml/login/login.fxml"));

        var pantalla = Screen.getPrimary().getVisualBounds();
        double ancho = Math.max(1024, pantalla.getWidth() * 0.75);
        double alto = Math.max(700, pantalla.getHeight() * 0.80);

        Scene escena = new Scene(raiz, ancho, alto);
        stage.setScene(escena);
        stage.setMinWidth(1024);
        stage.setMinHeight(650);
        stage.setTitle("Hotel San Antonio - SIGEH");
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
