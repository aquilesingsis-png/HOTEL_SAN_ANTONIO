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

public class CancelarReservaController {

    @FXML
    private TextField txtBuscarReserva;

    @FXML
    private Label lblEstado;

    @FXML
    private Label lblCodigo;

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
    private Label lblHabitacion;

    @FXML
    private Label lblHuespedes;

    @FXML
    private Label lblTotal;

    @FXML
    private TextArea txtObservaciones;

    @FXML
    private ChoiceBox<String> cbMotivo;

    @FXML
    private TextArea txtDetalle;

    @FXML
    private CheckBox chkNotificar;


    @FXML
    public void initialize() {

        configurarMotivos();

        limpiar();
    }


    private void configurarMotivos() {

        cbMotivo.getItems().addAll(
                "El cliente desistió",
                "Cambio de fecha",
                "Error en la reserva",
                "Falta de pago",
                "Duplicidad de reserva",
                "Otro"
        );
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
         * AQUÍ SE CONSULTARÁ MYSQL.
         *
         * Reserva reserva =
         *     reservaService.buscar(busqueda);
         */


        mostrarInformacion(
                "Sin base de datos",
                "La búsqueda funcionará cuando conectes MySQL."
        );
    }


    @FXML
    private void cancelarReserva() {

        if ("--".equals(lblCodigo.getText())) {

            mostrarAdvertencia(
                    "Reserva requerida",
                    "Primero seleccione una reserva."
            );

            return;
        }


        if (cbMotivo.getValue() == null) {

            mostrarAdvertencia(
                    "Motivo requerido",
                    "Seleccione un motivo de cancelación."
            );

            return;
        }


        /*
         * FUTURO:
         *
         * reservaService.cancelar(
         *     idReserva,
         *     cbMotivo.getValue(),
         *     txtDetalle.getText()
         * );
         *
         * habitacionService.liberarHabitacion(...);
         */


        mostrarInformacion(
                "Cancelación",
                "La función está preparada para guardar en MySQL."
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

        lblCliente.setText("--");

        lblDocumento.setText("--");

        lblTelefono.setText("--");

        lblCorreo.setText("--");

        lblIngreso.setText("--");

        lblSalida.setText("--");

        lblNoches.setText("--");

        lblHabitacion.setText("--");

        lblHuespedes.setText("--");

        lblTotal.setText("S/ 0.00");

        txtObservaciones.clear();

        txtDetalle.clear();

        cbMotivo.setValue(null);

        chkNotificar.setSelected(false);
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
