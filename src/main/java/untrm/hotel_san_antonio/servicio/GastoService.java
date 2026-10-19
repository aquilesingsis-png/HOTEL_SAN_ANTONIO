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
    /** Categorias de gasto: lista cerrada, para que los totales por categoria no se dispersen en variantes. */
    public static final List<String> CATEGORIAS = List.of("Servicios", "Insumos de limpieza", "Mantenimiento",
            "Sueldos", "Reposición de tienda", "Otros");
    /** Medios de pago: los mismos que usan los cobros; solo EFECTIVO descuenta de la caja. */
    public static final List<String> METODOS = List.of("EFECTIVO", "YAPE", "TRANSFERENCIA", "TARJETA");

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
                String detalle;
                if (nuevo) {
                    id = gastos.insertar(con, gasto, usuario);
                    detalle = describir(gasto) + (esTardio(gasto) ? " · registro tardío: " + gasto.getMotivoRegistroTardio() : "");
                } else {
                    id = gasto.getIdGasto();
                    Gasto antes = gastos.obtener(con, id);
                    if (antes == null || !gastos.actualizar(con, gasto)) {
                        throw new IllegalStateException("El gasto ya no está activo.");
                    }
                    detalle = "antes: " + describir(antes) + " → después: " + describir(gasto);
                }
                auditoria.registrar(con, usuario, nuevo ? "GASTO_CREAR" : "GASTO_EDITAR", "gasto", id, detalle);
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
                Gasto antes = gastos.obtener(con, idGasto);
                if (antes == null || !gastos.desactivar(con, idGasto)) {
                    throw new IllegalStateException("El gasto ya no está activo.");
                }
                auditoria.registrar(con, SesionActual.getUsuario().getIdUsuario(),
                        "GASTO_DESACTIVAR", "gasto", idGasto, describir(antes));
                con.commit();
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            }
        }
    }

    /** Una linea con lo que importa de un gasto, para dejarla en la auditoria. */
    private String describir(Gasto g) {
        return g.getFecha() + " · " + g.getConcepto() + " · " + g.getCategoria() + " · S/ "
                + g.getMonto().setScale(2, java.math.RoundingMode.HALF_UP) + " · " + g.getMetodoPago();
    }

    private boolean esTardio(Gasto g) {
        return g.getMotivoRegistroTardio() != null && !g.getMotivoRegistroTardio().isBlank();
    }

    private void validar(Gasto g) {
        if (g == null || g.getFecha() == null || g.getFecha().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Seleccione una fecha válida, no futura.");
        if (g.getConcepto() == null || g.getConcepto().isBlank() || g.getConcepto().length() > 150)
            throw new IllegalArgumentException("Ingrese un concepto de hasta 150 caracteres.");
        if (g.getCategoria() == null || !CATEGORIAS.contains(g.getCategoria()))
            throw new IllegalArgumentException("Seleccione la categoría del gasto.");
        if (g.getMonto() == null || g.getMonto().compareTo(BigDecimal.ZERO) <= 0 || g.getMonto().scale() > 2
                || g.getMonto().compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Ingrese un monto positivo con dos decimales como máximo.");
        if (g.getMetodoPago() == null || !METODOS.contains(g.getMetodoPago()))
            throw new IllegalArgumentException("Seleccione el método de pago.");
        if (g.getObservacion() != null && g.getObservacion().length() > 500)
            throw new IllegalArgumentException("La observación supera 500 caracteres.");
        if (g.getFecha().isBefore(LocalDate.now())
                && (g.getMotivoRegistroTardio() == null || g.getMotivoRegistroTardio().isBlank()))
            throw new IllegalArgumentException("Explique por qué el gasto se registra después de la fecha del hecho.");
        if (g.getMotivoRegistroTardio() != null && g.getMotivoRegistroTardio().length() > 300)
            throw new IllegalArgumentException("El motivo de registro tardío supera 300 caracteres.");
    }
}
