package untrm.hotel_san_antonio.controlador;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.servicio.CuentaService;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.Navegacion;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pantalla principal del modulo Habitaciones. El diseño esta en los FXML
 * (habitaciones, piso_seccion, tarjeta_habitacion); aqui solo se cargan los datos
 * y se decide que hacer al hacer clic segun el estado.
 */
public class HabitacionesController {

    private static final String RUTA_FXML = "/untrm/hotel_san_antonio/fxml/habitaciones/";

    @FXML private ComboBox<String> cmbEstado, cmbTipo;
    @FXML private VBox contenedorPisos;

    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final CuentaService cuentaService = new CuentaService();
    private final ReservaService reservaService = new ReservaService();

    private List<Habitacion> habitaciones = new ArrayList<>();
    private String estadoSeleccionado = null; // null = todos los estados
    private String tipoSeleccionado = null;   // null = todos los tipos

    @FXML
    public void initialize() {
        cargarHabitaciones();
    }

    @FXML
    private void onFiltrarEstado() {
        String opcion = cmbEstado.getValue();
        if (opcion == null || opcion.equals("Todas")) {
            estadoSeleccionado = null;
        } else {
            estadoSeleccionado = opcion.toUpperCase(); // "Disponible" -> "DISPONIBLE"
        }
        construirTarjetas();
    }

    @FXML
    private void onFiltrarTipo() {
        String opcion = cmbTipo.getValue();
        tipoSeleccionado = (opcion == null || opcion.equals("Todos")) ? null : opcion;
        construirTarjetas();
    }

    private void cargarHabitaciones() {
        try {
            habitaciones = habitacionDAO.listar();
        } catch (SQLException e) {
            Alertas.mostrarError("Error de conexión",
                    "No se pudieron cargar las habitaciones. Verifica que MySQL/XAMPP esté encendido.\n\n" + e.getMessage());
            return;
        }
        construirTarjetas();
    }

    private void construirTarjetas() {
        contenedorPisos.getChildren().clear();

        Map<Integer, List<Habitacion>> porPiso = new TreeMap<>();
        for (Habitacion h : habitaciones) {
            boolean cumpleEstado = estadoSeleccionado == null || estadoSeleccionado.equals(h.getEstado());
            boolean cumpleTipo = tipoSeleccionado == null || tipoSeleccionado.equals(h.getTipo().getNombre());
            if (cumpleEstado && cumpleTipo) {
                porPiso.computeIfAbsent(h.getPiso(), k -> new ArrayList<>()).add(h);
            }
        }

        try {
            for (Map.Entry<Integer, List<Habitacion>> piso : porPiso.entrySet()) {
                FXMLLoader cargaPiso = new FXMLLoader(getClass().getResource(RUTA_FXML + "piso_seccion.fxml"));
                cargaPiso.load();
                PisoSeccionController seccion = cargaPiso.getController();
                seccion.iniciar(piso.getKey(), piso.getValue().size());

                for (Habitacion h : piso.getValue()) {
                    FXMLLoader cargaTarjeta = new FXMLLoader(getClass().getResource(RUTA_FXML + "tarjeta_habitacion.fxml"));
                    cargaTarjeta.load();
                    TarjetaHabitacionController tarjeta = cargaTarjeta.getController();
                    tarjeta.setHabitacion(h, this::onClicHabitacion);
                    seccion.agregarTarjeta(tarjeta.getRaiz());
                }
                contenedorPisos.getChildren().add(seccion.getRaiz());
            }
        } catch (IOException e) {
            Alertas.mostrarError("Error", "No se pudo construir la pantalla.\n\n" + e.getMessage());
        }
    }

    private void onClicHabitacion(Habitacion h) {
        switch (h.getEstado()) {
            case "DISPONIBLE":
                if (h.getReservaHoy() != null) {
                    confirmarCheckIn(h);
                } else {
                    abrirFormularioReserva(h);
                }
                break;
            case "OCUPADA":
                abrirCuenta(h);
                break;
            case "LIMPIEZA":
                confirmarYLiberar(h, "¿Ya se limpió la habitación " + h.getNumero() + "?");
                break;
            case "MANTENIMIENTO":
                confirmarYLiberar(h, "¿Terminó el mantenimiento de la habitación " + h.getNumero() + "?");
                break;
            default:
                break;
        }
    }

    /** La habitacion libre tiene una reserva confirmada que llega hoy: se le hace el check-in. */
    private void confirmarCheckIn(Habitacion h) {
        if (!Alertas.confirmar("Check-in · Habitación " + h.getNumero(),
                "Hay una reserva confirmada para hoy a nombre de " + h.getReservaHoy()
                + ".\n\n¿Hacer el check-in? La habitación pasará a Ocupada.")) {
            return;
        }
        try {
            reservaService.hacerCheckIn(h.getIdHabitacion());
            cargarHabitaciones();
            abrirCuenta(h); // al llegar se cobra el 50 % restante
        } catch (IllegalStateException e) {
            Alertas.mostrarError("Check-in", e.getMessage());
            cargarHabitaciones();
        } catch (SQLException e) {
            Alertas.mostrarError("Error", "No se pudo hacer el check-in.\n\n" + e.getMessage());
        }
    }

    private void abrirCuenta(Habitacion h) {
        try {
            CuentaHabitacion cuenta = cuentaService.obtener(h.getIdHabitacion());
            if (cuenta == null) {
                // Figura ocupada pero no hay reserva en check-in (dato inconsistente)
                if (Alertas.confirmar("Habitación " + h.getNumero(), "La habitación figura como ocupada, pero no tiene una estadía activa registrada.\n\n¿Pasarla a Limpieza?")) {
                    if (!habitacionDAO.cambiarEstadoSiEs(h.getIdHabitacion(), "OCUPADA", "LIMPIEZA")) {
                        Alertas.mostrarInfo("Habitación " + h.getNumero(), "El estado de la habitación cambió mientras tanto.");
                    }
                    cargarHabitaciones();
                }
                return;
            }
            Navegacion.<CuentaHabitacionController>abrirModal(
                    RUTA_FXML + "cuenta_habitacion.fxml",
                    "Cuenta de la habitación",
                    controlador -> controlador.iniciar(h, cuenta, this::cargarHabitaciones));
        } catch (SQLException | IOException e) {
            Alertas.mostrarError("Error", "No se pudo abrir la cuenta.\n\n" + e.getMessage());
        }
    }

    private void abrirFormularioReserva(Habitacion h) {
        try {
            Navegacion.<ReservaFormController>abrirModal(
                    RUTA_FXML + "reserva_form.fxml",
                    "Registrar huésped y reserva",
                    controlador -> controlador.iniciar(h, this::cargarHabitaciones));
        } catch (IOException e) {
            Alertas.mostrarError("Error", "No se pudo abrir el formulario.\n\n" + e.getMessage());
        }
    }

    private void confirmarYLiberar(Habitacion h, String pregunta) {
        if (!Alertas.confirmar("Habitación " + h.getNumero(), pregunta)) {
            return;
        }
        try {
            if (!habitacionDAO.cambiarEstadoSiEs(h.getIdHabitacion(), h.getEstado(), "DISPONIBLE")) {
                Alertas.mostrarInfo("Habitación " + h.getNumero(), "El estado de la habitación cambió mientras tanto. Se actualizó la lista.");
            }
            cargarHabitaciones();
        } catch (SQLException e) {
            Alertas.mostrarError("Error", "No se pudo actualizar el estado.\n\n" + e.getMessage());
        }
    }
}
