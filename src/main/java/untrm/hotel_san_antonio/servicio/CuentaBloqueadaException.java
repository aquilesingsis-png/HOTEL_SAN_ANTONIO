package untrm.hotel_san_antonio.servicio;

/** La cuenta está bloqueada por demasiados intentos de ingreso fallidos. */
public class CuentaBloqueadaException extends Exception {
    private final int minutos;

    public CuentaBloqueadaException(int minutos) {
        super("Cuenta bloqueada por demasiados intentos. Intente de nuevo en " + minutos + " minuto(s).");
        this.minutos = minutos;
    }

    /** Minutos que faltan para que se levante el bloqueo. */
    public int getMinutos() {
        return minutos;
    }
}
