package untrm.hotel_san_antonio.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * Registro de errores de la aplicacion. Ademas de la consola, deja los mensajes en archivos dentro de
 * la carpeta del usuario (HotelSanAntonio/registros), para poder saber que paso aunque la ventana ya
 * se haya cerrado. Guarda hasta 5 archivos de 1 MB y reemplaza los mas antiguos.
 */
public final class Registro {

    private static final Logger RAIZ = Logger.getLogger("untrm.hotel_san_antonio");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static boolean iniciado;
    private static long ultimoAviso;

    private Registro() {}

    /** Carpeta donde queda el registro. */
    public static Path carpeta() {
        return Paths.get(System.getProperty("user.home"), "HotelSanAntonio", "registros");
    }

    /** Prepara el archivo de registro y el aviso de errores inesperados. Se llama una sola vez al arrancar. */
    public static synchronized void iniciar() {
        if (iniciado) {
            return;
        }
        iniciado = true;
        try {
            Files.createDirectories(carpeta());
            String patron = carpeta().resolve("hotel_%g.log").toString().replace("%", "%%").replace("%%g", "%g");
            FileHandler archivo = new FileHandler(patron, 1_000_000, 5, true);
            archivo.setEncoding("UTF-8");
            archivo.setFormatter(new Formato());
            RAIZ.addHandler(archivo);
        } catch (IOException | SecurityException error) {
            RAIZ.log(Level.WARNING, "No se pudo crear el archivo de registro; solo se usa la consola.", error);
        }
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
            RAIZ.log(Level.SEVERE, "Error no controlado en el hilo " + hilo.getName(), error);
            avisar();
        });
    }

    /** Anota una operacion que fallo (por ejemplo una consulta en segundo plano), con su causa. */
    public static void fallo(String contexto, Throwable error) {
        RAIZ.log(Level.WARNING, contexto, error);
    }

    /** Un solo aviso cada 10 segundos, para que un error repetido no llene la pantalla de ventanas. */
    private static void avisar() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoAviso < 10_000) {
            return;
        }
        ultimoAviso = ahora;
        try {
            Platform.runLater(() -> Alertas.mostrarAdvertencia("Error inesperado",
                    "Ocurrió un error inesperado. Quedó anotado en el registro:\n" + carpeta()));
        } catch (IllegalStateException interfazApagada) {
            // la interfaz ya no esta: queda solo en el archivo
        }
    }

    private static final class Formato extends Formatter {
        @Override
        public String format(LogRecord registro) {
            StringBuilder texto = new StringBuilder();
            texto.append(LocalDateTime.ofInstant(registro.getInstant(), ZoneId.systemDefault()).format(FECHA))
                    .append(" [").append(registro.getLevel().getName()).append("] ")
                    .append(formatMessage(registro)).append(System.lineSeparator());
            if (registro.getThrown() != null) {
                StringWriter pila = new StringWriter();
                registro.getThrown().printStackTrace(new PrintWriter(pila));
                texto.append(pila);
            }
            return texto.toString();
        }
    }
}
