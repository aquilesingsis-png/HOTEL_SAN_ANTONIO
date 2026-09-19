package untrm.hotel_san_antonio.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cifrado de contrasenas con SHA-256, para que coincida con el hash que ya
 * se genero en el script SQL de datos de prueba usando SHA2(valor, 256).
 */
public class PasswordUtil {

    public static String hash(String contrasenaPlana) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(contrasenaPlana.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("No se pudo generar el hash de la contrasena", e);
        }
    }

    public static boolean coincide(String contrasenaPlana, String hashGuardado) {
        return hash(contrasenaPlana).equalsIgnoreCase(hashGuardado);
    }
}
