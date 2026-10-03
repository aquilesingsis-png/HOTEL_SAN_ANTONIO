package untrm.hotel_san_antonio.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuración JDBC: valores locales incluidos para XAMPP, archivo externo
 * editable y, con prioridad mayor, propiedades JVM o variables de entorno.
 */
public class ConexionBD {
    public static Connection conectar() throws SQLException {
        Properties archivo = new Properties();
        try (InputStream in = ConexionBD.class.getResourceAsStream(
                "/untrm/hotel_san_antonio/db-defaults.properties")) {
            if (in != null) archivo.load(in);
        } catch (IOException error) {
            throw new SQLException("No se pudo leer la configuración JDBC incluida.", error);
        }
        try (FileInputStream in = new FileInputStream("config.properties")) {
            archivo.load(in);
        } catch (IOException ignored) {
            // El archivo es opcional: los valores incluidos o el entorno bastan.
        }
        String url = valor("HOTEL_DB_URL", "db.url", archivo);
        String usuario = valor("HOTEL_DB_USER", "db.user", archivo);
        String password = valor("HOTEL_DB_PASSWORD", "db.password", archivo);
        if (url == null || url.isBlank() || usuario == null || usuario.isBlank()) {
            throw new SQLException("Configure db.url y db.user en config.properties o mediante HOTEL_DB_URL y HOTEL_DB_USER.");
        }
        if (password == null) password = "";
        return DriverManager.getConnection(url, usuario, password);
    }

    private static String valor(String variable, String propiedad, Properties archivo) {
        String sistema = System.getProperty(propiedad);
        if (sistema != null) return sistema;
        String entorno = System.getenv(variable);
        return entorno != null ? entorno : archivo.getProperty(propiedad);
    }
}
