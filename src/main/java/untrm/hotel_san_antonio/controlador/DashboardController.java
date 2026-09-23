package untrm.hotel_san_antonio.controlador;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.event.ActionEvent;
import javafx.FXML;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import untrm.hotel_san_antonio.util.Alertas;
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

        // Mostrar Dashboard al iniciar
        mostrarDashboard();
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

        ejeY.setLowerBound(0);

        ejeY.setUpperBound(4000);

        ejeY.setTickUnit(1000);

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
    // TIENDITA / CARRITO
    // =========================================================

    @FXML
    private void onAbrirCarrito() {
        try {
            Navegacion.abrirModal("/untrm/hotel_san_antonio/fxml/carrito_tienda.fxml", "Tiendita / Carrito");
        } catch (IOException e) {
            Alertas.mostrarError("Tiendita", "No se pudo abrir el carrito.\n\n" + e.getMessage());
        }
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