package untrm.hotel_san_antonio.controlador.Reserva;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.servicio.ReniecService;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.Validador;

/** Componente FXML repetible; cada huésped se identifica antes de guardar. */
public class HuespedAdicionalController {
    @FXML private Label lblTitulo;
    @FXML private Label lblEstado;
    @FXML private TextField txtDni;
    @FXML private TextField txtNombres;
    @FXML private TextField txtApellidos;

    private final ReservaService reservas = new ReservaService();
    private String dniBuscado;

    @FXML private void initialize() {
        txtDni.textProperty().addListener((obs, antes, ahora) -> {
            if (!ahora.equals(dniBuscado)) {
                dniBuscado = null;
                txtNombres.setEditable(true);
                txtApellidos.setEditable(true);
                lblEstado.setText("Busque el DNI antes de guardar.");
            }
        });
    }

    public void numerar(int numero) {
        lblTitulo.setText("Acompañante " + numero);
    }

    @FXML private void buscar() {
        String dni = txtDni.getText().trim();
        if (!Validador.esDniValido(dni)) {
            lblEstado.setText("Ingrese un DNI válido de 8 dígitos.");
            return;
        }
        lblEstado.setText("Buscando...");
        Task<Huesped> local = new Task<>() {
            @Override protected Huesped call() throws Exception {
                return reservas.buscarHuespedLocal("DNI", dni);
            }
        };
        local.setOnSucceeded(e -> {
            if (!dni.equals(txtDni.getText().trim())) return;
            Huesped existente = local.getValue();
            if (existente != null) {
                cargar(existente, true);
                dniBuscado = dni;
                lblEstado.setText("Registrado localmente. Verificando identidad...");
            } else {
                txtNombres.clear();
                txtApellidos.clear();
                txtNombres.setEditable(true);
                txtApellidos.setEditable(true);
            }
            consultarReniec(dni, existente != null);
        });
        local.setOnFailed(e -> lblEstado.setText("No se pudo consultar la base de datos."));
        ejecutar(local, "buscar-acompanante");
    }

    private void consultarReniec(String dni, boolean existeLocal) {
        Task<Huesped> consulta = ReniecService.consultarDni(dni);
        consulta.setOnSucceeded(e -> {
            if (!dni.equals(txtDni.getText().trim())) return;
            Huesped encontrado = consulta.getValue();
            if (encontrado == null) {
                dniBuscado = dni;
                lblEstado.setText(existeLocal ? "Datos locales cargados." : "Complete los nombres manualmente.");
                return;
            }
            Task<Huesped> actualizar = new Task<>() {
                @Override protected Huesped call() throws Exception {
                    return reservas.reconciliarIdentidadDni(dni, encontrado);
                }
            };
            actualizar.setOnSucceeded(ev -> {
                if (!dni.equals(txtDni.getText().trim())) return;
                Huesped local = actualizar.getValue();
                cargar(local == null ? encontrado : local, local != null);
                dniBuscado = dni;
                lblEstado.setText("Identidad verificada.");
            });
            actualizar.setOnFailed(ev -> lblEstado.setText("No se pudo actualizar la identidad local."));
            ejecutar(actualizar, "actualizar-identidad-acompanante");
        });
        consulta.setOnFailed(e -> {
            if (!dni.equals(txtDni.getText().trim())) return;
            dniBuscado = dni;
            lblEstado.setText(existeLocal ? "Datos locales cargados; RENIEC no disponible."
                    : "RENIEC no disponible. Complete los nombres manualmente.");
        });
        ejecutar(consulta, "reniec-acompanante");
    }

    private void cargar(Huesped h, boolean local) {
        txtNombres.setText(h.getNombres());
        txtApellidos.setText(h.getApellidos());
        txtNombres.setEditable(!local);
        txtApellidos.setEditable(!local);
    }

    public Huesped obtener() {
        String dni = txtDni.getText().trim();
        if (!dni.equals(dniBuscado)) {
            throw new IllegalArgumentException("Busque el DNI de cada acompañante antes de guardar.");
        }
        return new Huesped("DNI", dni, txtNombres.getText().trim(), txtApellidos.getText().trim(),
                "Perú", null, null);
    }

    private void ejecutar(Task<?> tarea, String nombre) {
        Thread hilo = new Thread(tarea, nombre);
        hilo.setDaemon(true);
        hilo.start();
    }
}
