package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class HuespedDAO {

    public Huesped buscarPorDocumento(String tipoDocumento, String numDocumento) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return buscarPorDocumento(con, tipoDocumento, numDocumento);
        }
    }

    public Huesped buscarPorDocumento(Connection con, String tipoDocumento, String numDocumento) throws SQLException {
        String sql = "SELECT * FROM huesped WHERE tipo_documento = ? AND num_documento = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tipoDocumento);
            ps.setString(2, numDocumento);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Huesped h = new Huesped(
                            rs.getString("tipo_documento"),
                            rs.getString("num_documento"),
                            rs.getString("nombres"),
                            rs.getString("apellidos"),
                            rs.getString("pais_procedencia"),
                            rs.getString("telefono"),
                            rs.getString("email"));
                    h.setIdHuesped(rs.getInt("id_huesped"));
                    return h;
                }
            }
        }
        return null;
    }

    /** Inserta el huesped y devuelve el id generado. */
    public int insertar(Connection con, Huesped h) throws SQLException {
        String sql = "INSERT INTO huesped (tipo_documento, num_documento, nombres, apellidos, pais_procedencia, telefono, email) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, h.getTipoDocumento());
            ps.setString(2, h.getNumDocumento());
            ps.setString(3, h.getNombres());
            ps.setString(4, h.getApellidos());
            ps.setString(5, h.getPaisProcedencia());
            ps.setString(6, h.getTelefono());
            ps.setString(7, h.getEmail());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
