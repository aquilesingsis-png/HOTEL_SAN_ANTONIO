package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.DashboardService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.EstiloUtil;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.SesionActual;

public class DashboardController {

    // =========================================================
    // ELEMENTOS DEL DASHBOARD
    // =========================================================

    @FXML
    private Label lblSaludo;

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
    private Label lblOcupadasTotal;

    @FXML
    private Label lblDisponibles;

    @FXML
    private Label lblDisponiblesTotal;

    @FXML
    private Label lblHuespedes;

    @FXML
    private Label lblReservasHoy;

    @FXML
    private Label lblIngresosDia;

    @FXML
    private Label lblVentasTienda;

    @FXML
    private Label lblVentasTiendaCantidad;

    @FXML
    private Label lblTendenciaOcupadas;

    @FXML
    private Label lblTendenciaDisponibles;

    @FXML
    private Label lblTendenciaHuespedes;

    @FXML
    private Label lblTendenciaReservasHoy;

    @FXML
    private Label lblTendenciaIngresos;

    @FXML
    private Label lblTendenciaVentas;

    @FXML
    private PieChart chartOcupacion;

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
    private PieChart chartDistribucion;

    @FXML
    private Label lblTotalIngresos;

    @FXML
    private Label lblPorcentajeHabitaciones;

    @FXML
    private Label lblPorcentajeTienda;

    @FXML
    private Label lblTotalHabitaciones;

    @FXML
    private VBox listaAlertas;

    @FXML
    private ScrollPane scrollDashboard;

    @FXML
    private TableView<Reserva> tablaLlegadas;

    @FXML
    private TableColumn<Reserva, String> colLlegadaHora;

    @FXML
    private TableColumn<Reserva, String> colLlegadaHuesped;

    @FXML
    private TableColumn<Reserva, String> colLlegadaHabitacion;

    @FXML
    private TableColumn<Reserva, String> colLlegadaTipo;

    @FXML
    private TableColumn<Reserva, String> colLlegadaEstado;

    @FXML
    private TableView<Habitacion> tablaMantenimiento;

    @FXML
    private TableColumn<Habitacion, String> colMantHabitacion;

    @FXML
    private TableColumn<Habitacion, String> colMantTipo;

    @FXML
    private TableColumn<Habitacion, String> colMantMotivo;

    @FXML
    private TableColumn<Habitacion, String> colMantEstado;

    // Colores de las filas de las mini-tablas (antes en estilos.css, ".mini-table .table-row-cell").
    private static final String FILA_MINI_NORMAL =
            "-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    private static final String FILA_MINI_IMPAR =
            "-fx-background-color: #FBF9F5; -fx-border-color: transparent transparent #F0ECE2 transparent;";
    private static final String FILA_MINI_SELECCIONADA =
            "-fx-background-color: #F5E6C8; -fx-text-fill: #2C2118;";

    private final DashboardService dashboardService = new DashboardService();


    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    @FXML
    public void initialize() {

        // Saludo segun el rol de quien inicio sesion (no siempre es el Administrador)
        if (lblSaludo != null) {
            String rol = SesionActual.getUsuario() != null ? SesionActual.getUsuario().getRol() : null;
            lblSaludo.setText("¡Hola, " + ("RECEPCIONISTA".equals(rol) ? "Recepcionista" : "Administrador") + "!");
        }

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

        // Interior del ScrollPane transparente (por defecto pinta blanco y no se puede fijar con style="")
        if (scrollDashboard != null) {
            EstiloUtil.alArmarPiel(scrollDashboard, () -> {
                Node viewport = scrollDashboard.lookup(".viewport");
                if (viewport != null) {
                    viewport.setStyle("-fx-background-color: transparent;");
                }
            });
        }

        configurarColumnas();

        // Estilo de las mini-tablas (encabezado y filas), antes resuelto por ".mini-table" en el .css
        estilizarMiniTabla(tablaLlegadas);
        estilizarMiniTabla(tablaMantenimiento);

        cargarDatos();

        // Mostrar Dashboard al iniciar
        mostrarDashboard();
    }


    // =========================================================
    // CARGAR DATOS REALES DE LA BD
    // =========================================================

