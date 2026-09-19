package untrm.hotel_san_antonio.modelo;

import java.time.LocalDateTime;

public class Huesped {
    private int idHuesped;
    private String tipoDocumento; // DNI, PASAPORTE
    private String numDocumento;
    private String nombres;
    private String apellidos;
    private String paisProcedencia;
    private String telefono;
    private String email;
    private LocalDateTime fechaRegistro;

    public Huesped() {}

    public Huesped(String tipoDocumento, String numDocumento, String nombres, String apellidos,
                    String paisProcedencia, String telefono, String email) {
        this.tipoDocumento = tipoDocumento;
        this.numDocumento = numDocumento;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.paisProcedencia = paisProcedencia;
        this.telefono = telefono;
        this.email = email;
    }

    public int getIdHuesped() { return idHuesped; }
    public void setIdHuesped(int idHuesped) { this.idHuesped = idHuesped; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumDocumento() { return numDocumento; }
    public void setNumDocumento(String numDocumento) { this.numDocumento = numDocumento; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getPaisProcedencia() { return paisProcedencia; }
    public void setPaisProcedencia(String paisProcedencia) { this.paisProcedencia = paisProcedencia; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public String getNombreCompleto() { return nombres + " " + apellidos; }
}
