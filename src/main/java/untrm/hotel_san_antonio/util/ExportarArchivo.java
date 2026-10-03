package untrm.hotel_san_antonio.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Pregunta donde guardar un archivo y lo guarda (usado por Caja y Reportes). */
public final class ExportarArchivo {

    /** Produce el contenido del archivo; se llama solo cuando el usuario ya eligio donde guardarlo. */
    public interface Generador {
        byte[] generar() throws IOException;
    }

    private ExportarArchivo() {
    }

    /**
     * @param extension   sin punto: "pdf" o "xlsx"
     * @param descripcion nombre del tipo de archivo en la ventana de guardar
     */
    public static void guardar(Window ventana, String nombreSugerido, String extension, String descripcion,
                               Generador generador) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar archivo");
        selector.setInitialFileName(nombreSugerido.replaceAll("[^A-Za-z0-9_.-]", "_") + "." + extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(descripcion, "*." + extension));
        File elegido = selector.showSaveDialog(ventana);
        if (elegido == null) {
            return;
        }
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            destino = destino.resolveSibling(destino.getFileName() + "." + extension);
        }
        try {
            Files.write(destino, generador.generar());
            Alertas.mostrarInfo("Archivo guardado", "Se guardó en:\n" + destino.toAbsolutePath());
        } catch (IOException error) {
            Alertas.mostrarError("Exportar", "No se pudo guardar el archivo.\n\n" + error.getMessage());
        }
    }
}
