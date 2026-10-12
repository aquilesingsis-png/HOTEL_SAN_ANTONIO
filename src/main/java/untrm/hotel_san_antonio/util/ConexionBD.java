package untrm.hotel_san_antonio.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
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
 * Las conexiones salen de un pequeño grupo (HikariCP): cerrar una conexión la devuelve al grupo, no la corta.
 * Si la base no responde, la conexión falla a los 5 segundos en vez de dejar la pantalla colgada.
 */
public class ConexionBD {
    private static final Logger LOG = Logger.getLogger(ConexionBD.class.getName());
    private static final int TIEMPO_CONEXION_MS = 5000;
    private static final String TIEMPO_RESPUESTA_MS = "60000";
    private static final int CONEXIONES_MAXIMAS = 10;

    private static Properties configuracion;
    private static long marcaArchivo = Long.MIN_VALUE;
    private static boolean avisoSinArchivo;
    private static HikariDataSource grupo;
    private static String claveDelGrupo;

    public static Connection conectar() throws SQLException {
        Properties archivo = configuracion();
        String url = valor("HOTEL_DB_URL", "db.url", archivo);
        String usuario = valor("HOTEL_DB_USER", "db.user", archivo);
        String password = valor("HOTEL_DB_PASSWORD", "db.password", archivo);
        if (url == null || url.isBlank() || usuario == null || usuario.isBlank()) {
            throw new SQLException("Configure db.url y db.user en config.properties o mediante HOTEL_DB_URL y HOTEL_DB_USER.");
        }
        if (password == null) password = "";
        return grupo(url, usuario, password).getConnection();
    }

    /** El grupo de conexiones; si cambia la configuración (url, usuario o clave), se arma uno nuevo. */
    private static synchronized HikariDataSource grupo(String url, String usuario, String password) {
        String clave = url + "\n" + usuario + "\n" + password;
        if (grupo == null || grupo.isClosed() || !clave.equals(claveDelGrupo)) {
            if (grupo != null && !grupo.isClosed()) {
                grupo.close();
            }
            HikariConfig config = new HikariConfig();
            config.setPoolName("hotel-san-antonio");
            config.setJdbcUrl(url);
            config.setUsername(usuario);
            config.setPassword(password);
            config.setMaximumPoolSize(CONEXIONES_MAXIMAS);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(TIEMPO_CONEXION_MS);
            config.setInitializationFailTimeout(-1); // el aviso de que MySQL no responde sale al pedir la conexión
            if (!url.contains("connectTimeout")) config.addDataSourceProperty("connectTimeout", String.valueOf(TIEMPO_CONEXION_MS));
            if (!url.contains("socketTimeout")) config.addDataSourceProperty("socketTimeout", TIEMPO_RESPUESTA_MS);
            grupo = new HikariDataSource(config);
            claveDelGrupo = clave;
        }
        return grupo;
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
