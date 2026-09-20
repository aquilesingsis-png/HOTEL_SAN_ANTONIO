package untrm.hotel_san_antonio.servicio;

/** La habitacion ya esta reservada u ocupada en las fechas pedidas (permite a la pantalla marcar las fechas). */
public class ConflictoFechasException extends IllegalStateException {

    public ConflictoFechasException(String mensaje) {
        super(mensaje);
    }
}
