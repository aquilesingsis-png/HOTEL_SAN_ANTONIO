package untrm.hotel_san_antonio.controlador.Reserva;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.EstiloUtil;

public class CalendarioOcupacionController {

    private static final String COLOR_DISPONIBLE = "#2CB95F";
    private static final String COLOR_OCUPADA = "#EF4141";
    private static final String COLOR_RESERVADA = "#2E88DD";
    private static final String COLOR_LIMPIEZA = "#EAB851";
    private static final String COLOR_MANTENIMIENTO = "#AFA79C";

    // =========================================================
    // CONTROLES FXML
    // =========================================================

    @FXML
    private TextField txtBuscarHabitacion;

    @FXML
    private Label lblPeriodo;

    @FXML
    private ToggleButton btnSemana;

    @FXML
    private ToggleButton btnMes;

    @FXML
    private ChoiceBox<String> cbPiso;

    @FXML
    private Label lblDia1;

    @FXML
    private Label lblDia2;

    @FXML
    private Label lblDia3;

    @FXML
    private Label lblDia4;

    @FXML
    private Label lblDia5;

    @FXML
    private Label lblDia6;

    @FXML
    private Label lblDia7;

    @FXML
    private ScrollPane scrollHabitaciones;

    @FXML
    private VBox contenedorHabitaciones;

    @FXML
    private Label lblSinHabitaciones;


    // =========================================================
    // VARIABLES INTERNAS
    // =========================================================

    private final ToggleGroup grupoVista = new ToggleGroup();

    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final ReservaService reservaService = new ReservaService();

    private YearMonth mesActual;

    private LocalDate inicioSemana;

    /** Primer dia de los 7 que se ven ahora mismo (lblDia1..lblDia7); lo calcula actualizarDias(). */
    private LocalDate inicioVentana;


    // =========================================================
    // ESTILOS
    // =========================================================

    private static final String ESTILO_NORMAL =
            "-fx-background-color: white;" +
            "-fx-border-color: #E5E5E5;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #222222;" +
            "-fx-font-size: 12px;" +
            "-fx-cursor: hand;";


    private static final String ESTILO_ACTIVO =
            "-fx-background-color: #B27617;" +
            "-fx-border-color: #B27617;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;";


    // =========================================================
    // INITIALIZE
    // =========================================================

    @FXML
    public void initialize() {

        // Fecha inicial
        mesActual = YearMonth.now();

        inicioSemana = LocalDate.now();


        // El interior del ScrollPane pinta blanco por defecto: se corrige apenas arme su piel.
        EstiloUtil.alArmarPiel(scrollHabitaciones, () -> {
            Node viewport = scrollHabitaciones.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent;");
            }
        });

        // Configurar botones Semana / Mes
        configurarVista();


        // Configurar pisos
        configurarPisos();


        // Configurar buscador
        configurarBuscador();


        // Mostrar periodo inicial
        actualizarPeriodo();


        // Mostrar días
        actualizarDias();


