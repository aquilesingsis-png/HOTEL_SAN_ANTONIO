package untrm.hotel_san_antonio.util;

import java.io.IOException;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import untrm.hotel_san_antonio.controlador.ModulosNuevosController;

/**
 * Utilidad central de navegacion: cambiar la pantalla principal, o abrir
 * una ventana modal (para los formularios/dialogos tipo "Cuenta de
 * Habitacion" o el carrito de la tiendita).
 */
public class Navegacion {

    private static final String EN_CONSTRUCCION = "/untrm/hotel_san_antonio/fxml/principal/en_construccion.fxml";
    private static final String NUEVOS = "/untrm/hotel_san_antonio/fxml/nuevos/";
    // Estas vistas ya tienen controladores específicos; las demás comparten
    // el controlador de administración que activa sus botones y datos.
    private static final Set<String> MODULOS_OPERATIVOS_NUEVOS = Set.of(
            NUEVOS + "caja/gastos.fxml",
            NUEVOS + "caja/libro_caja.fxml",
            NUEVOS + "reportes/reportes.fxml",
            NUEVOS + "almacen/almacen.fxml",
            NUEVOS + "almacen/producto_form.fxml",
            NUEVOS + "almacen/stock_form.fxml",
            NUEVOS + "almacen/categorias_form.fxml",
            NUEVOS + "caja/cierre_arqueo.fxml",
            NUEVOS + "usuarios/crear_usuario.fxml",
            NUEVOS + "usuarios/asignar_rol.fxml",
            NUEVOS + "usuarios/recuperacion_admin.fxml");

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
        Permisos.requerirRuta(rutaFxml);
        Parent raiz = FXMLLoader.load(Navegacion.class.getResource(rutaFxml));
        if (stagePrincipal.getScene() == null) {
            Scene nueva = new Scene(raiz);
            aplicarCss(nueva);
            stagePrincipal.setScene(nueva);
        } else {
            stagePrincipal.getScene().setRoot(raiz);
        }
    }

    /**
     * Cambia solo el contenido del marco principal (menu lateral y encabezado se quedan).
     * Si falta el FXML se muestra la pantalla "en construcción".
     */
    public static void mostrar(String rutaFxml) throws IOException {
        Permisos.requerirRuta(rutaFxml);
        boolean moduloGenerico = rutaFxml.startsWith(NUEVOS)
                && !MODULOS_OPERATIVOS_NUEVOS.contains(rutaFxml);
        java.net.URL url = existe(rutaFxml) ? Navegacion.class.getResource(rutaFxml)
                : Navegacion.class.getResource(EN_CONSTRUCCION);
        FXMLLoader cargador = new FXMLLoader(url);
        Parent vista = cargador.load();
        if (moduloGenerico && existe(rutaFxml)) {
            String modulo = rutaFxml.substring(NUEVOS.length()).replace(".fxml", "");
            new ModulosNuevosController(modulo, cargador.getNamespace()).iniciar();
        }
        centro.getChildren().setAll(vista);
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
        Permisos.requerirRuta(rutaFxml);
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
        Scene escena = new Scene(raiz, ancho, alto);
        aplicarCss(escena);
        modal.setScene(escena);
        modal.showAndWait();
    }

    /** Abre una ventana modal encima de la actual y espera a que se cierre. */
    public static void abrirModal(String rutaFxml, String titulo) throws IOException {
        Permisos.requerirRuta(rutaFxml);
        Parent raiz = FXMLLoader.load(Navegacion.class.getResource(rutaFxml));
        Stage modal = new Stage();
        modal.setTitle(titulo);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(stagePrincipal);
        Scene escena = new Scene(raiz);
        aplicarCss(escena);
        modal.setScene(escena);
        modal.showAndWait();
    }

    private static void aplicarCss(Scene escena) {
        EstiloGlobal.instalar(escena);
    }
}
