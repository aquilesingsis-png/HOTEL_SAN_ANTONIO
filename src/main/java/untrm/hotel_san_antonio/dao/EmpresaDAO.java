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

    /**
     * Devuelve el id de la empresa con ese RUC; si no existe, la registra. Si ya existe, la razon social y la
     * direccion que se escribieron ahora la actualizan (lo que venga en blanco no se toca).
     */
    public int guardar(Connection con, Empresa e) throws SQLException {
        String buscar = "SELECT id_empresa FROM empresa WHERE ruc = ?";
        try (PreparedStatement ps = con.prepareStatement(buscar)) {
            ps.setString(1, e.getRuc());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    actualizarDatos(con, id, e);
                    return id;
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

    private void actualizarDatos(Connection con, int idEmpresa, Empresa e) throws SQLException {
        String razon = e.getRazonSocial() == null || e.getRazonSocial().isBlank() ? null : e.getRazonSocial().trim();
        String direccion = e.getDireccion() == null || e.getDireccion().isBlank() ? null : e.getDireccion().trim();
        if (razon == null && direccion == null) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE empresa SET razon_social = COALESCE(?, razon_social), "
                + "direccion_fiscal = COALESCE(?, direccion_fiscal) WHERE id_empresa = ?")) {
            ps.setString(1, razon);
            ps.setString(2, direccion);
            ps.setInt(3, idEmpresa);
            ps.executeUpdate();
        }
    }
}
