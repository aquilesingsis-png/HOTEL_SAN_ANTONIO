package untrm.hotel_san_antonio.controlador.Reserva;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

public class CalendarioOcupacionController {

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
    private VBox contenedorHabitaciones;

    @FXML
    private Label lblSinHabitaciones;


    // =========================================================
    // VARIABLES INTERNAS
    // =========================================================

    private final ToggleGroup grupoVista = new ToggleGroup();

    private YearMonth mesActual;

    private LocalDate inicioSemana;


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


        // Por ahora sin habitaciones
        mostrarSinDatos();
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
                .addAll(
                        "Todos los pisos",
                        "Piso 1",
                        "Piso 2"
                );


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

        /*
         * =====================================================
         * FUTURA CONEXIÓN MYSQL
         * =====================================================
         *
         * Aquí se obtendrán las habitaciones
         * según:
         *
         * - Piso seleccionado
         * - Texto de búsqueda
         * - Semana o mes
         * - Fecha seleccionada
         *
         *
         * Ejemplo:
         *
         * String piso =
         *         cbPiso.getValue();
         *
         * String busqueda =
         *         txtBuscarHabitacion
         *                 .getText()
         *                 .trim();
         *
         *
         * List<HabitacionCalendario> habitaciones =
         *
         *         calendarioService
         *             .obtenerDisponibilidad(
         *                 piso,
         *                 busqueda,
         *                 ...
         *             );
         *
         *
         * dibujarHabitaciones(habitaciones);
         *
         */


        mostrarSinDatos();
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


        if ("Piso 1".equals(piso)) {

            lblSinHabitaciones.setText(
                    "No hay habitaciones cargadas en el Piso 1."
            );

        } else if ("Piso 2".equals(piso)) {

            lblSinHabitaciones.setText(
                    "No hay habitaciones cargadas en el Piso 2."
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