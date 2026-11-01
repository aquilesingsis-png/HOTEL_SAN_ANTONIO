package untrm.hotel_san_antonio.modelo.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/** Movimientos de stock del almacén (acceso a datos). */
public class MovimientoStockDAO {

    public void insertar(Connection con, int idProducto, int idUsuario, String tipo, int cantidad, int stockAnterior,
                         int stockResultante, String motivo, String referencia, String observacion,
                         BigDecimal costoUnitario, Integer idGasto) throws SQLException {
        String sql = "INSERT INTO movimiento_stock(id_producto,id_usuario,tipo,cantidad,stock_anterior,"
                + "stock_resultante,motivo,referencia,observacion,costo_unitario,id_gasto) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idUsuario);
            ps.setString(3, tipo);
            ps.setInt(4, cantidad);
            ps.setInt(5, stockAnterior);
            ps.setInt(6, stockResultante);
            ps.setString(7, motivo);
            ps.setString(8, referencia);
            ps.setString(9, observacion);
            ps.setBigDecimal(10, costoUnitario);
            if (idGasto == null) {
                ps.setNull(11, Types.INTEGER);
            } else {
                ps.setInt(11, idGasto);
            }
            ps.executeUpdate();
        }
    }
}
