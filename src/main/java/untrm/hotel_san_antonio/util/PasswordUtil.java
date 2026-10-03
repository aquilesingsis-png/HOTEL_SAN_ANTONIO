package untrm.hotel_san_antonio.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 para contraseñas nuevas; admite SHA-256 heredado solo para migración al ingresar. */
public final class PasswordUtil {
    private static final int ITERACIONES = 210_000;
    private static final SecureRandom AZAR = new SecureRandom();

    private PasswordUtil() {}

    public static String hash(String contrasena) {
        byte[] sal = new byte[16];
        AZAR.nextBytes(sal);
        byte[] derivado = derivar(contrasena, sal, ITERACIONES);
        return "pbkdf2$" + ITERACIONES + "$" + Base64.getEncoder().encodeToString(sal)
                + "$" + Base64.getEncoder().encodeToString(derivado);
    }

    public static boolean esLegacy(String guardado) {
        return guardado != null && guardado.matches("(?i)[0-9a-f]{64}");
    }

    public static void validarNueva(String contrasena) {
        if (contrasena == null || contrasena.length() < 12 || contrasena.length() > 128
                || !contrasena.matches(".*[A-Za-z].*") || !contrasena.matches(".*[0-9].*")) {
            throw new IllegalArgumentException(
                    "Use una contraseña de 12 a 128 caracteres con letras y números.");
        }
    }

    public static boolean coincide(String contrasena, String guardado) {
        if (contrasena == null || guardado == null) return false;
        if (esLegacy(guardado)) {
            try {
                byte[] actual = MessageDigest.getInstance("SHA-256")
                        .digest(contrasena.getBytes(StandardCharsets.UTF_8));
                return MessageDigest.isEqual(actual, java.util.HexFormat.of().parseHex(guardado));
            } catch (Exception error) {
                return false;
            }
        }
        try {
            String[] partes = guardado.split("\\$");
            if (partes.length != 4 || !"pbkdf2".equals(partes[0])) return false;
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < 100_000 || iteraciones > 1_000_000) return false;
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (sal.length != 16 || esperado.length != 32) return false;
            return MessageDigest.isEqual(esperado, derivar(contrasena, sal, iteraciones));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static byte[] derivar(String contrasena, byte[] sal, int iteraciones) {
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
