package untrm.hotel_san_antonio.dao;

import untrm.hotel_san_antonio.modelo.VentaTienda;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class VentaTiendaDAO {

    public int insertar(Connection cn, VentaTienda venta) throws SQLException {
        String sql = "INSERT INTO venta_tienda " +
                "(id_huesped, id_habitacion, id_usuario, id_comprobante, cliente_externo, total) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setNullableInt(ps, 1, venta.getIdHuesped());
            setNullableInt(ps, 2, venta.getIdHabitacion());
            ps.setInt(3, venta.getIdUsuario());
            setNullableInt(ps, 4, venta.getIdComprobante());
            if (venta.getClienteExterno() == null || venta.getClienteExterno().trim().isEmpty()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, venta.getClienteExterno().trim());
            }
            ps.setBigDecimal(6, venta.getTotal());

            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo registrar la venta de tiendita.");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se obtuvo el identificador de la venta.");
    }

    private void setNullableInt(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }
}
