package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lo que el sistema calcula para el turno que se va a cerrar. */
public record ResumenCierre(BigDecimal fondoInicial, BigDecimal cobros, BigDecimal otros, BigDecimal salidas,
                            LocalDateTime desde, LocalDateTime hasta, boolean hayCierrePrevio) {
    public BigDecimal esperado() {
        return fondoInicial.add(cobros).add(otros).subtract(salidas);
    }
}
