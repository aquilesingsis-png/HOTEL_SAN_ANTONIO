package untrm.hotel_san_antonio.modelo.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Validación de documentos y datos de contacto (reglas del negocio y de la SUNAT). */
class ValidadorTest {

    @Test
    void dniDe8DigitosEsValido() {
        assertTrue(Validador.esDniValido("60217536"));
    }

    @Test
    void dniInvalido() {
        assertFalse(Validador.esDniValido("1234567"));
        assertFalse(Validador.esDniValido("123456789"));
        assertFalse(Validador.esDniValido("1234567a"));
        assertFalse(Validador.esDniValido("00000000"));
        assertFalse(Validador.esDniValido(""));
        assertFalse(Validador.esDniValido(null));
    }

    @Test
    void rucConDigitoVerificadorCorrectoEsValido() {
        assertTrue(Validador.esRucValido("20100070970"));
    }

    @Test
    void rucInvalido() {
        assertFalse(Validador.esRucValido("20100070971"), "digito verificador incorrecto");
        assertFalse(Validador.esRucValido("30100070970"), "prefijo que no existe");
        assertFalse(Validador.esRucValido("2010007097"), "10 digitos");
        assertFalse(Validador.esRucValido("2010007097a"));
        assertFalse(Validador.esRucValido(null));
    }

    @Test
    void pasaporteSoloConLetrasYNumerosYNoDeCeros() {
        assertTrue(Validador.esPasaporteValido("AB123456"));
        assertFalse(Validador.esPasaporteValido("00000000"));
        assertFalse(Validador.esPasaporteValido("ab 123"));
        assertFalse(Validador.esPasaporteValido(null));
    }

    @Test
    void correoYTelefono() {
        assertTrue(Validador.esEmailValido("persona@correo.com"));
        assertFalse(Validador.esEmailValido("persona@"));
        assertFalse(Validador.esEmailValido("sin-arroba.com"));
        assertTrue(Validador.esTelefonoValido("987654321"));
        assertFalse(Validador.esTelefonoValido("12345"));
    }
}
