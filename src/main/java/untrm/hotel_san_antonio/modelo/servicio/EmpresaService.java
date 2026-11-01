package untrm.hotel_san_antonio.modelo.servicio;

import java.sql.Connection;
import java.sql.SQLException;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;
import untrm.hotel_san_antonio.modelo.dao.EmpresaDAO;

/** Consulta de empresas ya registradas. */
public class EmpresaService {

    private final EmpresaDAO empresas = new EmpresaDAO();

    /** La empresa con ese RUC, o null si todavía no está registrada. */
    public Empresa buscarPorRuc(String ruc) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return empresas.buscarPorRuc(con, ruc);
        }
    }
}
