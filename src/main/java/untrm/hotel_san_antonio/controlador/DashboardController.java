package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.paint.Color;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloUtil;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.SesionActual;

public class DashboardController {

    // =========================================================
    // ELEMENTOS DEL DASHBOARD
    // =========================================================

    @FXML
    private Label lblFecha;

    @FXML
    private Label lblPlaceholderTitulo;

    @FXML
    private VBox dashboardView;

    @FXML
    private VBox placeholderView;

    @FXML
    private TextField txtBuscar;

    @FXML
    private StackPane contenedorGraficoIngresos;

    @FXML
    private Label lblHora;

    @FXML
    private Label lblOcupadas;

    @FXML
    private Label lblDisponibles;

    @FXML
    private Label lblHuespedes;

    @FXML
    private Label lblReservasHoy;

    @FXML
    private Label lblIngresosDia;

    @FXML
    private Label lblVentasTienda;

    @FXML
    private javafx.scene.chart.PieChart chartOcupacion;

    @FXML
    private Label lblPorcentajeOcupacion;

    @FXML
    private Label lblLeyOcupadas;

    @FXML
    private Label lblLeyDisponibles;

    @FXML
    private Label lblLeyLimpieza;

    @FXML
    private Label lblLeyMantenimiento;

    @FXML
    private javafx.scene.chart.PieChart chartDistribucion;

    @FXML
    private Label lblTotalIngresos;

    @FXML
    private VBox listaAlertas;

    @FXML
    private ScrollPane scrollDashboard;

    @FXML
    private TableView<?> tablaLlegadas;

    @FXML
    private TableView<?> tablaMantenimiento;

    // Colores de las filas de las mini-tablas (antes en estilos.css, ".mini-table .table-row-cell").
    private static final String FILA_MINI_NORMAL =
            "-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    private static final String FILA_MINI_IMPAR =
            "-fx-background-color: #FBF9F5; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    private static final String FILA_MINI_SELECCIONADA =
            "-fx-background-color: #F5E6C8; -fx-text-fill: #2C2118;";


    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    @FXML
    public void initialize() {

        // Mostrar la fecha actual
        if (lblFecha != null) {

            DateTimeFormatter formato =
                    DateTimeFormatter.ofPattern(
                            "EEEE, dd 'de' MMMM 'de' yyyy",
                            new Locale("es", "ES")
                    );

            String fecha = LocalDate.now()
                    .format(formato);

            // Primera letra en mayúscula
            fecha = fecha.substring(0, 1).toUpperCase()
                    + fecha.substring(1);

            lblFecha.setText("Hoy es " + fecha);
        }

        // Crear gráfico de ingresos
        crearGraficoIngresos();

        // Interior del ScrollPane transparente (por defecto pinta blanco y no se puede fijar con style="")
        if (scrollDashboard != null) {
            EstiloUtil.alArmarPiel(scrollDashboard, () -> {
                Node viewport = scrollDashboard.lookup(".viewport");
                if (viewport != null) {
                    viewport.setStyle("-fx-background-color: transparent;");
                }
            });
        }

        // Estilo de las mini-tablas (encabezado y filas), antes resuelto por ".mini-table" en el .css
        estilizarMiniTabla(tablaLlegadas);
        estilizarMiniTabla(tablaMantenimiento);

        // Mostrar Dashboard al iniciar
        mostrarDashboard();
    }


    // =========================================================
    // ESTILO DE LAS MINI-TABLAS (llegadas / mantenimiento)
    // =========================================================

    private void estilizarMiniTabla(TableView<?> tabla) {

        if (tabla == null) {
            return;
        }

        tabla.setStyle("-fx-background-color: transparent; -fx-font-size: 11.5px;");

        aplicarFilasMiniTabla(tabla);

        // Los encabezados de columna del TableView son piezas internas que la piel arma recien
        // en su primer paso de layout: hace falta esperar un pulso mas alla de "la piel ya existe".
        EstiloUtil.alArmarPiel(tabla, () -> Platform.runLater(() -> {

            Node fondoEncabezado = tabla.lookup(".column-header-background");
            if (fondoEncabezado != null) {
                fondoEncabezado.setStyle("-fx-background-color: transparent;");
            }

            for (Node encabezado : tabla.lookupAll(".column-header")) {
                encabezado.setStyle(
                        "-fx-background-color: transparent; -fx-border-color: transparent transparent #E7E0D3 transparent;");
            }

            for (Node relleno : tabla.lookupAll(".filler")) {
                relleno.setStyle(
                        "-fx-background-color: transparent; -fx-border-color: transparent transparent #E7E0D3 transparent;");
            }

            for (Node etiqueta : tabla.lookupAll(".column-header .label")) {
                etiqueta.setStyle("-fx-text-fill: #8A7F70; -fx-font-weight: normal; -fx-font-size: 10.5px;");
            }
        }));
    }

