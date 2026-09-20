/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package untrm.hotel_san_antonio.controlador.Reserva;


import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class ConfirmarReservaController {

    @FXML
    private TextField txtBuscarReserva;

    @FXML
    private Label lblEstado;

    @FXML
    private Label lblCodigo;

    @FXML
    private Label lblFechaReserva;

    @FXML
    private Label lblCliente;

    @FXML
    private Label lblDocumento;

    @FXML
    private Label lblTelefono;

    @FXML
    private Label lblCorreo;

    @FXML
    private Label lblIngreso;

    @FXML
    private Label lblSalida;

    @FXML
    private Label lblNoches;

    @FXML
    private Label lblHuespedes;

    @FXML
    private Label lblHabitacion;

    @FXML
    private Label lblTotal;

    @FXML
    private TextArea txtObservaciones;

    @FXML
    private ChoiceBox<String> cbEstado;

    @FXML
    private TextArea txtMensaje;

    @FXML
    private CheckBox chkCorreo;


    @FXML
    public void initialize() {

        cbEstado.getItems().addAll(
                "Pendiente",
                "Confirmada"
        );

        cbEstado.setValue(
                "Confirmada"
        );

        limpiar();
    }


    @FXML
    private void buscarReserva() {

        String busqueda =
                txtBuscarReserva
                        .getText()
                        .trim();

        if (busqueda.isEmpty()) {

            mostrarAdvertencia(
                    "Búsqueda requerida",
                    "Ingrese código, nombre o documento."
            );

            return;
        }


        /*
         * AQUÍ SE CONECTARÁ MYSQL.
         *
         * Reserva reserva =
         *     reservaService.buscar(busqueda);
         *
         * cargarReserva(reserva);
         */

        mostrarInformacion(
                "Sin base de datos",
                "La búsqueda estará disponible cuando conectes MySQL."
        );
    }


    @FXML
    private void confirmarReserva() {

        if ("--".equals(lblCodigo.getText())) {

            mostrarAdvertencia(
                    "Reserva requerida",
                    "Primero seleccione una reserva."
            );

            return;
        }


        /*
         * FUTURO:
         *
         * reservaService.confirmar(idReserva);
         *
         * habitacionService.actualizarEstado(...);
         */


        mostrarInformacion(
                "Confirmación",
                "La función está preparada para conectarse a MySQL."
        );
    }


    @FXML
    private void volver() {

        limpiar();
    }


    private void limpiar() {

        lblEstado.setText(
                "Sin reserva seleccionada"
        );

        lblCodigo.setText("--");

        lblFechaReserva.setText("--");

        lblCliente.setText("--");

        lblDocumento.setText("--");

        lblTelefono.setText("--");

        lblCorreo.setText("--");

        lblIngreso.setText("--");

        lblSalida.setText("--");

        lblNoches.setText("--");

        lblHuespedes.setText("--");

        lblHabitacion.setText("--");

        lblTotal.setText("S/ 0.00");

        txtObservaciones.clear();

        txtMensaje.clear();

        chkCorreo.setSelected(false);
    }


    private void mostrarAdvertencia(
            String titulo,
            String mensaje
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.WARNING
                );

        alert.setTitle(titulo);

        alert.setHeaderText(null);

        alert.setContentText(mensaje);

        alert.showAndWait();
    }


    private void mostrarInformacion(
            String titulo,
            String mensaje
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(titulo);

        alert.setHeaderText(null);

        alert.setContentText(mensaje);

        alert.showAndWait();
    }
}
