package untrm.hotel_san_antonio.modelo;

/** Una habitación con un huésped alojado ahora (check-in hecho), para cargarle consumos. */
public class EstadiaActiva {
    public final int idHuesped;
    public final int idHabitacion;
    public final String numero;
    public final String huesped;

    public EstadiaActiva(int idHuesped, int idHabitacion, String numero, String huesped) {
        this.idHuesped = idHuesped;
        this.idHabitacion = idHabitacion;
        this.numero = numero;
        this.huesped = huesped;
    }

    @Override
    public String toString() {
        return "Hab. " + numero + " — " + huesped;
    }
}
