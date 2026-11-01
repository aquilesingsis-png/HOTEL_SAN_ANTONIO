package untrm.hotel_san_antonio.modelo.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;

/** Lecturas que necesita el respaldo de la base de datos (acceso a datos). */
public class RespaldoDAO {

    /** Fecha y usuario del último respaldo anotado en la auditoría. */
    public record Registro(LocalDateTime fecha, String usuario) { }

    /** Lo que se hace con cada fila de una tabla mientras se lee. */
    public interface Fila {
        void procesar(ResultSet filas, ResultSetMetaData columnas) throws SQLException, IOException;
    }

    /** El último respaldo hecho desde el sistema, o null si nunca se hizo. */
    public Registro ultimoRespaldo() throws SQLException {
        String sql = "SELECT a.fecha, u.usuario FROM auditoria a JOIN usuario u ON u.id_usuario = a.id_usuario "
                + "WHERE a.accion = 'RESPALDO_CREAR' ORDER BY a.fecha DESC, a.id_auditoria DESC LIMIT 1";
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? new Registro(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2)) : null;
        }
    }

    /** Abre una lectura coherente: todo lo que se lea después es una foto de un mismo instante. */
    public void iniciarLecturaCoherente(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
            st.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
        }
    }

    public List<String> listarTablas(Connection con) throws SQLException {
        List<String> nombres = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
            while (rs.next()) nombres.add(rs.getString(1));
        }
        return nombres;
    }

    /** El CREATE TABLE de la tabla; nombreCitado ya viene entre comillas invertidas. */
    public String definicionTabla(Connection con, String nombreCitado) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + nombreCitado)) {
            rs.next();
            return rs.getString(2);
        }
    }

    /** Lee las filas de la tabla una por una (sin cargarlas todas en memoria) y entrega cada una a quien las escribe. */
    public void recorrerFilas(Connection con, String nombreCitado, Fila fila) throws SQLException, IOException {
        try (Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE); // el servidor envía fila por fila
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + nombreCitado)) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    fila.procesar(rs, meta);
                }
            }
        }
    }
}
