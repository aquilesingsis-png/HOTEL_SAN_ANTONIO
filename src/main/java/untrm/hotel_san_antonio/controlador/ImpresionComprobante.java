package untrm.hotel_san_antonio.controlador;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import untrm.hotel_san_antonio.modelo.ComprobanteDocumento;
import untrm.hotel_san_antonio.util.Alertas;
import untrm.hotel_san_antonio.util.CarpetaGuardado;
import untrm.hotel_san_antonio.util.PdfTexto;

/** Guarda en PDF un comprobante ya emitido (lo usan la pantalla Comprobantes y el Carrito de la tienda). */
public final class ImpresionComprobante {

    private ImpresionComprobante() {
    }

    /** Pregunta donde guardarlo y escribe el PDF; no hace nada si se cancela la ventana. */
    public static void guardarPdf(Window ventana, ComprobanteDocumento documento) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar comprobante en PDF");
        selector.setInitialFileName("comprobante_"
                + documento.getComprobante().getNumero().replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        CarpetaGuardado.preparar(selector);
        File elegido = selector.showSaveDialog(ventana);
        if (elegido == null) {
            return;
        }
        CarpetaGuardado.recordar(elegido);
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            destino = destino.resolveSibling(destino.getFileName() + ".pdf");
        }
        try {
            Files.write(destino, PdfTexto.crear(documento.aTexto()));
            Alertas.mostrarInfo("PDF guardado", "Comprobante guardado en:\n" + destino.toAbsolutePath());
        } catch (IOException error) {
            Alertas.mostrarError("Guardar PDF", "No se pudo guardar el comprobante.\n" + error.getMessage());
        }
    }
}
