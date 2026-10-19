package untrm.hotel_san_antonio.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Contraseñas con bcrypt (costo 12): el hash guardado mide 60 caracteres, empieza con $2a$12$ y lleva dentro su
 * propia sal. Las cuentas de versiones anteriores (PBKDF2 o SHA-256 simple) se siguen aceptando y se convierten
 * a bcrypt la primera vez que ingresan.
 */
public final class PasswordUtil {
    /** Cuanto trabajo cuesta cada hash: cada +1 duplica el tiempo (con 12 son unos 250 ms). */
    private static final int COSTO = 12;
    /** bcrypt solo lee los primeros 72 bytes de la contraseña. */
    private static final int MAXIMO_BYTES = 72;

    private PasswordUtil() {}

    public static String hash(String contrasena) {
        return BCrypt.withDefaults().hashToString(COSTO, contrasena.toCharArray());
    }

    /** true si el hash guardado ya es bcrypt. */
    public static boolean esBcrypt(String guardado) {
        return guardado != null && guardado.matches("\\$2[abxy]\\$\\d\\d\\$.{53}");
    }

    /** true si es un SHA-256 simple de 64 caracteres hexadecimales (el de la primera versión). */
    public static boolean esLegacy(String guardado) {
        return guardado != null && guardado.matches("(?i)[0-9a-f]{64}");
    }

    /** true si el hash guardado no es bcrypt y conviene convertirlo al ingresar. */
    public static boolean necesitaActualizar(String guardado) {
        return guardado != null && !esBcrypt(guardado);
    }

    public static void validarNueva(String contrasena) {
        if (contrasena == null || contrasena.length() < 12
                || contrasena.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES
                || !contrasena.matches(".*[A-Za-z].*") || !contrasena.matches(".*[0-9].*")) {
            throw new IllegalArgumentException(
                    "Use una contraseña de 12 a 72 caracteres con letras y números.");
        }
    }

    public static boolean coincide(String contrasena, String guardado) {
        if (contrasena == null || guardado == null) return false;
        if (esBcrypt(guardado)) {
            if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) return false;
            return BCrypt.verifyer().verify(contrasena.toCharArray(), guardado).verified;
        }
        if (esLegacy(guardado)) {
            try {
                byte[] actual = MessageDigest.getInstance("SHA-256")
                        .digest(contrasena.getBytes(StandardCharsets.UTF_8));
                return MessageDigest.isEqual(actual, java.util.HexFormat.of().parseHex(guardado));
            } catch (Exception error) {
                return false;
            }
        }
        return coincidePbkdf2(contrasena, guardado);
    }

    /** El formato de la segunda versión: pbkdf2$iteraciones$sal$hash. Solo para cuentas que aún no se convirtieron. */
    private static boolean coincidePbkdf2(String contrasena, String guardado) {
        try {
            String[] partes = guardado.split("\\$");
            if (partes.length != 4 || !"pbkdf2".equals(partes[0])) return false;
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < 100_000 || iteraciones > 1_000_000) return false;
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (sal.length != 16 || esperado.length != 32) return false;
            return MessageDigest.isEqual(esperado, derivarPbkdf2(contrasena, sal, iteraciones));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static byte[] derivarPbkdf2(String contrasena, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo procesar la contraseña.", error);
        } finally {
            spec.clearPassword();
        }
    }
}
