package untrm.hotel_san_antonio.controlador;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.SesionActual;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Controlador del marco principal (menu lateral + encabezado + contenido). Sirve para
 * cualquier rol: cada rol tiene su propio FXML con sus botones de menu, pero todos
 * comparten esta logica (el boton guarda en userData la ruta de la pantalla que abre).
 */
public class PrincipalController {

    private static final String RUTA_LOGIN = "/untrm/hotel_san_antonio/fxml/login.fxml";
    private static final String RUTA_MARCO_RECEPCIONISTA = "/untrm/hotel_san_antonio/fxml/principal/principal_recepcionista.fxml";
    private static final String RUTA_MARCO_ADMIN = "/untrm/hotel_san_antonio/fxml/principal/principal_administrador.fxml";
    private static final String RUTA_CARRITO = "/untrm/hotel_san_antonio/fxml/tiendita/carrito_tienda.fxml";

    @FXML private StackPane contenido;
    @FXML private VBox barraLateral;
    @FXML private ToggleButton btnInicio;
    @FXML private Label lblTitulo, lblFecha, lblUsuario, lblRol;

    /** FXML del marco que corresponde al rol del usuario que inicio sesion (lo usa el Login). */
    public static String rutaMarco(String rol) {
        return "ADMINISTRADOR".equals(rol) ? RUTA_MARCO_ADMIN : RUTA_MARCO_RECEPCIONISTA;
    }

    @FXML
    public void initialize() {
        Navegacion.setContenedorCentro(contenido);

        lblFecha.setText(capitalizar(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE")))));

        Usuario usuario = SesionActual.getUsuario();
        if (usuario != null) {
            lblUsuario.setText(usuario.getNombreCompleto());
            lblRol.setText("ADMINISTRADOR".equals(usuario.getRol()) ? "Administrador" : "Recepcionista");
        }

        abrir(btnInicio);
    }

    @FXML
    private void onMenu(ActionEvent evento) {
        ToggleButton boton = (ToggleButton) evento.getSource();
        if (!boton.isSelected()) { // no permitir dejar el menu sin opcion elegida
            boton.setSelected(true);
            return;
        }
        abrir(boton);
    }

    /** Despliega u oculta el submenu que sigue al boton (no abre ninguna pantalla por si solo). */
    @FXML
    private void onGrupo(ActionEvent evento) {
        Button boton = (Button) evento.getSource();
        int posicion = barraLateral.getChildren().indexOf(boton);
        Node submenu = barraLateral.getChildren().get(posicion + 1);
        boolean mostrar = !submenu.isVisible();
        if (mostrar) {
            plegarGrupos(); // solo un grupo abierto a la vez
        }
        establecerDesplegado(boton, submenu, mostrar);
        if (mostrar) {
            desplazarHasta(boton);
        }
    }

    /** Si el menu tiene barra de desplazamiento, sube hasta dejar el grupo recien abierto a la vista. */
    private void desplazarHasta(Button boton) {
        Node padre = barraLateral;
        while (padre != null && !(padre instanceof ScrollPane)) {
            padre = padre.getParent();
        }
        if (padre == null) {
            return;
        }
        ScrollPane desplazamiento = (ScrollPane) padre;
        Platform.runLater(() -> {
            barraLateral.applyCss();
            barraLateral.layout();
            double exceso = barraLateral.getHeight() - desplazamiento.getViewportBounds().getHeight();
            if (exceso > 0) {
                desplazamiento.setVvalue(Math.min(1, boton.getBoundsInParent().getMinY() / exceso));
            }
        });
    }

    private void plegarGrupos() {
        var hijos = barraLateral.getChildren();
        for (int i = 1; i < hijos.size(); i++) {
            if (hijos.get(i).getStyleClass().contains("submenu")) {
                establecerDesplegado((Button) hijos.get(i - 1), hijos.get(i), false);
            }
        }
    }

    private void establecerDesplegado(Button boton, Node submenu, boolean desplegado) {
        submenu.setVisible(desplegado);
        submenu.setManaged(desplegado);
        String texto = boton.getText();
        boton.setText(texto.substring(0, texto.length() - 1) + (desplegado ? "▾" : "▸"));
    }

    /** Resalta el boton de grupo (ej. Reservas) cuando la pantalla abierta es una de sus opciones. */
    private void marcarGrupoActivo(ToggleButton seleccionado) {
        var hijos = barraLateral.getChildren();
        for (int i = 1; i < hijos.size(); i++) {
            Node candidato = hijos.get(i);
            if (candidato.getStyleClass().contains("submenu")) {
                boolean activo = ((VBox) candidato).getChildren().contains(seleccionado);
                var clases = hijos.get(i - 1).getStyleClass();
                clases.remove("menu-grupo-activo");
                if (activo) {
                    clases.add("menu-grupo-activo");
                }
            }
        }
    }

    private void abrir(ToggleButton boton) {
        // En un submenu el titulo lleva el nombre del grupo: "Reservas · Nueva reserva"
        Object grupo = boton.getParent().getUserData();
        lblTitulo.setText(grupo == null ? boton.getText() : grupo + " · " + boton.getText());
        marcarGrupoActivo(boton);
        try {
            Navegacion.mostrar(boton.getUserData().toString());
        } catch (IOException e) {
            Alertas.mostrarError("Error", "No se pudo abrir " + boton.getText() + ".\n\n" + e.getMessage());
        }
    }

    /** El carrito de la tienda se abre encima de cualquier pantalla, sin cambiar de modulo. */
    @FXML
    private void onCarrito() {
        if (!Navegacion.existe(RUTA_CARRITO)) {
            Alertas.mostrarInfo("Carrito", "El carrito de la tienda aún no está disponible.");
            return;
        }
        try {
            Navegacion.abrirModal(RUTA_CARRITO, "Carrito de la tienda");
        } catch (IOException e) {
            Alertas.mostrarError("Error", "No se pudo abrir el carrito.\n\n" + e.getMessage());
        }
    }

    @FXML
    private void onCerrarSesion() {
        if (!Alertas.confirmar("Cerrar sesión", "¿Deseas cerrar la sesión?")) {
            return;
        }
        SesionActual.cerrar();
        try {
            Navegacion.irA(RUTA_LOGIN);
        } catch (IOException e) {
            Alertas.mostrarError("Error", "No se pudo volver al inicio de sesión.\n\n" + e.getMessage());
        }
    }

    private String capitalizar(String texto) {
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
