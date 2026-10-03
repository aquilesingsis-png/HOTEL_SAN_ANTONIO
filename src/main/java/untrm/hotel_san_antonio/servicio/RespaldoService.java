package untrm.hotel_san_antonio.servicio;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

/**
 * Respaldo de la base de datos en un archivo .sql que se puede restaurar desde phpMyAdmin o con mysql.
 * No usa programas externos: lee las tablas y escribe el archivo. Las filas se leen una por una y se
 * escriben enseguida, así que el uso de memoria no crece con la cantidad de registros.
 */
public class RespaldoService {

    /** Resultado de un respaldo terminado. */
    public record Resultado(Path archivo, int tablas, long filas, long bytes) { }

    /** Fecha y usuario del último respaldo registrado. */
    public record UltimoRespaldo(LocalDateTime fecha, String usuario) { }

    private static final int FILAS_POR_INSERT = 200;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    /** Nombre sugerido para el archivo, con la fecha y la hora. */
    public static String nombreSugerido() {
        return "hotel_san_antonio_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".sql";
    }

    /**
     * Escribe el respaldo en {@code destino}. Se hace dentro de una sola transacción de lectura, así que el
     * archivo queda coherente aunque alguien siga cobrando o registrando mientras se genera.
     *
     * @param progreso recibe un texto como «Tabla pago (5 de 22)» para mostrarlo en pantalla (puede ser null)
     */
    public Resultado crear(Path destino, Consumer<String> progreso) throws SQLException, IOException {
        Permisos.requerir("ADMINISTRADOR");
        int tablas = 0;
        long filas = 0;
        try (Connection con = ConexionBD.conectar();
             BufferedWriter salida = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
                st.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
            }
            List<String> nombres = listarTablas(con);
            salida.write("-- Respaldo de la base de datos Hotel San Antonio\n");
            salida.write("-- Fecha: " + LocalDateTime.now().format(FORMATO) + "\n");
            salida.write("-- Para restaurar: en phpMyAdmin elija la base hotel_san_antonio y use Importar con este archivo.\n");
            salida.write("-- Atención: al importar se reemplazan las tablas actuales por las de este respaldo.\n\n");
            salida.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\nSET UNIQUE_CHECKS = 0;\n\n");
            for (String tabla : nombres) {
                tablas++;
                if (progreso != null) progreso.accept("Tabla " + tabla + " (" + tablas + " de " + nombres.size() + ")");
                filas += volcarTabla(con, tabla, salida);
            }
            salida.write("SET UNIQUE_CHECKS = 1;\nSET FOREIGN_KEY_CHECKS = 1;\n");
            con.rollback(); // solo se leyó: se cierra la transacción de lectura
        }
        long bytes = Files.size(destino);
        try (Connection con = ConexionBD.conectar()) {
            auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(), "RESPALDO_CREAR", "sistema", 0,
                    destino.getFileName() + " · " + tablas + " tablas · " + filas + " filas");
        }
        return new Resultado(destino, tablas, filas, bytes);
    }

    /** El último respaldo hecho desde el sistema, o null si nunca se hizo. */
    public UltimoRespaldo ultimo() throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        String sql = "SELECT a.fecha, u.usuario FROM auditoria a JOIN usuario u ON u.id_usuario = a.id_usuario "
                + "WHERE a.accion = 'RESPALDO_CREAR' ORDER BY a.fecha DESC, a.id_auditoria DESC LIMIT 1";
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? new UltimoRespaldo(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2)) : null;
        }
    }

    private List<String> listarTablas(Connection con) throws SQLException {
        List<String> nombres = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
            while (rs.next()) nombres.add(rs.getString(1));
        }
        return nombres;
    }

    /** Escribe la definición de la tabla y todas sus filas; devuelve cuántas filas escribió. */
    private long volcarTabla(Connection con, String tabla, BufferedWriter salida) throws SQLException, IOException {
        String nombre = "`" + tabla.replace("`", "``") + "`";
        salida.write("-- ----------------------------------------------------------------\n");
        salida.write("-- Tabla " + tabla + "\n");
        salida.write("-- ----------------------------------------------------------------\n");
        salida.write("DROP TABLE IF EXISTS " + nombre + ";\n");
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + nombre)) {
            rs.next();
            salida.write(rs.getString(2) + ";\n\n");
        }
        long total = 0;
        try (Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE); // el servidor envía fila por fila
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + nombre)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnas = meta.getColumnCount();
                int enLote = 0;
                while (rs.next()) {
                    salida.write(enLote == 0 ? "INSERT INTO " + nombre + " VALUES\n(" : ",\n(");
                    for (int i = 1; i <= columnas; i++) {
                        if (i > 1) salida.write(",");
                        salida.write(valor(rs, i, meta.getColumnType(i)));
                    }
                    salida.write(")");
                    total++;
                    if (++enLote == FILAS_POR_INSERT) {
                        salida.write(";\n");
                        enLote = 0;
                    }
                }
                if (enLote > 0) salida.write(";\n");
            }
        }
        salida.write("\n");
        return total;
    }

    private String valor(ResultSet rs, int columna, int tipo) throws SQLException {
        switch (tipo) {
            case Types.BIT, Types.BOOLEAN: {
                boolean v = rs.getBoolean(columna);
                return rs.wasNull() ? "NULL" : (v ? "1" : "0");
            }
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.DECIMAL, Types.NUMERIC, Types.FLOAT, Types.REAL, Types.DOUBLE: {
                java.math.BigDecimal v = rs.getBigDecimal(columna);
                return v == null ? "NULL" : v.toPlainString();
            }
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB: {
                byte[] bytes = rs.getBytes(columna);
                return bytes == null ? "NULL" : "0x" + java.util.HexFormat.of().formatHex(bytes);
            }
            default: {
                String v = rs.getString(columna);
                return v == null ? "NULL" : "'" + escapar(v) + "'";
            }
        }
    }

    private String escapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 8);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\0' -> sb.append("\\0");
                case '\u001a' -> sb.append("\\Z");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
