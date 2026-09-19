package untrm.hotel_san_antonio.util;

import java.util.regex.Pattern;

/**
 * Validaciones de formato reutilizadas tanto en los Controller (feedback
 * inmediato en la UI) como en los Service antes de guardar en la BD.
 */
public class Validador {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern RUC = Pattern.compile("^\\d{11}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern MONTO = Pattern.compile("^\\d+(\\.\\d{1,2})?$");
    private static final Pattern TELEFONO = Pattern.compile("^\\d{6,9}$");

    public static boolean esDniValido(String dni) {
        return dni != null && DNI.matcher(dni).matches();
    }

    public static boolean esRucValido(String ruc) {
        return ruc != null && RUC.matcher(ruc).matches();
    }

    public static boolean esEmailValido(String email) {
        return email != null && EMAIL.matcher(email).matches();
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
