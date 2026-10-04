package untrm.hotel_san_antonio.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversión entre dólares y soles con el tipo de cambio de la SUNAT, desde el punto de vista del hotel:
 * los dólares que recibe se cambian al precio de Compra, y para dar dólares se usa el precio de Venta.
 */
public final class ConversorDolar {

    private ConversorDolar() {
    }

    /** Soles que se obtienen por esos dólares (precio de compra). */
    public static BigDecimal dolaresASoles(BigDecimal dolares, BigDecimal compra) {
        return dolares.multiply(compra).setScale(2, RoundingMode.HALF_UP);
    }

    /** Dólares que se obtienen por esos soles (precio de venta). */
    public static BigDecimal solesADolares(BigDecimal soles, BigDecimal venta) {
        return soles.divide(venta, 2, RoundingMode.HALF_UP);
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
