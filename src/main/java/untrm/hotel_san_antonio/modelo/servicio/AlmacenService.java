package untrm.hotel_san_antonio.modelo.servicio;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import untrm.hotel_san_antonio.modelo.CategoriaResumen;
import untrm.hotel_san_antonio.modelo.Gasto;
import untrm.hotel_san_antonio.modelo.ItemInventario;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;
import untrm.hotel_san_antonio.modelo.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.modelo.dao.CategoriaDAO;
import untrm.hotel_san_antonio.modelo.dao.GastoDAO;
import untrm.hotel_san_antonio.modelo.dao.MovimientoStockDAO;
import untrm.hotel_san_antonio.modelo.dao.ProductoDAO;
import untrm.hotel_san_antonio.modelo.seguridad.Permisos;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

/** Almacén: productos, precios, existencias y categorías (solo el Administrador). */
public class AlmacenService {

    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999.99");

    /** Motivo de una entrada que es una compra: pide el costo y puede registrarse como gasto. */
    public static final String MOTIVO_COMPRA = "Compra";

    private final ProductoDAO productos = new ProductoDAO();
    private final CategoriaDAO categorias = new CategoriaDAO();
    private final MovimientoStockDAO movimientos = new MovimientoStockDAO();
    private final GastoDAO gastos = new GastoDAO();
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    private void admin() {
        Permisos.requerir("ADMINISTRADOR");
    }

    private int usuario() {
        return SesionActual.getUsuario().getIdUsuario();
    }

    // ------------------------------------------------------------------ consultas

    /** Todo el inventario; los filtros se aplican en pantalla porque son pocos productos. */
    public List<ItemInventario> listar() throws SQLException {
        admin();
        return productos.listarInventario();
    }

    public List<CategoriaResumen> categorias() throws SQLException {
        admin();
        return categorias.listarConConteo();
    }

    // ---------------------------------------------------------------- categorías

