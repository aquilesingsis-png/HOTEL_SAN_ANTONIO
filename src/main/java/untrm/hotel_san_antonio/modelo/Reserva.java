package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Reserva {
    private int idReserva;
    private int idHuesped;
    private int idHabitacion;
    private int idUsuario;
    private LocalDateTime fechaReserva;
    private LocalDate fechaCheckin;
    private LocalDate fechaCheckout;
    private BigDecimal adelanto;
    private BigDecimal montoTotal;
    private String estado; // PENDIENTE, CONFIRMADA, CHECKIN, FINALIZADA, CANCELADA
    private String canal;  // TELEFONO, WHATSAPP, BOOKING, PRESENCIAL
    private Integer idEmpresa; // null si no se factura a una empresa
    private int numHuespedes;
    private LocalTime horaCheckin; // hora en que se espera la llegada (opcional)

    // campos de conveniencia para mostrar en tablas, cargados por el DAO con JOIN
    private String nombreHuesped;
    private String numeroHabitacion;
    private String tipoDocumentoHuesped;
    private String numDocumentoHuesped;
    private String telefonoHuesped;
    private String emailHuesped;
    private String nombreTipoHabitacion;
    private int pisoHabitacion;
    private String motivoCancelacion;
    private String detalleCancelacion;

    public Reserva() {}

    /** Codigo corto que se muestra al usuario (ej. "R-0005"). */
    public String getCodigo() {
        return String.format("R-%04d", idReserva);
    }

    public int getIdReserva() { return idReserva; }
    public void setIdReserva(int idReserva) { this.idReserva = idReserva; }

    public int getIdHuesped() { return idHuesped; }
    public void setIdHuesped(int idHuesped) { this.idHuesped = idHuesped; }

    public int getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(int idHabitacion) { this.idHabitacion = idHabitacion; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDateTime fechaReserva) { this.fechaReserva = fechaReserva; }

    public LocalDate getFechaCheckin() { return fechaCheckin; }
    public void setFechaCheckin(LocalDate fechaCheckin) { this.fechaCheckin = fechaCheckin; }

    public LocalDate getFechaCheckout() { return fechaCheckout; }
    public void setFechaCheckout(LocalDate fechaCheckout) { this.fechaCheckout = fechaCheckout; }

    public BigDecimal getAdelanto() { return adelanto; }
    public void setAdelanto(BigDecimal adelanto) { this.adelanto = adelanto; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public Integer getIdEmpresa() { return idEmpresa; }
    public void setIdEmpresa(Integer idEmpresa) { this.idEmpresa = idEmpresa; }

    public int getNumHuespedes() { return numHuespedes; }
    public void setNumHuespedes(int numHuespedes) { this.numHuespedes = numHuespedes; }

    public LocalTime getHoraCheckin() { return horaCheckin; }
    public void setHoraCheckin(LocalTime horaCheckin) { this.horaCheckin = horaCheckin; }

    public String getNombreHuesped() { return nombreHuesped; }
    public void setNombreHuesped(String nombreHuesped) { this.nombreHuesped = nombreHuesped; }

    public String getNumeroHabitacion() { return numeroHabitacion; }
    public void setNumeroHabitacion(String numeroHabitacion) { this.numeroHabitacion = numeroHabitacion; }

    public String getTipoDocumentoHuesped() { return tipoDocumentoHuesped; }
    public void setTipoDocumentoHuesped(String tipoDocumentoHuesped) { this.tipoDocumentoHuesped = tipoDocumentoHuesped; }

    public String getNumDocumentoHuesped() { return numDocumentoHuesped; }
    public void setNumDocumentoHuesped(String numDocumentoHuesped) { this.numDocumentoHuesped = numDocumentoHuesped; }

    public String getTelefonoHuesped() { return telefonoHuesped; }
    public void setTelefonoHuesped(String telefonoHuesped) { this.telefonoHuesped = telefonoHuesped; }

    public String getEmailHuesped() { return emailHuesped; }
    public void setEmailHuesped(String emailHuesped) { this.emailHuesped = emailHuesped; }

    public String getNombreTipoHabitacion() { return nombreTipoHabitacion; }
    public void setNombreTipoHabitacion(String nombreTipoHabitacion) { this.nombreTipoHabitacion = nombreTipoHabitacion; }

    public int getPisoHabitacion() { return pisoHabitacion; }
    public void setPisoHabitacion(int pisoHabitacion) { this.pisoHabitacion = pisoHabitacion; }

    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }

    public String getDetalleCancelacion() { return detalleCancelacion; }
    public void setDetalleCancelacion(String detalleCancelacion) { this.detalleCancelacion = detalleCancelacion; }
}
