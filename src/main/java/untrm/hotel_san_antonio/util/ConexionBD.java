package untrm.hotel_san_antonio.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuración JDBC: valores locales incluidos para XAMPP, archivo externo
 * editable y, con prioridad mayor, propiedades JVM o variables de entorno.
 * La configuración se lee una vez y solo se vuelve a leer si config.properties cambia.
 * Si la base no responde, la conexión falla a los pocos segundos en vez de dejar la pantalla colgada.
 */
public class ConexionBD {
    private static final Logger LOG = Logger.getLogger(ConexionBD.class.getName());
    private static final String TIEMPO_CONEXION_MS = "5000";
    private static final String TIEMPO_RESPUESTA_MS = "60000";

    private static Properties configuracion;
    private static long marcaArchivo = Long.MIN_VALUE;
    private static boolean avisoSinArchivo;

    public static Connection conectar() throws SQLException {
        Properties archivo = configuracion();
        String url = valor("HOTEL_DB_URL", "db.url", archivo);
        String usuario = valor("HOTEL_DB_USER", "db.user", archivo);
        String password = valor("HOTEL_DB_PASSWORD", "db.password", archivo);
        if (url == null || url.isBlank() || usuario == null || usuario.isBlank()) {
            throw new SQLException("Configure db.url y db.user en config.properties o mediante HOTEL_DB_URL y HOTEL_DB_USER.");
        }
        if (password == null) password = "";
        Properties conexion = new Properties();
        conexion.setProperty("user", usuario);
        conexion.setProperty("password", password);
        if (!url.contains("connectTimeout")) conexion.setProperty("connectTimeout", TIEMPO_CONEXION_MS);
        if (!url.contains("socketTimeout")) conexion.setProperty("socketTimeout", TIEMPO_RESPUESTA_MS);
        return DriverManager.getConnection(url, conexion);
    }

    /** Valores incluidos + config.properties (de la carpeta desde donde se abre el programa). */
    private static synchronized Properties configuracion() throws SQLException {
        File externo = new File("config.properties");
        long marca = externo.isFile() ? externo.lastModified() : -1;
        if (configuracion != null && marca == marcaArchivo) {
            return configuracion;
        }
        Properties nueva = new Properties();
        try (InputStream in = ConexionBD.class.getResourceAsStream(
                "/untrm/hotel_san_antonio/db-defaults.properties")) {
            if (in != null) nueva.load(in);
        } catch (IOException error) {
            throw new SQLException("No se pudo leer la configuración JDBC incluida.", error);
        }
        if (marca >= 0) {
            try (FileInputStream in = new FileInputStream(externo)) {
                nueva.load(in);
            } catch (IOException error) {
                throw new SQLException("No se pudo leer " + externo.getAbsolutePath() + ".", error);
            }
        } else if (!avisoSinArchivo) {
            avisoSinArchivo = true;
            LOG.log(Level.WARNING, "No se encontró config.properties en {0}: se usan los valores incluidos "
                    + "(usuario root sin clave). Copie config.properties.example como config.properties.",
                    externo.getAbsolutePath());
        }
        configuracion = nueva;
        marcaArchivo = marca;
        return nueva;
    }

    private static String valor(String variable, String propiedad, Properties archivo) {
        String sistema = System.getProperty(propiedad);
        if (sistema != null) return sistema;
        String entorno = System.getenv(variable);
        return entorno != null ? entorno : archivo.getProperty(propiedad);
    }
}
