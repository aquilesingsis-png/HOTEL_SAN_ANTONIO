package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import javafx.scene.Node;
import javafx.scene.chart.PieChart;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.modelo.TipoCambio;
import untrm.hotel_san_antonio.modelo.servicio.DecolectaTipoCambioService;
import untrm.hotel_san_antonio.modelo.servicio.DashboardService;
import untrm.hotel_san_antonio.controlador.util.Alertas;
import untrm.hotel_san_antonio.controlador.util.EstiloUtil;
import untrm.hotel_san_antonio.controlador.util.GraficosInicio;
import untrm.hotel_san_antonio.controlador.util.Navegacion;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;
import untrm.hotel_san_antonio.modelo.util.Registro;

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

    // Tarjetas que cambian según el rol y el widget del tipo de cambio
    @FXML private VBox tarjetaSemana;
    @FXML private VBox tarjetaDistribucion;
    @FXML private VBox tarjetaHoras;
    @FXML private Label lblTituloHoras;
    @FXML private Label lblSubtituloHoras;
    @FXML private Label lblPeriodoOcupacion;
    @FXML private Label lblSubOcupacion;
    @FXML private javafx.scene.control.MenuButton mbOcupacion;
    @FXML private javafx.scene.control.MenuButton mbHoras;
    @FXML private javafx.scene.control.MenuButton mbIngresos;
    // periodo (en días) que el administrador eligió en cada gráfico; 1 = hoy
    private int diasOcupacion = 1;
    private int diasHoras = 30;
    private int diasIngresos = 7;
    private DashboardService.Resumen resumen;
    @FXML private StackPane contenedorGraficoHoras;
    @FXML private Label lblTipoCambio;
    @FXML private Label lblTipoCambioFecha;

    @FXML private HBox cajaTipoCambio;
    private static final String RUTA_CONVERSOR = "/untrm/hotel_san_antonio/vista/dashboard/conversor_dolar.fxml";

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

    private final DashboardService dashboardService = new DashboardService();


    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    private javafx.animation.Timeline reloj;

    /** La fecha y la hora del Inicio se actualizan solas (cada segundo) mientras la pantalla está abierta. */
    private void iniciarReloj() {
        if (lblFecha == null || lblHora == null) return;
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("hh:mm a", Locale.of("es", "ES"));
        Runnable poner = () -> {
            LocalDateTime ahora = LocalDateTime.now();
            String fecha = ahora.format(formatoFecha);
            lblFecha.setText("Hoy es " + fecha.substring(0, 1).toUpperCase() + fecha.substring(1));
            lblHora.setText(ahora.format(formatoHora));
        };
        poner.run();
        reloj = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> poner.run()));
        reloj.setCycleCount(javafx.animation.Animation.INDEFINITE);
        reloj.play();
        // al cambiar de pantalla el reloj se detiene; si el Inicio vuelve a mostrarse, sigue
        lblHora.sceneProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) reloj.stop(); else reloj.play();
        });
    }

    @FXML
    public void initialize() {
        iniciarReloj();

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
                            Locale.of("es", "ES")
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

        configurarSegunRol();
        cargarTipoCambio();
        configurarColumnas();

        // Estilo de las mini-tablas (encabezado y filas), antes resuelto por ".mini-table" en el .css
        GraficosInicio.estilizarMiniTabla(tablaLlegadas);
        GraficosInicio.estilizarMiniTabla(tablaMantenimiento);

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

        resumen = r;
        mostrarOcupacion(r);

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
        GraficosInicio.colorearRodajas(chartDistribucion, "#8A5A1E", "#D9A53B");

        contenedorGraficoIngresos.getChildren().setAll(GraficosInicio.graficoIngresos(r.ingresosUltimaSemana));
        contenedorGraficoHoras.getChildren().setAll(GraficosInicio.graficoHoras(r.movimientoPorHora, SesionActual.esAdministrador()));
        if (SesionActual.esAdministrador()) {
            // respeta el periodo que el administrador eligió en cada gráfico
            actualizarHoras();
            actualizarIngresos();
        }

        tablaLlegadas.getItems().setAll(r.llegadasHoy);
        tablaMantenimiento.getItems().setAll(r.enMantenimiento);

        poblarAlertas(r);
    }

    /** Ocupación del hotel: hoy muestra el estado actual de las habitaciones; en 7 o 30 días, el promedio de ocupadas. */
    private void mostrarOcupacion(DashboardService.Resumen r) {
        if (diasOcupacion <= 1) {
            lblSubOcupacion.setText("Ocupación actual");
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
            GraficosInicio.colorearRodajas(chartOcupacion, "#8A5A1E", "#D8C39A", "#D9A53B", "#B3ACA2");
            return;
        }
        double promedio;
        try {
            promedio = dashboardService.ocupadasPromedio(diasOcupacion);
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo calcular la ocupación.\n\n" + e.getMessage());
            return;
        }
        double libres = Math.max(0, r.totalHabitaciones - promedio);
        int porcentaje = r.totalHabitaciones == 0 ? 0 : (int) Math.round(promedio * 100.0 / r.totalHabitaciones);
        lblSubOcupacion.setText("Ocupación promedio");
        lblPorcentajeOcupacion.setText(porcentaje + "%");
        lblLeyOcupadas.setText(String.format(Locale.US, "%.1f", promedio));
        lblLeyDisponibles.setText(String.format(Locale.US, "%.1f", libres));
        lblLeyLimpieza.setText("—");
        lblLeyMantenimiento.setText("—");
        chartOcupacion.setData(javafx.collections.FXCollections.observableArrayList(
                new PieChart.Data("Ocupadas", Math.max(promedio, 0.0001)),
                new PieChart.Data("Disponibles", Math.max(libres, 0.0001))
        ));
        GraficosInicio.colorearRodajas(chartOcupacion, "#8A5A1E", "#D8C39A");
    }

    private void actualizarOcupacion() {
        if (resumen != null) mostrarOcupacion(resumen);
    }

    private void actualizarHoras() {
        try {
            contenedorGraficoHoras.getChildren().setAll(GraficosInicio.graficoHoras(dashboardService.movimientoPorHora(diasHoras), SesionActual.esAdministrador()));
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cargar el movimiento por hora.\n\n" + e.getMessage());
        }
    }

    private void actualizarIngresos() {
        try {
            contenedorGraficoIngresos.getChildren().setAll(GraficosInicio.graficoIngresos(dashboardService.ingresosPorDia(diasIngresos)));
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar los ingresos.\n\n" + e.getMessage());
        }
    }

    private static String etiquetaPeriodo(int dias) {
        return dias == 1 ? "Hoy" : dias + " días";
    }

    /** Convierte la etiqueta del gráfico en un menú desplegable con los periodos que se pueden elegir. */
    private void armarPeriodo(javafx.scene.control.MenuButton boton, int[] opciones, int inicial,
                              java.util.function.IntConsumer alElegir) {
        boton.setText(etiquetaPeriodo(inicial));
        boton.getItems().clear();
        for (int dias : opciones) {
            javafx.scene.control.MenuItem opcion = new javafx.scene.control.MenuItem(etiquetaPeriodo(dias));
            opcion.setOnAction(e -> {
                boton.setText(etiquetaPeriodo(dias));
                alElegir.accept(dias);
            });
            boton.getItems().add(opcion);
        }
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


    // =========================================================
    // ALERTAS Y PENDIENTES
    // =========================================================

    private void poblarAlertas(DashboardService.Resumen r) {

        listaAlertas.getChildren().clear();

        if (SesionActual.esAdministrador()) {
            // Lo que le toca vigilar al administrador
            if (r.productosAgotados > 0) {
                listaAlertas.getChildren().add(crearAlerta("#D9433A",
                        plural(r.productosAgotados, "producto agotado", "productos agotados")));
            }
            if (r.productosStockBajo > 0) {
                listaAlertas.getChildren().add(crearAlerta("#E3A12F",
                        plural(r.productosStockBajo, "producto con stock bajo", "productos con stock bajo")));
            }
            if (r.cuentasBloqueadas > 0) {
                listaAlertas.getChildren().add(crearAlerta("#D9433A",
                        plural(r.cuentasBloqueadas, "cuenta bloqueada por intentos", "cuentas bloqueadas por intentos")));
            }
            if (r.diasSinRespaldo < 0) {
                listaAlertas.getChildren().add(crearAlerta("#D9433A", "Todavía no se hizo un respaldo de la base"));
            } else if (r.diasSinRespaldo > 0) {
                listaAlertas.getChildren().add(crearAlerta("#E3A12F",
                        "Hace " + r.diasSinRespaldo + " días sin respaldo de la base"));
            }
        } else {
            // Lo que le toca atender a Recepción hoy
            long sinCheckin = r.llegadasHoy.stream().filter(l -> "CONFIRMADA".equals(l.getEstado())).count();
            if (sinCheckin > 0) {
                listaAlertas.getChildren().add(crearAlerta("#E3A12F",
                        plural((int) sinCheckin, "llegada de hoy sin check-in", "llegadas de hoy sin check-in")));
            }
            if (!r.salidasHoy.isEmpty()) {
                listaAlertas.getChildren().add(crearAlerta("#6A9BD1",
                        plural(r.salidasHoy.size(), "salida de hoy", "salidas de hoy")
                                + ": " + String.join(", ", r.salidasHoy)));
            }
        }
        if (!r.estadiasVencidas.isEmpty()) {
            listaAlertas.getChildren().add(crearAlerta("#D9433A",
                    plural(r.estadiasVencidas.size(), "estadía vencida", "estadías vencidas")
                            + ": hab. " + String.join(", ", r.estadiasVencidas)));
        }
        if (r.diasSinRegistrar > 0 && r.registroAbierto) {
            listaAlertas.getChildren().add(crearAlerta("#E3A12F", plural(r.diasSinRegistrar,
                    "día sin registrar (abra «Registro de días pasados»)",
                    "días sin registrar (abra «Registro de días pasados»)")));
        }
        if (r.mantenimiento > 0) {
            listaAlertas.getChildren().add(crearAlerta("#B3ACA2",
                    plural(r.mantenimiento, "habitación en mantenimiento", "habitaciones en mantenimiento")));
        }
        if (r.limpieza > 0) {
            listaAlertas.getChildren().add(crearAlerta("#D9A53B",
                    plural(r.limpieza, "habitación en limpieza", "habitaciones en limpieza")));
        }
        if (listaAlertas.getChildren().isEmpty()) {
            Label sinAlertas = new Label("Sin alertas por ahora.");
            sinAlertas.setStyle("-fx-font-size: 12px; -fx-text-fill: #8A7F70;");
            listaAlertas.getChildren().add(sinAlertas);
        }
    }

    private String plural(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    // =========================================================
    // LO QUE CAMBIA SEGÚN EL ROL
    // =========================================================

    /**
     * El administrador ve todo. Recepción no ve el dinero acumulado (ingresos de la semana ni la distribución):
     * solo lo del día, y en lugar del gráfico semanal ve el movimiento de hoy hora por hora.
     */
    private void configurarSegunRol() {
        boolean admin = SesionActual.esAdministrador();
        mostrar(tarjetaSemana, admin);
        mostrar(tarjetaDistribucion, admin);
        lblTituloHoras.setText(admin ? "⏱  Horas de más movimiento" : "⏱  Movimiento de hoy por hora");
        lblSubtituloHoras.setText("Hoy");
        // Administrador: los periodos son menús que se pueden elegir. Recepción: solo se ve "Hoy", sin menú.
        mostrar(lblPeriodoOcupacion, !admin);
        mostrar(lblSubtituloHoras, !admin);
        mostrar(mbOcupacion, admin);
        mostrar(mbHoras, admin);
        mostrar(mbIngresos, admin);
        if (admin) {
            armarPeriodo(mbOcupacion, new int[]{1, 7, 30}, diasOcupacion, dias -> { diasOcupacion = dias; actualizarOcupacion(); });
            armarPeriodo(mbHoras, new int[]{1, 7, 30}, diasHoras, dias -> { diasHoras = dias; actualizarHoras(); });
            armarPeriodo(mbIngresos, new int[]{7, 30}, diasIngresos, dias -> { diasIngresos = dias; actualizarIngresos(); });
        }
    }

    private void mostrar(Node nodo, boolean visible) {
        if (nodo != null) {
            nodo.setVisible(visible);
            nodo.setManaged(visible);
        }
    }

    // =========================================================
    // TIPO DE CAMBIO (API de la SUNAT vía Decolecta)
    // =========================================================

    private void cargarTipoCambio() {
        // al hacer clic en la tarjeta se abre el conversor de dólares y soles
        cajaTipoCambio.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(cajaTipoCambio, new Tooltip("Clic para convertir dólares y soles"));
        cajaTipoCambio.setOnMouseClicked(e -> abrirConversor());

        TipoCambio vigente = DecolectaTipoCambioService.vigente();
        if (vigente != null) {
            mostrarTipoCambio(vigente);
            return;
        }
        javafx.concurrent.Task<TipoCambio> tarea = DecolectaTipoCambioService.consultarHoy();
        tarea.setOnSucceeded(e -> mostrarTipoCambio(tarea.getValue()));
        tarea.setOnFailed(e -> {
            Registro.fallo("DashboardController.cargarTipoCambio", tarea.getException());
            TipoCambio anterior = DecolectaTipoCambioService.ultimoConocido();
            if (anterior != null) {
                mostrarTipoCambio(anterior);
                return;
            }
            lblTipoCambio.setText("Dólar: no disponible");
            lblTipoCambioFecha.setText("Sin conexión o sin clave de la API");
        });
        Thread hilo = new Thread(tarea, "tipo-cambio");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Muestra cuántos soles vale 1 dólar: el precio de compra, que es el que usa el hotel. */
    private void mostrarTipoCambio(TipoCambio tc) {
        lblTipoCambio.setText(String.format(Locale.US, "1 US$ = S/ %.3f", tc.getCompra()));
        String fecha = tc.getFecha();
        try {
            fecha = LocalDate.parse(tc.getFecha()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (RuntimeException ignorado) {
            // si la API manda otro formato de fecha, se muestra tal cual
        }
        lblTipoCambioFecha.setText("Precio de compra · SUNAT " + fecha);
    }

    private void abrirConversor() {
        try {
            Navegacion.abrirModal(RUTA_CONVERSOR, "Conversor de dólares");
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Error", "No se pudo abrir el conversor.\n\n" + error.getMessage());
        }
    }

    // =========================================================
    // GRÁFICO DE MOVIMIENTO POR HORA
    // =========================================================

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
    // GRÁFICO DE INGRESOS (últimos 7 días)
    // =========================================================


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
        Object ruta = ((Node) event.getSource()).getUserData();
        if (!(ruta instanceof String destino)) {
            Alertas.mostrarError("Navegación", "Esta acción no tiene una pantalla configurada.");
            return;
        }
        try {
            // por el menú del marco, para que el título y la opción marcada cambien con la pantalla
            if (!PrincipalController.irAPantalla(destino)) {
                Navegacion.mostrar(destino);
            }
        } catch (IOException | SecurityException error) {
            Alertas.mostrarError("Navegación", "No se pudo abrir la lista solicitada.");
        }
    }
}