    private <T> void aplicarFilasMiniTabla(TableView<T> tabla) {

        tabla.setFixedCellSize(34);

        tabla.setRowFactory(t -> {

            TableRow<T> fila = new TableRow<>();

            Runnable actualizar = () -> {
                if (fila.isEmpty()) {
                    fila.setStyle("");
                } else if (fila.isSelected()) {
                    fila.setStyle(FILA_MINI_SELECCIONADA);
                } else if (fila.getIndex() % 2 != 0) {
                    fila.setStyle(FILA_MINI_IMPAR);
                } else {
                    fila.setStyle(FILA_MINI_NORMAL);
                }
            };

            fila.selectedProperty().addListener((obs, antes, ahora) -> actualizar.run());
            fila.indexProperty().addListener((obs, antes, ahora) -> actualizar.run());
            fila.itemProperty().addListener((obs, antes, ahora) -> actualizar.run());

            return fila;
        });
    }


    // =========================================================
    // CREAR GRÁFICO DE INGRESOS
    // =========================================================

    private void crearGraficoIngresos() {

        CategoryAxis ejeX = new CategoryAxis();

        NumberAxis ejeY = new NumberAxis();

        AreaChart<String, Number> grafico =
                new AreaChart<>(ejeX, ejeY);

        grafico.setLegendVisible(false);

        grafico.setAnimated(false);

        grafico.setPrefHeight(230);

        grafico.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        grafico.setVerticalGridLinesVisible(false);

        ejeY.setLowerBound(0);

        ejeY.setUpperBound(4000);

        ejeY.setTickUnit(1000);

        // Color y tamaño de las etiquetas de los ejes (antes ".income-chart .axis" en el .css)
        ejeX.setTickLabelFill(Color.web("#A89B89"));
        ejeY.setTickLabelFill(Color.web("#A89B89"));
        ejeX.setStyle("-fx-font-size: 10.5px;");
        ejeY.setStyle("-fx-font-size: 10.5px;");
        ejeX.setTickMarkVisible(false);
        ejeY.setTickMarkVisible(false);
        ejeY.setMinorTickVisible(false);

        if (contenedorGraficoIngresos != null) {

            contenedorGraficoIngresos
                    .getChildren()
                    .add(grafico);
        }
    }


    // =========================================================
    // MOSTRAR DASHBOARD
    // =========================================================

    @FXML
    private void mostrarDashboard() {

        if (dashboardView != null) {

            dashboardView.setVisible(true);

            dashboardView.setManaged(true);
        }

        if (placeholderView != null) {

            placeholderView.setVisible(false);

            placeholderView.setManaged(false);
        }
    }


    // =========================================================
    // MOSTRAR OTRAS SECCIONES
    // =========================================================

    @FXML
    private void mostrarSeccion(ActionEvent event) {

        if (dashboardView != null) {

            dashboardView.setVisible(false);

            dashboardView.setManaged(false);
        }

        if (placeholderView != null) {

            placeholderView.setVisible(true);

            placeholderView.setManaged(true);
        }

        if (lblPlaceholderTitulo != null) {

            lblPlaceholderTitulo.setText(
                    "Sección en construcción"
            );
        }
    }


    // =========================================================
    // NOTIFICACIONES
    // =========================================================

    @FXML
    private void mostrarNotificaciones() {

        Alertas.mostrarInfo(
                "Notificaciones",
                "No tienes nuevas notificaciones."
        );
    }


    // =========================================================
    // PERFIL
    // =========================================================

    @FXML
    private void verPerfil() {

        String mensaje = "Información del usuario";

        if (SesionActual.getUsuario() != null) {

            mensaje =
                    "Usuario: "
                    + SesionActual.getUsuario().getNombreCompleto()
                    + "\nRol: "
                    + SesionActual.getUsuario().getRol();
        }

        Alertas.mostrarInfo(
                "Mi perfil",
                mensaje
        );
    }


    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    @FXML
    private void verConfiguracion() {

        Alertas.mostrarInfo(
                "Configuración",
                "La configuración estará disponible próximamente."
        );
    }


    // =========================================================
    // BUSCADOR
    // =========================================================

    @FXML
    private void filtrarLlegadas() {

        if (txtBuscar == null) {
            return;
        }

        String texto = txtBuscar.getText();

        if (texto == null) {
            texto = "";
        }

        texto = texto.trim();

        // Por ahora solamente obtenemos el texto.
        // Aquí posteriormente podemos conectar
        // la búsqueda con las tablas del Dashboard.
    }


    // =========================================================
    // CERRAR SESIÓN
    // =========================================================

    @FXML
    private void cerrarSesion() {

        if (Alertas.confirmar(
                "Cerrar sesión",
                "¿Seguro que deseas cerrar sesión?"
        )) {

            SesionActual.cerrar();

            try {

                Navegacion.irA(
                        "/untrm/hotel_san_antonio/fxml/login.fxml"
                );

            } catch (IOException e) {

                Alertas.mostrarError(
                        "Error",
                        "No se pudo volver al Login.\n\n"
                        + e.getMessage()
                );
            }
        }
    }
}