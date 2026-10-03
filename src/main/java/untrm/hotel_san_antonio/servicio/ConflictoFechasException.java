package untrm.hotel_san_antonio.servicio;

/** La habitacion ya esta reservada u ocupada en las fechas pedidas (permite a la pantalla marcar las fechas). */
public class ConflictoFechasException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ConflictoFechasException(String mensaje) {
        super(mensaje);
    }
}