        // Cargar habitaciones y su ocupación desde la BD
        cargarDisponibilidad();
    }


    // =========================================================
    // CONFIGURAR SEMANA / MES
    // =========================================================

    private void configurarVista() {

        /*
         * Ambos botones pertenecen al mismo grupo.
         * Solo uno puede estar seleccionado.
         */
        btnSemana.setToggleGroup(grupoVista);

        btnMes.setToggleGroup(grupoVista);


        /*
         * Al iniciar, Mes estará seleccionado.
         */
        btnMes.setSelected(true);


        /*
         * Detectar cambio entre Semana y Mes.
         */
        grupoVista
                .selectedToggleProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            /*
                             * Evita que ambos botones
                             * queden deseleccionados.
                             */
                            if (actual == null) {

                                if (anterior != null) {

                                    anterior.setSelected(true);
                                }

                                return;
                            }


                            actualizarEstiloVista();
                        }
                );


        actualizarEstiloVista();
    }


    // =========================================================
    // COLOR SEMANA / MES
    // =========================================================

    private void actualizarEstiloVista() {

        if (btnSemana.isSelected()) {

            // Semana dorado
            btnSemana.setStyle(
                    ESTILO_ACTIVO
            );

            // Mes blanco
            btnMes.setStyle(
                    ESTILO_NORMAL
            );

        } else {

            // Semana blanco
            btnSemana.setStyle(
                    ESTILO_NORMAL
            );

            // Mes dorado
            btnMes.setStyle(
                    ESTILO_ACTIVO
            );
        }
    }


    // =========================================================
    // CONFIGURAR PISOS
    // =========================================================

    private void configurarPisos() {

        cbPiso
                .getItems()
                .clear();

        cbPiso
                .getItems()
                .add(
                        "Todos los pisos"
                );

        try (java.sql.Connection con = ConexionBD.conectar()) {
            for (Integer piso : habitacionDAO.listarPisos(con)) {
                cbPiso.getItems().add("Piso " + piso);
            }
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudieron cargar los pisos.\n\n" + e.getMessage());
        }

        cbPiso.setValue(
                "Todos los pisos"
        );


        /*
         * Cuando el usuario cambia de piso,
         * volveremos a consultar habitaciones.
         */
        cbPiso
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            cargarDisponibilidad();
                        }
                );
    }


    // =========================================================
    // BUSCADOR
    // =========================================================

    private void configurarBuscador() {

        txtBuscarHabitacion
                .textProperty()
                .addListener(
                        (observable, anterior, actual) -> {

                            cargarDisponibilidad();
                        }
                );
    }


    // =========================================================
    // PERIODO ANTERIOR
    // =========================================================

    @FXML
    private void periodoAnterior() {

        if (btnSemana.isSelected()) {

            // Retrocede una semana
            inicioSemana =
                    inicioSemana.minusWeeks(1);

        } else {

            // Retrocede un mes
            mesActual =
                    mesActual.minusMonths(1);
        }


        actualizarPeriodo();

        actualizarDias();

        cargarDisponibilidad();
    }


    // =========================================================
    // PERIODO SIGUIENTE
    // =========================================================

    @FXML
    private void periodoSiguiente() {

        if (btnSemana.isSelected()) {

            // Avanza una semana
            inicioSemana =
                    inicioSemana.plusWeeks(1);

        } else {

            // Avanza un mes
            mesActual =
                    mesActual.plusMonths(1);
        }


        actualizarPeriodo();

        actualizarDias();

        cargarDisponibilidad();
    }


    // =========================================================
    // CAMBIAR SEMANA / MES
    // =========================================================

    @FXML
    private void cambiarVista() {

        /*
         * El color cambia inmediatamente.
         */
        actualizarEstiloVista();


        /*
         * Al entrar a Semana usamos
         * la fecha actual como referencia.
         */
        if (btnSemana.isSelected()) {

            inicioSemana =
                    LocalDate.now();

        } else {

            mesActual =
                    YearMonth.now();
        }


        actualizarPeriodo();

        actualizarDias();

        cargarDisponibilidad();
    }


    // =========================================================
    // ACTUALIZAR PERIODO
    // =========================================================

    private void actualizarPeriodo() {

        if (btnSemana.isSelected()) {

            actualizarPeriodoSemana();

        } else {

            actualizarPeriodoMes();
        }
    }


    // =========================================================
    // PERIODO SEMANA
    // =========================================================

    private void actualizarPeriodoSemana() {

        LocalDate fin =
                inicioSemana.plusDays(6);


        String mesInicio =
                nombreMesCorto(
                        inicioSemana
                );


        String mesFin =
                nombreMesCorto(
                        fin
                );


        /*
         * Si la semana está dentro
         * del mismo mes.
         */
        if (inicioSemana.getMonth()
                == fin.getMonth()) {

            lblPeriodo.setText(
                    inicioSemana.getDayOfMonth()
                            + " - "
                            + fin.getDayOfMonth()
                            + " "
                            + mesFin
                            + " "
                            + fin.getYear()
            );

        } else {

            /*
             * Si la semana atraviesa
             * dos meses.
             */
            lblPeriodo.setText(
                    inicioSemana.getDayOfMonth()
                            + " "
                            + mesInicio
                            + " - "
                            + fin.getDayOfMonth()
                            + " "
                            + mesFin
                            + " "
                            + fin.getYear()
            );
        }
    }


    // =========================================================
    // PERIODO MES
    // =========================================================

    private void actualizarPeriodoMes() {

        String mes =
                mesActual
                        .getMonth()
                        .getDisplayName(
                                TextStyle.FULL,
                                new Locale(
                                        "es",
                                        "PE"
                                )
                        );


        /*
         * Primera letra en mayúscula.
         */
        mes =
                Character.toUpperCase(
                        mes.charAt(0)
                )
                + mes.substring(1);


        lblPeriodo.setText(
                mes
                        + " "
                        + mesActual.getYear()
        );
    }


    // =========================================================
    // ACTUALIZAR DÍAS
    // =========================================================

    private void actualizarDias() {

        LocalDate inicio;


        /*
         * Semana:
         * utilizamos inicioSemana.
         */
        if (btnSemana.isSelected()) {

            inicio = inicioSemana;

        } else {

            /*
             * Mes:
             * comenzamos en el día 1
             * del mes seleccionado.
             */
            inicio =
                    LocalDate.of(
                            mesActual.getYear(),
                            mesActual.getMonth(),
                            1
                    );
        }


        inicioVentana = inicio;

        Label[] labels = {
            lblDia1,
            lblDia2,
            lblDia3,
            lblDia4,
            lblDia5,
            lblDia6,
            lblDia7
        };


        for (int i = 0;
             i < labels.length;
             i++) {

            LocalDate fecha =
                    inicio.plusDays(i);


            String dia =
                    fecha
                            .getDayOfWeek()
                            .getDisplayName(
                                    TextStyle.SHORT,
                                    new Locale(
                                            "es",
                                            "PE"
                                    )
                            );


            /*
             * Ejemplo:
             *
             * lun.
             * 21
             */

            labels[i].setText(
                    dia
                            + "\n"
                            + fecha.getDayOfMonth()
            );
        }
    }


    // =========================================================
    // NOMBRE COMPLETO DEL MES
    // =========================================================

    private String nombreMes(
            LocalDate fecha
    ) {

        String mes =
                fecha
                        .getMonth()
                        .getDisplayName(
                                TextStyle.FULL,
                                new Locale(
                                        "es",
                                        "PE"
                                )
                        );


        return Character.toUpperCase(
                mes.charAt(0)
        ) + mes.substring(1);
    }


    // =========================================================
    // NOMBRE CORTO DEL MES
    // =========================================================

    private String nombreMesCorto(
            LocalDate fecha
    ) {

        return fecha
                .getMonth()
                .getDisplayName(
                        TextStyle.SHORT,
                        new Locale(
                                "es",
                                "PE"
                        )
                );
    }


    // =========================================================
    // CARGAR DISPONIBILIDAD
    // =========================================================

    private void cargarDisponibilidad() {

        if (inicioVentana == null) {
            return;
        }

        String piso = cbPiso == null ? null : cbPiso.getValue();
        Integer numeroPiso = (piso != null && piso.startsWith("Piso "))
                ? Integer.valueOf(piso.substring("Piso ".length())) : null;
        String busqueda = txtBuscarHabitacion == null ? "" : txtBuscarHabitacion.getText().trim().toLowerCase(Locale.ROOT);
        LocalDate finVentana = inicioVentana.plusDays(6);

        List<Habitacion> habitaciones;
        List<Reserva> reservas;
        try {
            habitaciones = habitacionDAO.listar();
            reservas = reservaService.listarEnRango(inicioVentana, finVentana);
        } catch (SQLException e) {
            Alertas.mostrarError("Error de base de datos", "No se pudo cargar la disponibilidad.\n\n" + e.getMessage());
            mostrarSinDatos();
            return;
        }

        habitaciones = habitaciones.stream()
                .filter(h -> numeroPiso == null || h.getPiso() == numeroPiso)
                .filter(h -> busqueda.isEmpty() || h.getNumero().toLowerCase(Locale.ROOT).contains(busqueda)
                        || h.getTipo().getNombre().toLowerCase(Locale.ROOT).contains(busqueda))
                .collect(Collectors.toList());

        if (habitaciones.isEmpty()) {
            mostrarSinDatos();
            return;
        }

        Map<Integer, List<Reserva>> reservasPorHabitacion = reservas.stream()
                .collect(Collectors.groupingBy(Reserva::getIdHabitacion));

        contenedorHabitaciones.getChildren().clear();
        for (Habitacion h : habitaciones) {
            contenedorHabitaciones.getChildren().add(
                    crearFilaHabitacion(h, reservasPorHabitacion.getOrDefault(h.getIdHabitacion(), List.of())));
        }
    }


    // =========================================================
    // FILA DE UNA HABITACIÓN (número/tipo + 7 días de color)
    // =========================================================

    private GridPane crearFilaHabitacion(Habitacion h, List<Reserva> reservasHabitacion) {

        GridPane fila = new GridPane();
        fila.getColumnConstraints().addAll(columnasCalendario());
        fila.setPrefHeight(46.0);
        fila.setMinHeight(46.0);
        fila.setStyle("-fx-border-color: transparent transparent #F0ECE2 transparent;");

        VBox etiqueta = new VBox(
                new Label(h.getNumero()),
                new Label(h.getTipo().getNombre())
        );
        etiqueta.setAlignment(Pos.CENTER);
        ((Label) etiqueta.getChildren().get(0)).setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #222222;");
        ((Label) etiqueta.getChildren().get(1)).setStyle("-fx-font-size: 10px; -fx-text-fill: #8A7F70;");
        GridPane.setColumnIndex(etiqueta, 0);
        fila.getChildren().add(etiqueta);

        for (int i = 0; i < 7; i++) {
            LocalDate dia = inicioVentana.plusDays(i);
            String color = colorParaDia(h, dia, reservasHabitacion);

            Region celda = new Region();
            celda.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 5;");
            celda.setPrefHeight(28.0);
            celda.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(celda, Priority.ALWAYS);
            Tooltip.install(celda, new Tooltip(h.getNumero() + " · " + dia + " · " + textoColor(color)));

            HBox contenedorCelda = new HBox(celda);
            contenedorCelda.setAlignment(Pos.CENTER);
            contenedorCelda.setStyle("-fx-padding: 6 8 6 8;");
            HBox.setHgrow(celda, Priority.ALWAYS);
            GridPane.setColumnIndex(contenedorCelda, i + 1);
            fila.getChildren().add(contenedorCelda);
        }

        return fila;
    }

    private List<ColumnConstraints> columnasCalendario() {
        List<ColumnConstraints> columnas = new ArrayList<>();
        ColumnConstraints primera = new ColumnConstraints();
        primera.setPercentWidth(10.0);
        columnas.add(primera);
        for (int i = 0; i < 6; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(12.85);
            columnas.add(c);
        }
        ColumnConstraints ultima = new ColumnConstraints();
        ultima.setPercentWidth(12.90);
        columnas.add(ultima);
        return columnas;
    }

    /** Color de la celda de un dia: la reserva vigente manda; si no hay, el estado actual de la habitacion. */
    private String colorParaDia(Habitacion h, LocalDate dia, List<Reserva> reservasHabitacion) {
        for (Reserva r : reservasHabitacion) {
            if (!dia.isBefore(r.getFechaCheckin()) && dia.isBefore(r.getFechaCheckout())) {
                return "CHECKIN".equals(r.getEstado()) ? COLOR_OCUPADA : COLOR_RESERVADA;
            }
        }
        if ("MANTENIMIENTO".equals(h.getEstado())) {
            return COLOR_MANTENIMIENTO;
        }
        if ("LIMPIEZA".equals(h.getEstado()) && dia.equals(LocalDate.now())) {
            return COLOR_LIMPIEZA;
        }
        return COLOR_DISPONIBLE;
    }

    private String textoColor(String color) {
        return switch (color) {
            case COLOR_OCUPADA -> "Ocupada";
            case COLOR_RESERVADA -> "Reservada";
            case COLOR_LIMPIEZA -> "En limpieza";
            case COLOR_MANTENIMIENTO -> "Mantenimiento";
            default -> "Disponible";
        };
    }


    // =========================================================
    // MOSTRAR CALENDARIO VACÍO
    // =========================================================

    private void mostrarSinDatos() {

        contenedorHabitaciones
                .getChildren()
                .clear();


        /*
         * Cambiamos ligeramente el mensaje
         * dependiendo del piso.
         */

        String piso =
                cbPiso != null
                        ? cbPiso.getValue()
                        : null;


        if (piso != null && piso.startsWith("Piso ")) {

            lblSinHabitaciones.setText(
                    "No hay habitaciones cargadas en el " + piso + "."
            );

        } else {

            lblSinHabitaciones.setText(
                    "No hay habitaciones cargadas."
            );
        }


        contenedorHabitaciones
                .getChildren()
                .add(
                        lblSinHabitaciones
                );
    }
}