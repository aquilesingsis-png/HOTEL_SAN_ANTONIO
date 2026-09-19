package untrm.hotel_san_antonio.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexion JDBC a MySQL. Cada laptop del equipo debe tener la base de datos
 * "hotel_san_antonio" creada localmente (ver carpeta /sql del repo) con
 * usuario root sin contrasena, tal como viene por defecto en XAMPP.
 */
public class ConexionBD {

    private static final String URL = "jdbc:mysql://localhost:3306/hotel_san_antonio?useSSL=false&serverTimezone=America/Lima";
    private static final String USUARIO = "root";
    private static final String CONTRASENA = "";

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CONTRASENA);
    }
}
