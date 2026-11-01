package untrm.hotel_san_antonio.modelo.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Las contraseñas se guardan con bcrypt (hash con sal) y nunca en texto plano. */
class PasswordUtilTest {

    @Test
    void elHashEsBcryptDe60Caracteres() {
        String hash = PasswordUtil.hash("ClaveSegura2026");
        assertTrue(PasswordUtil.esBcrypt(hash));
        assertEquals(60, hash.length());
        assertTrue(hash.startsWith("$2a$12$"), "debe usar costo 12");
    }

    @Test
    void elHashNoContieneLaContrasena() {
        assertFalse(PasswordUtil.hash("ClaveSegura2026").contains("ClaveSegura2026"));
    }

    @Test
    void laMismaContrasenaDaHashesDistintosPorLaSal() {
        assertNotEquals(PasswordUtil.hash("ClaveSegura2026"), PasswordUtil.hash("ClaveSegura2026"));
    }

    @Test
    void laContrasenaCorrectaCoincideYLaIncorrectaNo() {
        String hash = PasswordUtil.hash("ClaveSegura2026");
        assertTrue(PasswordUtil.coincide("ClaveSegura2026", hash));
        assertFalse(PasswordUtil.coincide("claveSegura2026", hash));
        assertFalse(PasswordUtil.coincide("", hash));
    }

    @Test
    void conValoresNulosNuncaCoincide() {
        assertFalse(PasswordUtil.coincide(null, PasswordUtil.hash("ClaveSegura2026")));
        assertFalse(PasswordUtil.coincide("ClaveSegura2026", null));
    }

    @Test
    void unaContrasenaDeMasDe72BytesNoEntraAunqueCoincidaElInicio() {
        String larga = "a1".repeat(40);
        assertFalse(PasswordUtil.coincide(larga, PasswordUtil.hash(larga.substring(0, 60))));
    }

    @Test
    void lasCuentasAntiguasConSha256SiguenEntrandoYSeMarcanParaActualizar() throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("claveVieja1".getBytes()));
        assertTrue(PasswordUtil.esLegacy(sha));
        assertTrue(PasswordUtil.coincide("claveVieja1", sha));
        assertFalse(PasswordUtil.coincide("otra", sha));
        assertTrue(PasswordUtil.necesitaActualizar(sha));
        assertFalse(PasswordUtil.necesitaActualizar(PasswordUtil.hash("ClaveSegura2026")));
    }

    @Test
    void unaContrasenaNuevaDebeTener12Caracteres_LetrasYNumeros() {
        assertDoesNotThrow(() -> PasswordUtil.validarNueva("ClaveSegura2026"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.validarNueva("Corta1"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.validarNueva("SoloLetrasSinNumeros"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.validarNueva("123456789012345"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.validarNueva("a1".repeat(40)));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.validarNueva(null));
    }
}
