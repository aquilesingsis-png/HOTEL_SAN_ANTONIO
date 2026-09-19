package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;
import untrm.hotel_san_antonio.util.SesionActual;

import java.io.IOException;

public class DashboardController {

    @FXML private Label lblUsuarioActual;

    @FXML
    public void initialize() {
        if (SesionActual.getUsuario() != null) {
            lblUsuarioActual.setText("· " + SesionActual.getUsuario().getNombreCompleto()
                    + " (" + SesionActual.getUsuario().getRol() + ")");
        }
        // TODO (Unidad II/III): si SesionActual.esAdministrador(), mostrar ademas
        // las tarjetas de Almacen, Usuarios y Reportes.
    }

    @FXML
    private void onAbrirHabitaciones() {
        // TODO: reemplazar por Navegacion.irA(".../fxml/habitaciones.fxml") cuando esa pantalla exista
        Alertas.mostrarInfo("Habitaciones", "Pantalla en construcción por el equipo.");
    }

    @FXML
    private void onAbrirReservas() {
        // TODO: reemplazar por Navegacion.irA(".../fxml/reservas.fxml") cuando esa pantalla exista
        Alertas.mostrarInfo("Reservas", "Pantalla en construcción por el equipo.");
    }

    @FXML
    private void onAbrirCarrito() {
        // TODO: reemplazar por Navegacion.abrirModal(".../fxml/carrito_tienda.fxml", "Carrito") cuando exista
        Alertas.mostrarInfo("Tiendita", "Carrito flotante en construcción por el equipo.");
    }

    @FXML
    private void onCerrarSesion() {
        if (Alertas.confirmar("Cerrar sesión", "¿Seguro que deseas cerrar sesión?")) {
            SesionActual.cerrar();
            try {
                Navegacion.irA("/untrm/hotel_san_antonio/fxml/login.fxml");
            } catch (IOException e) {
                Alertas.mostrarError("Error", "No se pudo volver al Login.\n\n" + e.getMessage());
            }
        }
    }
}
