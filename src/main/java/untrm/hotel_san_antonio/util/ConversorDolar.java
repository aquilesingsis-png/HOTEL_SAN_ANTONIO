package untrm.hotel_san_antonio.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversión entre dólares y soles con el precio de Compra de la SUNAT. El hotel solo recibe dólares y da soles
 * (nunca entrega dólares), así que el precio de Venta no se usa. Con un solo precio la conversión es reversible
 * y el hotel no pierde por la diferencia entre compra y venta.
 */
public final class ConversorDolar {

    private ConversorDolar() {
    }

    /** Soles que se reconocen por esos dólares (precio de compra). */
    public static BigDecimal dolaresASoles(BigDecimal dolares, BigDecimal compra) {
        return dolares.multiply(compra).setScale(2, RoundingMode.HALF_UP);
    }

    /** Dólares que debe entregar el huésped para pagar esos soles (mismo precio de compra). */
    public static BigDecimal solesADolares(BigDecimal soles, BigDecimal compra) {
        return soles.divide(compra, 2, RoundingMode.HALF_UP);
    }

    /** Lee un importe escrito por el usuario (acepta coma o punto); null si no es un número válido. */
    public static BigDecimal leer(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            BigDecimal valor = new BigDecimal(texto.trim().replace(',', '.'));
            return valor.signum() < 0 ? null : valor;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
