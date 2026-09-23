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
 *
 * Sin archivo .css: el FXML solo trae el estilo fijo de cada boton (color de fondo, texto,
 * bordes). Lo que cambia segun lo que hace el usuario -pasar el mouse, dejar seleccionado un
 * boton, marcar un grupo del menu como activo- se resuelve aqui mismo con setStyle(), porque
 * eso no se puede fijar de antemano en el FXML.
 */
public class PrincipalController {

    private static final String RUTA_LOGIN = "/untrm/hotel_san_antonio/fxml/login/login.fxml";
    private static final String RUTA_MARCO_RECEPCIONISTA = "/untrm/hotel_san_antonio/fxml/principal/principal_recepcionista.fxml";
    private static final String RUTA_MARCO_ADMIN = "/untrm/hotel_san_antonio/fxml/principal/principal_administrador.fxml";
    private static final String RUTA_CARRITO = "/untrm/hotel_san_antonio/fxml/carrito/carrito_tienda.fxml";

    // Botones de opcion (Inicio, Habitaciones, y los de dentro de un submenu)
    private static final String ITEM = "-fx-background-color: transparent; -fx-text-fill: #F3ECDD; -fx-font-size: 14px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 11px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";
    private static final String ITEM_HOVER = "-fx-background-color: #5A3516; -fx-text-fill: #F3ECDD; -fx-font-size: 14px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 11px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";
    private static final String ITEM_SELECCIONADO = "-fx-background-color: #B8862D; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 11px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";

    // Botones dentro de un submenu desplegado: igual, con letra un poco mas chica
    private static final String SUBITEM = "-fx-background-color: transparent; -fx-text-fill: #F3ECDD; -fx-font-size: 13px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 8px 14px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";
    private static final String SUBITEM_HOVER = "-fx-background-color: #5A3516; -fx-text-fill: #F3ECDD; -fx-font-size: 13px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 8px 14px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";
    private static final String SUBITEM_SELECCIONADO = "-fx-background-color: #B8862D; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 8px 14px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";

    // Botones que solo despliegan un submenu (ej. "Reservas ▸"): mismo look que ITEM, mas un color
    // distinto cuando una de sus opciones esta abierta ("Reservas" activo aunque este plegado)
    private static final String GRUPO_ACTIVO = "-fx-background-color: transparent; -fx-text-fill: #E6C77A; -fx-font-weight: bold; -fx-font-size: 14px; "
            + "-fx-alignment: CENTER_LEFT; -fx-padding: 11px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-max-width: infinity;";

    @FXML private StackPane contenido;
    @FXML private ScrollPane scrollMenu;
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

        aplicarEstiloMenu();
        aplicarTransparenciaViewport();
        abrir(btnInicio);
    }

    /**
     * El interior del ScrollPane (su "viewport") pinta blanco por defecto y eso no se puede fijar
     * desde un style="" (solo un .css con "> .viewport" lo alcanza). Se corrige a mano en cuanto
     * el ScrollPane arma su piel (skin) -recien ahi existe el nodo "viewport" para buscarlo-, para
     * que se vea del mismo marron que el menu.
     */
    private void aplicarTransparenciaViewport() {
        if (scrollMenu.getSkin() != null) {
            teñirViewport();
        } else {
            scrollMenu.skinProperty().addListener(new javafx.beans.value.ChangeListener<javafx.scene.control.Skin<?>>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.control.Skin<?>> obs,
                                     javafx.scene.control.Skin<?> antes, javafx.scene.control.Skin<?> ahora) {
                    teñirViewport();
                    scrollMenu.skinProperty().removeListener(this);
                }
            });
        }
    }

    private void teñirViewport() {
        Node viewport = scrollMenu.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: transparent;");
        }
    }

    /** Da a cada boton del menu su look normal/mouse-encima/seleccionado (no depende de ningun .css). */
    private void aplicarEstiloMenu() {
        for (Node hijo : barraLateral.getChildren()) {
            if (hijo instanceof ToggleButton) {
                aplicarItem((ToggleButton) hijo, ITEM, ITEM_HOVER, ITEM_SELECCIONADO);
            } else if (hijo instanceof Button) {
                aplicarGrupo((Button) hijo);
            } else if (hijo instanceof VBox) { // submenu
                for (Node sub : ((VBox) hijo).getChildren()) {
                    if (sub instanceof ToggleButton) {
                        aplicarItem((ToggleButton) sub, SUBITEM, SUBITEM_HOVER, SUBITEM_SELECCIONADO);
                    }
                }
            }
        }
    }

    private void aplicarItem(ToggleButton boton, String normal, String hover, String seleccionado) {
        Runnable actualizar = () -> boton.setStyle(boton.isSelected() ? seleccionado : normal);
        actualizar.run();
        boton.selectedProperty().addListener((obs, antes, ahora) -> actualizar.run());
        boton.setOnMouseEntered(e -> { if (!boton.isSelected()) boton.setStyle(hover); });
        boton.setOnMouseExited(e -> actualizar.run());
    }

    private void aplicarGrupo(Button boton) {
        boton.setStyle(ITEM);
        boton.setOnMouseEntered(e -> boton.setStyle(ITEM_HOVER));
        boton.setOnMouseExited(e -> boton.setStyle(esGrupoActivo(boton) ? GRUPO_ACTIVO : ITEM));
    }

    private boolean esGrupoActivo(Button boton) {
        int posicion = barraLateral.getChildren().indexOf(boton);
        Node submenu = barraLateral.getChildren().get(posicion + 1);
        return ((VBox) submenu).getChildren().stream()
                .anyMatch(n -> n instanceof ToggleButton && ((ToggleButton) n).isSelected());
    }

    @FXML
    private void onMenu(ActionEvent evento) {
        ToggleButton boton = (ToggleButton) evento.getSource();
        if (!boton.isSelected()) { // no permitir dejar el menu sin opcion elegida
            boton.setSelected(true);
            return;
        }
        // Si se eligio una opcion fuera de un submenu (ej. "Comprobantes"), se pliega el
        // submenu que hubiera quedado abierto (ej. "Reservas"); si se eligio una opcion DE
        // un submenu (ej. "Nueva reserva"), ese submenu se queda desplegado.
        Object padre = boton.getParent();
        plegarGrupos(padre instanceof VBox ? (VBox) padre : null);
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
            plegarGrupos(null); // solo un grupo abierto a la vez
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

    /** Pliega todos los submenus abiertos, salvo "excepto" (para no cerrar el submenu que se esta usando). */
    private void plegarGrupos(VBox excepto) {
        var hijos = barraLateral.getChildren();
        for (int i = 1; i < hijos.size(); i++) {
            if (hijos.get(i) instanceof VBox && hijos.get(i) != excepto) {
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
            if (candidato instanceof VBox) {
                boolean activo = ((VBox) candidato).getChildren().contains(seleccionado);
                ((Button) hijos.get(i - 1)).setStyle(activo ? GRUPO_ACTIVO : ITEM);
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
