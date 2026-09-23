package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.Empresa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class EmpresaDAO {

    /** Busca una empresa ya registrada por su RUC, o null si no existe todavia. */
    public Empresa buscarPorRuc(Connection con, String ruc) throws SQLException {
        String sql = "SELECT id_empresa, ruc, razon_social, direccion_fiscal FROM empresa WHERE ruc = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ruc);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Empresa e = new Empresa();
                    e.setIdEmpresa(rs.getInt("id_empresa"));
                    e.setRuc(rs.getString("ruc"));
                    e.setRazonSocial(rs.getString("razon_social"));
                    e.setDireccion(rs.getString("direccion_fiscal"));
                    return e;
                }
            }
        }
        return null;
    }

    /** Devuelve el id de la empresa con ese RUC; si no existe, la registra. */
    public int guardar(Connection con, Empresa e) throws SQLException {
        String buscar = "SELECT id_empresa FROM empresa WHERE ruc = ?";
        try (PreparedStatement ps = con.prepareStatement(buscar)) {
            ps.setString(1, e.getRuc());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        String insertar = "INSERT INTO empresa (ruc, razon_social, direccion_fiscal) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(insertar, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getRuc());
            ps.setString(2, e.getRazonSocial());
            ps.setString(3, e.getDireccion());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
