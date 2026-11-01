package untrm.hotel_san_antonio.modelo.servicio;

import java.sql.SQLException;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.dao.HuespedDAO;

/** Consulta de huéspedes ya registrados. */
public class HuespedService {

    private final HuespedDAO huespedes = new HuespedDAO();

    /** El huésped con ese documento, o null si todavía no está registrado. */
    public Huesped buscarPorDocumento(String tipoDocumento, String numeroDocumento) throws SQLException {
        return huespedes.buscarPorDocumento(tipoDocumento, numeroDocumento);
    }
}
