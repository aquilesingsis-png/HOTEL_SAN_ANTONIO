package untrm.hotel_san_antonio.util;

import java.util.regex.Pattern;

/**
 * Validaciones de formato reutilizadas tanto en los Controller (feedback
 * inmediato en la UI) como en los Service antes de guardar en la BD.
 */
public class Validador {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern RUC = Pattern.compile("^\\d{11}$");
    // acepta dominios con varios puntos (ej. correo@hotmail.com.pe)
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)*\\.[a-zA-Z]{2,}$");
    // nombres y apellidos: solo letras (con tildes y ñ), espacios, punto, apostrofe y guion; hasta 80 (columna VARCHAR(80))
    private static final Pattern NOMBRE = Pattern.compile("^\\p{L}[\\p{L} .'-]{0,79}$");
    private static final Pattern PAIS = Pattern.compile("^\\p{L}[\\p{L} .'-]{0,59}$");
    private static final Pattern MONTO = Pattern.compile("^\\d+(\\.\\d{1,2})?$");
    private static final Pattern TELEFONO = Pattern.compile("^9\\d{8}$");

    private static final Pattern PASAPORTE = Pattern.compile("^[A-Za-z0-9]{6,20}$");

    public static boolean esPasaporteValido(String pasaporte) {
        // un pasaporte hecho solo de ceros no es real
        return pasaporte != null && PASAPORTE.matcher(pasaporte).matches() && !pasaporte.matches("0+");
    }

    public static boolean esDniValido(String dni) {
        // 00000000 cumple el formato pero no es un DNI real (algunas APIs devuelven un registro de prueba)
        return dni != null && DNI.matcher(dni).matches() && !dni.equals("00000000");
    }

    public static boolean esRucValido(String ruc) {
        return ruc != null && RUC.matcher(ruc).matches() && tienePrefijoDeRuc(ruc) && digitoVerificadorCorrecto(ruc);
    }

    /** Los RUC empiezan con 10 (persona natural), 15, 16, 17 o 20 (empresa). */
    private static boolean tienePrefijoDeRuc(String ruc) {
        String prefijo = ruc.substring(0, 2);
        return prefijo.equals("10") || prefijo.equals("15") || prefijo.equals("16")
                || prefijo.equals("17") || prefijo.equals("20");
    }

    /** El ultimo digito del RUC se calcula con los 10 primeros (modulo 11, pesos 5,4,3,2,7,6,5,4,3,2). */
    private static boolean digitoVerificadorCorrecto(String ruc) {
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += Character.getNumericValue(ruc.charAt(i)) * pesos[i];
        }
        int digito = 11 - (suma % 11);
        if (digito == 10) {
            digito = 0;
        } else if (digito == 11) {
            digito = 1;
        }
        return digito == Character.getNumericValue(ruc.charAt(10));
    }

    public static boolean esEmailValido(String email) {
        return email != null && email.length() <= 100 && EMAIL.matcher(email).matches();
    }

    /** Nombres o apellidos de una persona: sin numeros ni simbolos raros, maximo 80 caracteres. */
    public static boolean esNombreValido(String nombre) {
        return nombre != null && NOMBRE.matcher(nombre.trim()).matches();
    }

    public static boolean esPaisValido(String pais) {
        return pais != null && PAIS.matcher(pais.trim()).matches();
    }

    public static boolean esMontoValido(String monto) {
        return monto != null && MONTO.matcher(monto).matches();
    }

    public static boolean esTelefonoValido(String telefono) {
        return telefono != null && TELEFONO.matcher(telefono).matches();
    }

    public static boolean esTextoObligatorioValido(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }
}
