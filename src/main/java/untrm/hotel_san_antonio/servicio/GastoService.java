package untrm.hotel_san_antonio.servicio;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.GastoDAO;
import untrm.hotel_san_antonio.modelo.Gasto;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

public class GastoService {
    private final GastoDAO gastos = new GastoDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    public List<Gasto> listar(LocalDate desde, LocalDate hasta, String texto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango de fechas es inválido.");
        }
        try (Connection con = ConexionBD.conectar()) {
            return gastos.listar(con, desde, hasta, texto == null || texto.isBlank() ? null : texto.trim());
        }
    }

    public int guardar(Gasto gasto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        validar(gasto);
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                int usuario = SesionActual.getUsuario().getIdUsuario();
                boolean nuevo = gasto.getIdGasto() == 0;
                int id;
                if (nuevo) id = gastos.insertar(con, gasto, usuario);
                else {
                    id = gasto.getIdGasto();
                    if (!gastos.actualizar(con, gasto)) throw new IllegalStateException("El gasto ya no está activo.");
                }
                auditoria.registrar(con, usuario, nuevo ? "GASTO_CREAR" : "GASTO_EDITAR", "gasto", id,
                        gasto.getMotivoRegistroTardio());
                con.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    public void desactivar(int idGasto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (!gastos.desactivar(con, idGasto)) throw new IllegalStateException("El gasto ya no está activo.");
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        "GASTO_DESACTIVAR", "gasto", idGasto, null);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    private void validar(Gasto g) {
        if (g == null || g.getFecha() == null || g.getFecha().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Seleccione una fecha válida, no futura.");
        if (g.getConcepto() == null || g.getConcepto().isBlank() || g.getConcepto().length() > 150)
            throw new IllegalArgumentException("Ingrese un concepto de hasta 150 caracteres.");
        if (g.getCategoria() == null || g.getCategoria().isBlank() || g.getCategoria().length() > 60)
            throw new IllegalArgumentException("Ingrese una categoría de hasta 60 caracteres.");
        if (g.getMonto() == null || g.getMonto().compareTo(BigDecimal.ZERO) <= 0 || g.getMonto().scale() > 2
                || g.getMonto().compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Ingrese un monto positivo con dos decimales como máximo.");
        if (g.getMetodoPago() == null || g.getMetodoPago().isBlank() || g.getMetodoPago().length() > 30)
            throw new IllegalArgumentException("Ingrese un método de pago.");
        if (g.getObservacion() != null && g.getObservacion().length() > 500)
            throw new IllegalArgumentException("La observación supera 500 caracteres.");
        if (g.getFecha().isBefore(LocalDate.now())
                && (g.getMotivoRegistroTardio() == null || g.getMotivoRegistroTardio().isBlank()))
            throw new IllegalArgumentException("Explique por qué el gasto se registra después de la fecha del hecho.");
        if (g.getMotivoRegistroTardio() != null && g.getMotivoRegistroTardio().length() > 300)
            throw new IllegalArgumentException("El motivo de registro tardío supera 300 caracteres.");
    }
}
