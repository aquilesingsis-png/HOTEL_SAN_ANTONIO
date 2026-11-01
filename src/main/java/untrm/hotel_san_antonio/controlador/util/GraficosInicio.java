package untrm.hotel_san_antonio.controlador.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;

/** Gráficos y mini tablas de la pantalla de Inicio: se arman aquí y el controlador solo los coloca. */
public final class GraficosInicio {

    private GraficosInicio() { }

    private static final String FILA_MINI_NORMAL =
            "-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    private static final String FILA_MINI_IMPAR =
            "-fx-background-color: #FBF9F5; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    private static final String FILA_MINI_SELECCIONADA =
            "-fx-background-color: #704313; -fx-text-background-color: white;";

    /**
     * Gráfico de líneas con puntos: cuántas operaciones (ventas del carrito y cobros de reservas) hubo en cada
     * hora, para ver si el movimiento sube o baja a lo largo del día. Recepción ve las 24 horas de hoy; el
     * administrador, el acumulado de 30 días entre las 7h y las 21h (más si hay movimiento fuera de ese rango).
     */
    /** El gráfico de operaciones por hora; el administrador ve su horario habitual y las horas con movimiento. */
    public static Node graficoHoras(Map<Integer, Integer> porHora, boolean esAdministrador) {
        int desde = 0;
        int hasta = 23;
        if (esAdministrador) {
            desde = 7;
            hasta = 21;
            for (int hora : porHora.keySet()) {
                desde = Math.min(desde, hora);
                hasta = Math.max(hasta, hora);
            }
        }

        CategoryAxis ejeX = new CategoryAxis();
        NumberAxis ejeY = new NumberAxis();
        LineChart<String, Number> grafico = new LineChart<>(ejeX, ejeY);
        grafico.setLegendVisible(false);
        grafico.setAnimated(false);
        grafico.setCreateSymbols(true);
        grafico.setPrefHeight(230);
        grafico.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        grafico.setVerticalGridLinesVisible(false);

        int maximo = porHora.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int techo = Math.max(4, (int) Math.ceil(maximo * 1.2));
        ejeY.setAutoRanging(false);
        ejeY.setLowerBound(0);
        ejeY.setUpperBound(techo);
        ejeY.setTickUnit(Math.max(1, Math.ceil(techo / 4.0)));
        ejeY.setMinorTickVisible(false);
        ejeY.setTickMarkVisible(false);
        ejeX.setTickMarkVisible(false);
        ejeX.setTickLabelFill(Color.web("#A89B89"));
        ejeY.setTickLabelFill(Color.web("#A89B89"));
        ejeX.setStyle("-fx-font-size: 10.5px;");
        ejeY.setStyle("-fx-font-size: 10.5px;");

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (int hora = desde; hora <= hasta; hora++) {
            serie.getData().add(new XYChart.Data<>(hora + "h", porHora.getOrDefault(hora, 0)));
        }
        grafico.getData().add(serie);

        // al pasar el mouse por cada punto se ve cuántas operaciones fueron a esa hora
        for (XYChart.Data<String, Number> dato : serie.getData()) {
            Runnable ayuda = () -> {
                Node punto = dato.getNode();
                if (punto != null) {
                    int n = dato.getYValue().intValue();
                    Tooltip.install(punto, new Tooltip(n + (n == 1 ? " operación a las " : " operaciones a las ")
                            + dato.getXValue()));
                }
            };
            if (dato.getNode() != null) {
                ayuda.run();
            } else {
                dato.nodeProperty().addListener((obs, antes, nodo) -> ayuda.run());
            }
        }
        return grafico;
    }

    /** El gráfico de ingresos por día; con más de 7 días las etiquetas son dd/MM porque los nombres se repetirían. */
    public static Node graficoIngresos(Map<LocalDate, BigDecimal> ingresosPorDia) {
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
        ejeY.setAutoRanging(false);
        double maximo = ingresosPorDia.values().stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double techo = Math.max(100, Math.ceil((maximo * 1.2) / 100) * 100);
        ejeY.setUpperBound(techo);
        ejeY.setTickUnit(techo / 4);

        // Color y tamaño de las etiquetas de los ejes (antes ".income-chart .axis" en el .css)
        ejeX.setTickLabelFill(Color.web("#A89B89"));
        ejeY.setTickLabelFill(Color.web("#A89B89"));
        ejeX.setStyle("-fx-font-size: 10.5px;");
        ejeY.setStyle("-fx-font-size: 10.5px;");
        ejeX.setTickMarkVisible(false);
        ejeY.setTickMarkVisible(false);
        ejeY.setMinorTickVisible(false);

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (Map.Entry<LocalDate, BigDecimal> dia : ingresosPorDia.entrySet()) {
            String etiqueta = ingresosPorDia.size() > 7
                    ? dia.getKey().format(DateTimeFormatter.ofPattern("dd/MM"))
                    : dia.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.of("es", "PE"));
            serie.getData().add(new XYChart.Data<>(etiqueta, dia.getValue()));
        }
        grafico.getData().add(serie);

        return grafico;
    }

    /**
     * Colorea, en orden, las "rodajas" de un donut (PieChart). Un Chart es un Region, no un
     * Control (no tiene "piel" que avisar cuando esta lista): sus rodajas recien existen despues
     * del primer paso de layout, por eso hace falta esperar un par de pulsos con Platform.runLater.
     */
    public static void colorearRodajas(PieChart chart, String... colores) {
        Platform.runLater(() -> Platform.runLater(() -> {
            java.util.List<PieChart.Data> datos = chart.getData();
            for (int i = 0; i < datos.size() && i < colores.length; i++) {
                Node rodaja = datos.get(i).getNode();
                if (rodaja != null) {
                    rodaja.setStyle("-fx-pie-color: " + colores[i] + ";");
                }
            }
        }));
    }

    public static void estilizarMiniTabla(TableView<?> tabla) {

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

    private static <T> void aplicarFilasMiniTabla(TableView<T> tabla) {

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
}
