package untrm.hotel_san_antonio.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import untrm.hotel_san_antonio.modelo.Gasto;

public class GastoDAO {
    public List<Gasto> listar(Connection con, LocalDate desde, LocalDate hasta, String texto) throws SQLException {
        String sql = "SELECT id_gasto, fecha, concepto, categoria, monto, metodo_pago, observacion, "
                + "motivo_registro_tardio FROM gasto WHERE activo = TRUE "
                + "AND (? IS NULL OR fecha >= ?) AND (? IS NULL OR fecha <= ?) "
                + "AND (? IS NULL OR concepto LIKE ?) ORDER BY fecha DESC, id_gasto DESC";
        List<Gasto> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, desde == null ? null : Date.valueOf(desde));
            ps.setDate(2, desde == null ? null : Date.valueOf(desde));
            ps.setDate(3, hasta == null ? null : Date.valueOf(hasta));
            ps.setDate(4, hasta == null ? null : Date.valueOf(hasta));
            ps.setString(5, texto);
            ps.setString(6, texto == null ? null : "%" + texto + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Gasto g = new Gasto();
                    g.setIdGasto(rs.getInt("id_gasto"));
                    g.setFecha(rs.getDate("fecha").toLocalDate());
                    g.setConcepto(rs.getString("concepto"));
                    g.setCategoria(rs.getString("categoria"));
                    g.setMonto(rs.getBigDecimal("monto"));
                    g.setMetodoPago(rs.getString("metodo_pago"));
                    g.setObservacion(rs.getString("observacion"));
                    g.setMotivoRegistroTardio(rs.getString("motivo_registro_tardio"));
                    lista.add(g);
                }
            }
        }
        return lista;
    }

    public int insertar(Connection con, Gasto g, int usuario) throws SQLException {
        String sql = "INSERT INTO gasto (fecha, concepto, categoria, monto, metodo_pago, observacion, "
                + "motivo_registro_tardio, id_usuario) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, g);
            ps.setInt(8, usuario);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se obtuvo el ID del gasto.");
                return rs.getInt(1);
            }
        }
    }

    public boolean actualizar(Connection con, Gasto g) throws SQLException {
        String sql = "UPDATE gasto SET fecha = ?, concepto = ?, categoria = ?, monto = ?, "
                + "metodo_pago = ?, observacion = ?, motivo_registro_tardio = ? "
                + "WHERE id_gasto = ? AND activo = TRUE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            asignar(ps, g);
            ps.setInt(8, g.getIdGasto());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean desactivar(Connection con, int idGasto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE gasto SET activo = FALSE WHERE id_gasto = ? AND activo = TRUE")) {
            ps.setInt(1, idGasto);
            return ps.executeUpdate() == 1;
        }
    }

    private void asignar(PreparedStatement ps, Gasto g) throws SQLException {
        ps.setDate(1, Date.valueOf(g.getFecha()));
        ps.setString(2, g.getConcepto());
        ps.setString(3, g.getCategoria());
        ps.setBigDecimal(4, g.getMonto());
        ps.setString(5, g.getMetodoPago());
        ps.setString(6, g.getObservacion());
        ps.setString(7, g.getMotivoRegistroTardio());
    }
}
