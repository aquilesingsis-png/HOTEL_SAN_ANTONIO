package untrm.hotel_san_antonio.util;

import java.util.List;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;

/**
 * Lo que el diseño de las pantallas necesita y un style="" fijo del FXML no puede dar, porque
 * depende de un estado (mouse encima, campo con foco, fila seleccionada, pestaña elegida).
 * El aspecto normal de cada control esta en su FXML; aqui solo se agrega lo que cambia.
 *
 * Se instala una vez por ventana (App y Navegacion) y vigila los controles que van apareciendo.
 */
public final class EstiloGlobal {

    private static final String MARCA = "estiloGlobal";
    private static final String ESTILO_PREVIO = "estiloGlobalPrevio";

    /** Fuente y fondo general de cada ventana. */
    private static final String RAIZ = "-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-background-color: #F7F4EF; ";

    /** Debe coincidir con el valor base que los FXML dan a un Button sin otro color. */
    private static final String BOTON_BASE =
            "-fx-background-color: #A87425; -fx-text-fill: white; -fx-background-radius: 5px; -fx-cursor: hand;";
    private static final String HOVER_BOTON = "-fx-background-color: #B8862D;";
    private static final String HOVER_PRIMARIO = "-fx-background-color: #A56D17; -fx-border-color: #A56D17;";
    private static final String HOVER_SECUNDARIO = "-fx-background-color: #F5E6C8;";
    private static final String HOVER_PELIGRO = "-fx-background-color: #FFF0ED;";

    private static final String FOCO = "-fx-border-color: #B8862D;";

    private static final String FILA_SELECCIONADA = "-fx-background-color: #704313; -fx-text-background-color: white;";

    private static final String TAB_NORMAL = "-fx-background-color: white; -fx-padding: 10 20; "
            + "-fx-border-color: transparent; -fx-border-width: 0 0 3 0; "
            + "-fx-text-base-color: #5A3516; -fx-font-weight: bold;";
    private static final String TAB_ELEGIDA = "-fx-background-color: #FBF7EF; -fx-padding: 10 20; "
            + "-fx-border-color: #B8862D; -fx-border-width: 0 0 3 0; "
            + "-fx-text-base-color: #5A3516; -fx-font-weight: bold;";

    private EstiloGlobal() {
    }

    public static void instalar(Scene escena) {
        aplicarRaiz(escena.getRoot());
        vigilar(escena.getRoot());
        escena.rootProperty().addListener((obs, antes, ahora) -> {
            aplicarRaiz(ahora);
            vigilar(ahora);
        });
        EstiloBotones.instalar(escena);
    }

    private static void aplicarRaiz(Node raiz) {
        if (raiz.getProperties().putIfAbsent(MARCA + "Raiz", Boolean.TRUE) == null) {
            raiz.setStyle(RAIZ + raiz.getStyle());
        }
    }

    // ---------------------------------------------------------------- recorrido