    public void guardarCategoria(Integer id, String nombre) throws SQLException {
        admin();
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 40)
            throw new IllegalArgumentException("La categoría debe tener entre 1 y 40 caracteres.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                int afectado;
                try {
                    if (id == null) {
                        afectado = categorias.insertar(con, nombre.trim());
                    } else {
                        if (!categorias.renombrar(con, id, nombre.trim())) throw new SQLException("No se guardó la categoría.");
                        afectado = id;
                    }
                } catch (java.sql.SQLIntegrityConstraintViolationException repetida) {
                    throw new IllegalArgumentException("Ya existe una categoría con ese nombre.");
                }
                auditoria.registrar(con, usuario(), id == null ? "CATEGORIA_CREAR" : "CATEGORIA_EDITAR",
                        "categoria", afectado, nombre.trim());
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    public void eliminarCategoria(int id) throws SQLException {
        admin();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (categorias.contarProductos(con, id) > 0)
                    throw new IllegalStateException("La categoría tiene productos asociados y no se puede eliminar.");
                if (!categorias.eliminar(con, id)) throw new SQLException("La categoría ya no existe.");
                auditoria.registrar(con, usuario(), "CATEGORIA_ELIMINAR", "categoria", id, "Sin productos asociados");
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    // ----------------------------------------------------------------- productos

    /**
     * Borra de verdad un producto, solo si nunca se vendió ni tuvo movimientos de stock (por ejemplo, uno creado
     * por error). Si ya tiene historial no se borra: se desactiva desde «Editar producto».
     */
    public void eliminarProducto(int id) throws SQLException {
        admin();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                String nombre = productos.nombreBloqueando(con, id);
                if (nombre == null) throw new IllegalStateException("El producto ya no existe.");
                if (productos.contarHistorial(con, id) > 0) throw new IllegalStateException(
                        "El producto tiene ventas o movimientos de stock y no se puede eliminar. "
                                + "Desactívelo desde «Editar producto».");
                try {
                    if (!productos.eliminar(con, id)) throw new SQLException("El producto ya no existe.");
                } catch (java.sql.SQLIntegrityConstraintViolationException enUso) {
                    throw new IllegalStateException("El producto ya se usa en otros registros y no se puede eliminar.");
                }
                auditoria.registrar(con, usuario(), "PRODUCTO_ELIMINAR", "producto", id, nombre);
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    /**
     * Crea (id nulo) o edita un producto. Al editar no se toca el stock: eso se hace con {@link #moverStock}.
     * Si cambia el precio queda anotado en la auditoría con el valor anterior.
     */
    public void guardarProducto(Integer id, String codigoBarra, String nombre, String marca, int idCategoria,
                                BigDecimal precio, int stockInicial, boolean activo) throws SQLException {
        admin();
        if (codigoBarra == null || !codigoBarra.trim().matches("[A-Za-z0-9-]{3,20}"))
            throw new IllegalArgumentException("El código de barras debe tener 3 a 20 caracteres (letras, números o guion).");
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120)
            throw new IllegalArgumentException("Ingrese un nombre de producto de hasta 120 caracteres.");
        if (marca != null && marca.trim().length() > 60)
            throw new IllegalArgumentException("La marca supera 60 caracteres.");
        validarPrecio(precio);
        if (stockInicial < 0) throw new IllegalArgumentException("El stock no puede ser negativo.");
        String marcaLimpia = marca == null || marca.isBlank() ? null : marca.trim();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                BigDecimal anterior = null;
                if (id != null) {
                    anterior = productos.precioBloqueando(con, id);
                    if (anterior == null) throw new IllegalStateException("El producto ya no existe.");
                }
                int afectado;
                try {
                    if (id == null) {
                        afectado = productos.insertar(con, codigoBarra.trim(), nombre.trim(), marcaLimpia, idCategoria,
                                precio, stockInicial, activo);
                    } else {
                        productos.actualizar(con, id, codigoBarra.trim(), nombre.trim(), marcaLimpia, idCategoria,
                                precio, activo);
                        afectado = id;
                    }
                } catch (java.sql.SQLIntegrityConstraintViolationException repetido) {
                    throw new IllegalArgumentException("Ya existe un producto con ese código de barras.");
                }
                auditoria.registrar(con, usuario(), id == null ? "PRODUCTO_CREAR" : "PRODUCTO_EDITAR",
                        "producto", afectado, nombre.trim());
                if (anterior != null && anterior.compareTo(precio) != 0) {
                    auditoria.registrar(con, usuario(), "PRODUCTO_PRECIO", "producto", afectado,
                            anterior + " → " + precio);
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.scale() > 2 || precio.signum() <= 0 || precio.compareTo(PRECIO_MAXIMO) > 0)
            throw new IllegalArgumentException("El precio debe estar entre S/ 0.01 y S/ 9999.99, con hasta dos decimales.");
    }

    // ------------------------------------------------------------------- existencias

    /**
     * Entrada y salida mueven unidades; ajuste fija el stock contado físicamente.
     * Queda un registro en movimiento_stock con el stock antes y después.
     * En una entrada por compra se indica el costo por unidad (queda en el movimiento) y, si se da el medio de
     * pago, la compra se anota también como gasto «Reposición de tienda» por unidades × costo, en la misma
     * operación: o se guardan las dos cosas o ninguna.
     *
     * @param costoUnitario costo por unidad de la compra (obligatorio en una compra; se ignora en lo demás)
     * @param metodoGasto   medio con el que se pagó la compra, o null para no registrar el gasto
     */
    public void moverStock(int idProducto, String tipo, int cantidad, String motivo,
                           String referencia, String observacion, BigDecimal costoUnitario,
                           String metodoGasto) throws SQLException {
        admin();
        if (!List.of("ENTRADA", "SALIDA", "AJUSTE").contains(tipo))
            throw new IllegalArgumentException("Seleccione el tipo de movimiento.");
        if (cantidad <= 0)
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        if (motivo == null || motivo.isBlank() || motivo.length() > 100)
            throw new IllegalArgumentException("Indique un motivo válido.");
        boolean compra = "ENTRADA".equals(tipo) && MOTIVO_COMPRA.equals(motivo.trim());
        BigDecimal costo = null;
        BigDecimal totalCompra = null;
        if (compra) {
            if (costoUnitario == null || costoUnitario.signum() <= 0 || costoUnitario.scale() > 2
                    || costoUnitario.compareTo(PRECIO_MAXIMO) > 0)
                throw new IllegalArgumentException("Indique el costo por unidad de la compra (S/ 0.01 a S/ 9999.99, con hasta dos decimales).");
            costo = costoUnitario;
            totalCompra = costo.multiply(BigDecimal.valueOf(cantidad));
            if (totalCompra.compareTo(new BigDecimal("99999999.99")) > 0)
                throw new IllegalArgumentException("El total de la compra es demasiado grande.");
            if (metodoGasto != null && !GastoService.METODOS.contains(metodoGasto))
                throw new IllegalArgumentException("Seleccione con qué se pagó la compra.");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                ProductoDAO.Existencia existencia = productos.existenciaActivaBloqueando(con, idProducto);
                if (existencia == null) throw new IllegalStateException("El producto no existe o está inactivo.");
                int anterior = existencia.stock();
                String nombre = existencia.nombre();
                int nuevo = "AJUSTE".equals(tipo) ? cantidad : "ENTRADA".equals(tipo)
                        ? Math.addExact(anterior, cantidad) : anterior - cantidad;
                if (nuevo < 0) throw new IllegalArgumentException("No hay stock suficiente para la salida.");
                productos.fijarStock(con, idProducto, nuevo);
                Integer idGasto = null;
                if (compra && metodoGasto != null) {
                    Gasto gasto = new Gasto();
                    gasto.setFecha(LocalDate.now());
                    String concepto = "Compra de " + cantidad + " x " + nombre;
                    gasto.setConcepto(concepto.length() > 150 ? concepto.substring(0, 150) : concepto);
                    gasto.setCategoria("Reposición de tienda");
                    gasto.setMonto(totalCompra);
                    gasto.setMetodoPago(metodoGasto);
                    gasto.setObservacion("Entrada de almacén · S/ " + costo + " por unidad");
                    idGasto = gastos.insertar(con, gasto, usuario());
                    auditoria.registrar(con, usuario(), "GASTO_CREAR", "gasto", idGasto,
                            gasto.getFecha() + " · " + gasto.getConcepto() + " · " + gasto.getCategoria() + " · S/ "
                            + totalCompra + " · " + metodoGasto + " · desde almacén");
                }
                movimientos.insertar(con, idProducto, usuario(), tipo, cantidad, anterior, nuevo, motivo.trim(),
                        referencia == null || referencia.isBlank() ? null : referencia.trim(),
                        observacion == null || observacion.isBlank() ? null : observacion.trim(), costo, idGasto);
                auditoria.registrar(con, usuario(), "PRODUCTO_STOCK", "producto", idProducto,
                        anterior + " → " + nuevo + " (" + tipo + ")"
                        + (costo == null ? "" : " · costo S/ " + costo + " por unidad")
                        + (idGasto == null ? "" : " · gasto " + idGasto));
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }
}
