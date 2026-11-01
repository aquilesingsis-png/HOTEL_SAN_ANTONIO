package untrm.hotel_san_antonio.modelo.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Conversión de dólares y soles con el precio de compra del día. */
class ConversorDolarTest {

    @Test
    void dolaresASolesRedondeaADosDecimales() {
        assertEquals(new BigDecimal("34.58"), ConversorDolar.dolaresASoles(new BigDecimal("10"), new BigDecimal("3.458")));
    }

    @Test
    void solesADolaresRedondeaADosDecimales() {
        assertEquals(new BigDecimal("2.89"), ConversorDolar.solesADolares(new BigDecimal("10"), new BigDecimal("3.458")));
    }

    @Test
    void leeImportesConComaOPunto() {
        assertEquals(new BigDecimal("12.50"), ConversorDolar.leer("12,50"));
        assertEquals(new BigDecimal("12.50"), ConversorDolar.leer(" 12.50 "));
    }

    @Test
    void unTextoQueNoEsImporteDevuelveNull() {
        assertNull(ConversorDolar.leer("abc"));
        assertNull(ConversorDolar.leer("-5"));
        assertNull(ConversorDolar.leer(""));
        assertNull(ConversorDolar.leer(null));
    }
}