    private static void vigilar(Node nodo) {
        estilizar(nodo);
        if (!(nodo instanceof Parent padre) || !conPiezasPropias(nodo)) {
            return;
        }
        for (Node hijo : padre.getChildrenUnmodifiable()) {
            vigilar(hijo);
        }
        if (padre.getProperties().putIfAbsent(MARCA + "Hijos", Boolean.TRUE) == null) {
            padre.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) cambio -> {
                while (cambio.next()) {
                    if (cambio.wasAdded()) {
                        for (Node nuevo : cambio.getAddedSubList()) {
                            vigilar(nuevo);
                        }
                    }
                }
            });
        }
    }

    /** Un control (campo, boton, tabla...) arma sus propias piezas internas: no se entra a ellas. */
    private static boolean conPiezasPropias(Node nodo) {
        if (!(nodo instanceof Control)) {
            return true;
        }
        return nodo instanceof TabPane || nodo instanceof ScrollPane || nodo instanceof SplitPane
                || nodo instanceof TitledPane || nodo instanceof Accordion;
    }

    private static void estilizar(Node nodo) {
        if (nodo.getProperties().putIfAbsent(MARCA, Boolean.TRUE) != null) {
            return;
        }
        if (nodo instanceof TextInputControl || nodo instanceof ComboBoxBase<?>
                || nodo instanceof ChoiceBox<?> || nodo instanceof Spinner<?>) {
            bordeAlEnfocar(nodo);
        } else if (nodo instanceof Button boton) {
            efectoBoton(boton);
        } else if (nodo instanceof TableView<?> tabla) {
            estilizarTabla(tabla);
        } else if (nodo instanceof TabPane pestanas && "tabs-nuevos".equals(pestanas.getUserData())) {
            estilizarPestanas(pestanas);
        }
    }

    // ------------------------------------------------------------- campos y botones

    private static void bordeAlEnfocar(Node campo) {
        campo.focusWithinProperty().addListener((obs, antes, ahora) -> {
            String estilo = campo.getStyle();
            if (ahora) {
                if (!estilo.contains(FOCO)) {
                    campo.setStyle(estilo + " " + FOCO);
                }
            } else {
                campo.setStyle(estilo.replace(" " + FOCO, ""));
            }
        });
    }

    private static void efectoBoton(Button boton) {
        String tipo = boton.getUserData() instanceof String texto ? texto : "";
        String hover = switch (tipo) {
            case "primary" -> HOVER_PRIMARIO;
            case "secondary" -> HOVER_SECUNDARIO;
            case "danger" -> HOVER_PELIGRO;
            default -> esBotonBase(boton) ? HOVER_BOTON : null;
        };
        if (hover == null) {
            return; // el boton tiene su propio color y su propio efecto (lo pone su controlador)
        }
        boton.setOpacity(boton.isDisabled() ? 0.55 : 1);
        boton.disabledProperty().addListener((obs, antes, ahora) -> boton.setOpacity(ahora ? 0.55 : 1));
        boton.addEventHandler(MouseEvent.MOUSE_ENTERED, evento -> {
            if (boton.isDisabled()) {
                return;
            }
            String actual = boton.getStyle();
            if (!actual.endsWith(hover)) {
                boton.getProperties().put(ESTILO_PREVIO, actual);
                boton.setStyle(actual + " " + hover);
            }
        });
        boton.addEventHandler(MouseEvent.MOUSE_EXITED, evento -> {
            Object previo = boton.getProperties().remove(ESTILO_PREVIO);
            if (previo instanceof String anterior && boton.getStyle().equals(anterior + " " + hover)) {
                boton.setStyle(anterior);
            }
        });
    }

    /** Un boton "normal": su unico color de fondo es el base. Si trae otro, ya tiene su propio diseño. */
    private static boolean esBotonBase(Button boton) {
        String estilo = boton.getStyle();
        return estilo.startsWith(BOTON_BASE) && estilo.indexOf("-fx-background-color") == estilo.lastIndexOf("-fx-background-color");
    }

    // ------------------------------------------------------------------- tablas

    private static <T> void estilizarTabla(TableView<T> tabla) {
        if (tabla.getRowFactory() != null) {
            return; // su controlador ya la estiliza (por ejemplo, las mini tablas del Dashboard)
        }
        boolean nuevos = "tabla-nuevos".equals(tabla.getUserData());
        tabla.setRowFactory(tv -> filaEstilizada(nuevos));
        EstiloUtil.alArmarPiel(tabla, () -> Platform.runLater(() -> encabezados(tabla, nuevos)));
    }

    private static <T> TableRow<T> filaEstilizada(boolean nuevos) {
        TableRow<T> fila = new TableRow<>();
        Runnable pintar = () -> fila.setStyle(estiloFila(fila, nuevos));
        fila.selectedProperty().addListener((obs, antes, ahora) -> pintar.run());
        fila.indexProperty().addListener((obs, antes, ahora) -> pintar.run());
        fila.emptyProperty().addListener((obs, antes, ahora) -> pintar.run());
        return fila;
    }

    private static String estiloFila(TableRow<?> fila, boolean nuevos) {
        if (fila.isSelected() && !fila.isEmpty()) {
            return FILA_SELECCIONADA;
        }
        if (!nuevos) {
            return "";
        }
        String fondo = fila.getIndex() % 2 == 0 ? "white" : "#FBF9F5";
        return "-fx-background-color: " + fondo + "; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    }

    private static void encabezados(TableView<?> tabla, boolean nuevos) {
        String fondo = nuevos ? "-fx-background-color: #F3EEE7; -fx-pref-height: 42;" : "-fx-background-color: #EEE7DB;";
        for (Node contenedor : tabla.lookupAll(".column-header-background")) {
            contenedor.setStyle(fondo);
        }
        if (nuevos) {
            for (String pieza : List.of(".column-header", ".filler")) {
                for (Node encabezado : tabla.lookupAll(pieza)) {
                    encabezado.setStyle(fondo);
                }
            }
        }
        String texto = nuevos
                ? "-fx-text-fill: #5A3516; -fx-font-size: 12px; -fx-alignment: CENTER_LEFT; -fx-padding: 0 10;"
                : "-fx-text-fill: #3B210F; -fx-font-weight: bold;";
        for (Node etiqueta : tabla.lookupAll(".column-header .label")) {
            etiqueta.setStyle(texto);
        }
    }

    // ---------------------------------------------------------------- pestañas

    private static void estilizarPestanas(TabPane pestanas) {
        for (Tab pestana : pestanas.getTabs()) {
            pintarPestana(pestana);
            pestana.selectedProperty().addListener((obs, antes, ahora) -> pintarPestana(pestana));
        }
        EstiloUtil.alArmarPiel(pestanas, () -> Platform.runLater(() -> {
            for (Node fondo : pestanas.lookupAll(".tab-header-background")) {
                fondo.setStyle("-fx-background-color: white;");
            }
            for (Node area : pestanas.lookupAll(".tab-header-area")) {
                area.setStyle("-fx-padding: 8 24 0 24;");
            }
        }));
    }

    private static void pintarPestana(Tab pestana) {
        pestana.setStyle(pestana.isSelected() ? TAB_ELEGIDA : TAB_NORMAL);
    }
}