    private void cargarDatos() {

        DashboardService.Resumen r;
        try {
            r = dashboardService.obtenerResumen();
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cargar el dashboard.\n\n" + e.getMessage());
            return;
        }

        lblOcupadas.setText(String.valueOf(r.ocupadas));
        lblOcupadasTotal.setText("de " + r.totalHabitaciones + " habitaciones");
        lblDisponibles.setText(String.valueOf(r.disponibles));
        lblDisponiblesTotal.setText("de " + r.totalHabitaciones + " habitaciones");
        lblTotalHabitaciones.setText(String.valueOf(r.totalHabitaciones));
        lblHuespedes.setText(String.valueOf(r.huespedes));
        lblReservasHoy.setText(String.valueOf(r.reservasHoy));

        // Comparacion real contra ayer (no numeros fijos) en cada tarjeta KPI
        aplicarTendencia(lblTendenciaOcupadas, r.ocupadas, r.ocupadasAyer);
        aplicarTendencia(lblTendenciaDisponibles, r.disponibles, r.disponiblesAyer);
        aplicarTendencia(lblTendenciaHuespedes, r.huespedes, r.huespedesAyer);
        aplicarTendencia(lblTendenciaReservasHoy, r.reservasHoy, r.reservasAyer);
        aplicarTendencia(lblTendenciaIngresos, r.getIngresosHoy().doubleValue(), r.ingresosAyer.doubleValue());
        aplicarTendencia(lblTendenciaVentas, r.ingresosTiendaHoy.doubleValue(), r.ventasTiendaAyer.doubleValue());
        lblIngresosDia.setText(formatoMoneda(r.getIngresosHoy()));
        lblVentasTienda.setText(formatoMoneda(r.ingresosTiendaHoy));
        lblVentasTiendaCantidad.setText(r.ventasTiendaHoyCantidad
                + (r.ventasTiendaHoyCantidad == 1 ? " venta realizada" : " ventas realizadas"));

        lblPorcentajeOcupacion.setText(r.getPorcentajeOcupacion() + "%");
        lblLeyOcupadas.setText(String.valueOf(r.ocupadas));
        lblLeyDisponibles.setText(String.valueOf(r.disponibles));
        lblLeyLimpieza.setText(String.valueOf(r.limpieza));
        lblLeyMantenimiento.setText(String.valueOf(r.mantenimiento));
        chartOcupacion.setData(javafx.collections.FXCollections.observableArrayList(
                new PieChart.Data("Ocupadas", Math.max(r.ocupadas, 0.0001)),
                new PieChart.Data("Disponibles", Math.max(r.disponibles, 0.0001)),
                new PieChart.Data("Limpieza", Math.max(r.limpieza, 0.0001)),
                new PieChart.Data("Mantenimiento", Math.max(r.mantenimiento, 0.0001))
        ));
        colorearRodajas(chartOcupacion, "#8A5A1E", "#D8C39A", "#D9A53B", "#B3ACA2");

        BigDecimal ingresosHoy = r.getIngresosHoy();
        int porcentajeHabitaciones = porcentaje(r.ingresosHabitacionesHoy, ingresosHoy);
        int porcentajeTienda = 100 - porcentajeHabitaciones;
        if (ingresosHoy.signum() == 0) {
            porcentajeTienda = 0;
        }
        lblTotalIngresos.setText(formatoMoneda(ingresosHoy));
        lblPorcentajeHabitaciones.setText(porcentajeHabitaciones + "%");
        lblPorcentajeTienda.setText(porcentajeTienda + "%");
        chartDistribucion.setData(javafx.collections.FXCollections.observableArrayList(
                new PieChart.Data("Habitaciones", r.ingresosHabitacionesHoy.doubleValue() + 0.0001),
                new PieChart.Data("Tienda", r.ingresosTiendaHoy.doubleValue() + 0.0001)
        ));
        colorearRodajas(chartDistribucion, "#8A5A1E", "#D9A53B");

        crearGraficoIngresos(r.ingresosUltimaSemana);

        tablaLlegadas.getItems().setAll(r.llegadasHoy);
        tablaMantenimiento.getItems().setAll(r.enMantenimiento);

        poblarAlertas(r);
    }

    private String formatoMoneda(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }

