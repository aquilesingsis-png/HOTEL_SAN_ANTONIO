package untrm.hotel_san_antonio.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import javafx.stage.FileChooser;

/**
 * Carpeta con la que abren las ventanas de guardar (respaldos y exportes): la ultima donde se guardo algo o,
 * si no hay, Documentos. Nunca la carpeta del proyecto, para que un respaldo o un Excel con datos de
 * huespedes no termine junto al codigo y se suba a GitHub por error.
 */
public final class CarpetaGuardado {

    private static final String CLAVE = "ultima_carpeta_guardado";

    private CarpetaGuardado() {
    }

    /** Deja la ventana de guardar abierta en una carpeta adecuada. */
    public static void preparar(FileChooser selector) {
        File carpeta = inicial();
        if (carpeta != null) {
            selector.setInitialDirectory(carpeta);
        }
    }

    /** Anota la carpeta del archivo guardado para la proxima vez. */
    public static void recordar(File archivo) {
        File carpeta = archivo == null ? null : archivo.getAbsoluteFile().getParentFile();
        if (carpeta == null) {
            return;
        }
        try {
            Preferences.userNodeForPackage(CarpetaGuardado.class).put(CLAVE, carpeta.getPath());
        } catch (RuntimeException sinPreferencias) {
            // no es importante: la proxima vez abrira en Documentos
        }
    }

    private static File inicial() {
        try {
            String guardada = Preferences.userNodeForPackage(CarpetaGuardado.class).get(CLAVE, "");
            if (!guardada.isEmpty()) {
                File recordada = new File(guardada);
                if (recordada.isDirectory() && !dentroDelProyecto(recordada)) {
                    return recordada;
                }
            }
        } catch (RuntimeException sinPreferencias) {
            // se usa Documentos
        }
        File inicio = new File(System.getProperty("user.home", ""));
        File documentos = new File(inicio, "Documents");
        if (documentos.isDirectory()) {
            return documentos;
        }
        return inicio.isDirectory() ? inicio : null;
    }

    private static boolean dentroDelProyecto(File carpeta) {
        Path trabajo = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return carpeta.toPath().toAbsolutePath().normalize().startsWith(trabajo);
    }
}
