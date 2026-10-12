package untrm.hotel_san_antonio.controlador;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.servicio.ReniecService;
import untrm.hotel_san_antonio.servicio.ReservaService;
import untrm.hotel_san_antonio.util.SoloLectura;
import untrm.hotel_san_antonio.util.Validador;
import untrm.hotel_san_antonio.util.Registro;

/**
 * Componente FXML repetible; cada huésped se identifica antes de guardar. Se identifica con DNI (se
 * verifica en RENIEC) o, si es extranjero, con pasaporte (se escribe a mano, junto con su país).
 */
public class HuespedAdicionalController {
    private static final String DNI = "DNI";
    private static final String PASAPORTE = "Pasaporte";

    @FXML private Label lblTitulo;
    @FXML private Label lblEstado;
    @FXML private ComboBox<String> cmbTipoDocumento;
    @FXML private TextField txtDni;
    @FXML private TextField txtNombres;
    @FXML private TextField txtApellidos;
    @FXML private TextField txtPais;

    private final ReservaService reservas = new ReservaService();
    private String dniBuscado;

    @FXML private void initialize() {
        cmbTipoDocumento.getItems().setAll(DNI, PASAPORTE);
        cmbTipoDocumento.setValue(DNI);
        cmbTipoDocumento.valueProperty().addListener((obs, antes, ahora) -> {
            boolean pasaporte = PASAPORTE.equals(ahora);
            txtDni.setPromptText(pasaporte ? "Pasaporte" : "DNI (8 dígitos)");
            txtPais.setVisible(pasaporte);
            txtPais.setManaged(pasaporte);
            txtDni.clear();
            reiniciar();
        });
        txtDni.textProperty().addListener((obs, antes, ahora) -> {
            if (!ahora.equals(dniBuscado)) {
                reiniciar();
            }
        });
    }

    public void numerar(int numero) {
        lblTitulo.setText("Acompañante " + numero);
    }

    private boolean esPasaporte() {
        return PASAPORTE.equals(cmbTipoDocumento.getValue());
    }

    /** "DNI" o "PASAPORTE", como se guarda en la base. */
    private String tipoDocumento() {
        return esPasaporte() ? "PASAPORTE" : DNI;
    }

    /** Vuelve a abrir los nombres hasta que se busque de nuevo el documento. */
    private void reiniciar() {
        dniBuscado = null;
        SoloLectura.aplicar(txtNombres, false);
        SoloLectura.aplicar(txtApellidos, false);
        lblEstado.setText(esPasaporte() ? "Busque el pasaporte antes de guardar." : "Busque el DNI antes de guardar.");
    }

    @FXML private void buscar() {
        String documento = txtDni.getText().trim();
        boolean valido = esPasaporte() ? Validador.esPasaporteValido(documento) : Validador.esDniValido(documento);
        if (!valido) {
            lblEstado.setText(esPasaporte() ? "Ingrese un pasaporte válido (6 a 20 letras o números)."
                    : "Ingrese un DNI válido de 8 dígitos.");
            return;
        }
        String tipo = tipoDocumento();
        boolean pasaporte = esPasaporte();
        lblEstado.setText("Buscando...");
        Task<Huesped> local = new Task<>() {
            @Override protected Huesped call() throws Exception {
                return reservas.buscarHuespedLocal(tipo, documento);
            }
        };
        local.setOnSucceeded(e -> {
            if (!documento.equals(txtDni.getText().trim())) return;
            Huesped existente = local.getValue();
            if (existente != null) {
                cargar(existente, true);
                dniBuscado = documento;
                lblEstado.setText(pasaporte ? "Registrado localmente." : "Registrado localmente. Verificando identidad...");
            } else {
                txtNombres.clear();
                txtApellidos.clear();
                txtPais.clear();
                txtNombres.setEditable(true);
                txtApellidos.setEditable(true);
            }
            if (pasaporte) {
                // RENIEC solo conoce DNI: el extranjero se escribe a mano
                if (existente == null) {
                    dniBuscado = documento;
                    lblEstado.setText("Pasaporte nuevo: complete los nombres y el país.");
                }
                return;
            }
            consultarReniec(documento, existente != null);
        });
        local.setOnFailed(e -> {
            Registro.fallo("HuespedAdicionalController.buscar", local.getException());
            lblEstado.setText("No se pudo consultar la base de datos.");
        });
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
                cargar(local == null ? encontrado : local, true);
                dniBuscado = dni;
                lblEstado.setText("Identidad verificada.");
            });
            actualizar.setOnFailed(ev -> {
                Registro.fallo("HuespedAdicionalController.consultarReniec", actualizar.getException());
                lblEstado.setText("No se pudo actualizar la identidad local.");
            });
            ejecutar(actualizar, "actualizar-identidad-acompanante");
        });
        consulta.setOnFailed(e -> {
            Registro.fallo("HuespedAdicionalController.consultarReniec", consulta.getException());
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
        if (esPasaporte()) {
            txtPais.setText(h.getPaisProcedencia() == null ? "" : h.getPaisProcedencia());
        }
        SoloLectura.aplicar(txtNombres, local);
        SoloLectura.aplicar(txtApellidos, local);
    }

    public Huesped obtener() {
        String documento = txtDni.getText().trim();
        if (!documento.equals(dniBuscado)) {
            throw new IllegalArgumentException("Busque el " + (esPasaporte() ? "pasaporte" : "DNI")
                    + " de cada acompañante antes de guardar.");
        }
        String pais = esPasaporte() ? txtPais.getText().trim() : "Perú";
        if (esPasaporte() && !Validador.esPaisValido(pais)) {
            throw new IllegalArgumentException("Indique el país de cada acompañante con pasaporte (solo letras).");
        }
        return new Huesped(tipoDocumento(), documento, txtNombres.getText().trim(), txtApellidos.getText().trim(),
                pais, null, null);
    }

    private void ejecutar(Task<?> tarea, String nombre) {
        Thread hilo = new Thread(tarea, nombre);
        hilo.setDaemon(true);
        hilo.start();
    }
}