    private int porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? 0
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).intValue();
    }

    /** Compara "hoy" contra "ayer" y pinta la tarjeta KPI con una flecha y un color reales, no fijos. */
    private void aplicarTendencia(Label lbl, double hoy, double ayer) {
        String flecha;
        String color;
        long cambio;

        if (ayer <= 0) {
            cambio = hoy > 0 ? 100 : 0;
            flecha = hoy > 0 ? "▲" : "—";
            color = hoy > 0 ? "#2F9E55" : "#8A7F70";
        } else {
            cambio = Math.round(((hoy - ayer) / ayer) * 100);
            flecha = cambio > 0 ? "▲" : cambio < 0 ? "▼" : "—";
            color = cambio > 0 ? "#2F9E55" : cambio < 0 ? "#D9433A" : "#8A7F70";
        }

        lbl.setText(flecha + " " + Math.abs(cambio) + "%");
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 8 10 0 0;");
    }

    /**
     * Colorea, en orden, las "rodajas" de un donut (PieChart). Un Chart es un Region, no un
     * Control (no tiene "piel" que avisar cuando esta lista): sus rodajas recien existen despues
     * del primer paso de layout, por eso hace falta esperar un par de pulsos con Platform.runLater.
     */
    private void colorearRodajas(PieChart chart, String... colores) {
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


    // =========================================================
    // ALERTAS Y PENDIENTES
    // =========================================================

    private void poblarAlertas(DashboardService.Resumen r) {

        listaAlertas.getChildren().clear();

        if (r.pendientesConfirmar > 0) {
            listaAlertas.getChildren().add(crearAlerta("#E3A12F",
                    r.pendientesConfirmar + (r.pendientesConfirmar == 1
                            ? " reserva pendiente de confirmar" : " reservas pendientes de confirmar")));
        }
        if (r.mantenimiento > 0) {
            listaAlertas.getChildren().add(crearAlerta("#B3ACA2",
                    r.mantenimiento + (r.mantenimiento == 1
                            ? " habitación en mantenimiento" : " habitaciones en mantenimiento")));
        }
        if (r.limpieza > 0) {
            listaAlertas.getChildren().add(crearAlerta("#D9A53B",
                    r.limpieza + (r.limpieza == 1 ? " habitación en limpieza" : " habitaciones en limpieza")));
        }
        if (listaAlertas.getChildren().isEmpty()) {
            Label sinAlertas = new Label("Sin alertas por ahora.");
            sinAlertas.setStyle("-fx-font-size: 12px; -fx-text-fill: #8A7F70;");
            listaAlertas.getChildren().add(sinAlertas);
        }
    }

    private HBox crearAlerta(String color, String texto) {
        Region punto = new Region();
        punto.setPrefSize(8.0, 8.0);
        punto.setMaxSize(8.0, 8.0);
        punto.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");
        Label lbl = new Label(texto);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C2118;");
        HBox fila = new HBox(8.0, punto, lbl);
        fila.setStyle("-fx-alignment: CENTER_LEFT;");
        return fila;
    }


    // =========================================================
    // COLUMNAS DE LAS MINI-TABLAS
    // =========================================================

    private void configurarColumnas() {

        colLlegadaHora.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getHoraCheckin() == null ? "--"
                        : d.getValue().getHoraCheckin().format(DateTimeFormatter.ofPattern("HH:mm"))));
        colLlegadaHuesped.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getNombreHuesped()));
        colLlegadaHabitacion.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getNumeroHabitacion()));
        colLlegadaTipo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getNombreTipoHabitacion()));
        colLlegadaEstado.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                "CHECKIN".equals(d.getValue().getEstado()) ? "Con check-in" : "Confirmada"));

        colMantHabitacion.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getNumero()));
        colMantTipo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getTipo().getNombre()));
        colMantMotivo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getMotivoMantenimiento() == null || d.getValue().getMotivoMantenimiento().isBlank()
                        ? "--" : d.getValue().getMotivoMantenimiento()));
        colMantEstado.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty("Mantenimiento"));
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
    // GRÁFICO DE INGRESOS (últimos 7 días)
    // =========================================================

    private void crearGraficoIngresos(Map<LocalDate, BigDecimal> ingresosPorDia) {

        contenedorGraficoIngresos.getChildren().clear();

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
            String etiqueta = dia.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("es", "PE"));
            serie.getData().add(new XYChart.Data<>(etiqueta, dia.getValue()));
        }
        grafico.getData().add(serie);

        contenedorGraficoIngresos.getChildren().add(grafico);
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

        texto = texto.trim().toLowerCase(Locale.ROOT);

        if (texto.isEmpty()) {
            cargarDatos();
            return;
        }

        String filtro = texto;
        tablaLlegadas.getItems().removeIf(r ->
                !r.getNombreHuesped().toLowerCase(Locale.ROOT).contains(filtro)
                && !r.getNumeroHabitacion().toLowerCase(Locale.ROOT).contains(filtro));
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
                        "/untrm/hotel_san_antonio/fxml/login/login.fxml"
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
