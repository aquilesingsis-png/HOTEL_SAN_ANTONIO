package untrm.hotel_san_antonio.modelo;

public class Habitacion {
    private int idHabitacion;
    private String numero;
    private int idTipo;
    private TipoHabitacion tipo; // opcional: cargado por el DAO con JOIN
    private int piso;
    private String estado; // DISPONIBLE, OCUPADA, LIMPIEZA, MANTENIMIENTO
    private String huespedActual; // solo si esta ocupada con una reserva en CHECKIN (lo carga el DAO)
    private String reservaHoy;    // huesped con reserva CONFIRMADA que llega hoy a esta habitacion libre (lo carga el DAO)
    private String motivoMantenimiento; // por que esta en MANTENIMIENTO, o null si no aplica

    public Habitacion() {}

    public Habitacion(int idHabitacion, String numero, int idTipo, int piso, String estado) {
        this.idHabitacion = idHabitacion;
        this.numero = numero;
        this.idTipo = idTipo;
        this.piso = piso;
        this.estado = estado;
    }

    public int getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(int idHabitacion) { this.idHabitacion = idHabitacion; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public int getIdTipo() { return idTipo; }
    public void setIdTipo(int idTipo) { this.idTipo = idTipo; }

    public TipoHabitacion getTipo() { return tipo; }
    public void setTipo(TipoHabitacion tipo) { this.tipo = tipo; }

    public int getPiso() { return piso; }
    public void setPiso(int piso) { this.piso = piso; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getHuespedActual() { return huespedActual; }
    public void setHuespedActual(String huespedActual) { this.huespedActual = huespedActual; }

    public String getReservaHoy() { return reservaHoy; }
    public void setReservaHoy(String reservaHoy) { this.reservaHoy = reservaHoy; }

    public String getMotivoMantenimiento() { return motivoMantenimiento; }
    public void setMotivoMantenimiento(String motivoMantenimiento) { this.motivoMantenimiento = motivoMantenimiento; }
}
