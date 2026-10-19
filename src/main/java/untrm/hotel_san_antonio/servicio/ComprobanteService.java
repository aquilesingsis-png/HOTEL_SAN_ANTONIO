package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.ComprobanteDAO;
import untrm.hotel_san_antonio.dao.CuentaDAO;
import untrm.hotel_san_antonio.dao.DetalleVentaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.Comprobante;
import untrm.hotel_san_antonio.modelo.ComprobanteDocumento;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.DetalleVenta;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.modelo.VentaTienda;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.Validador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Genera el comprobante (boleta/nota de venta/factura) de una estadia que hizo check-in, o de una
 * venta de tienda que todavia no tiene comprobante. No cambia el estado de la reserva/habitacion:
 * eso lo hace el check-out por separado (son dos pasos distintos, como en cualquier hotel real).
 * Cada comprobante guarda a quien se emitio y su subtotal e IGV, para poder imprimirlo de nuevo.
 */
public class ComprobanteService {

    private static final Set<String> TIPOS = Set.of("BOLETA", "FACTURA", "NOTA_VENTA");
    private static final int INTENTOS_NUMERO = 5;
    /** El precio ya incluye IGV (18 %). */
    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final VentaTiendaDAO ventaDAO = new VentaTiendaDAO();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAO();
    private final ComprobanteDAO comprobanteDAO = new ComprobanteDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

    /**
     * A quien se emite el comprobante. Todo puede quedar vacio salvo en una factura (RUC, razon social y
     * direccion); sin datos, el comprobante sale a nombre de "Cliente varios".
     *
     * @param tipoDocumento "DNI" o "RUC" (se ignora si no hay documento)
     */
    public record DatosCliente(String tipoDocumento, String documento, String nombre, String direccion) {
        public static DatosCliente vacio() {
            return new DatosCliente(null, null, null, null);
        }
    }

    /** Lo que se hace dentro de la transaccion de una emision. */
    @FunctionalInterface
    private interface Emision {
        Comprobante emitir(Connection con) throws SQLException;
    }

    /**
     * Busca la cuenta de una estadia activa (CHECKIN) por numero de habitacion ("207"), codigo de
     * reserva ("R-0005") o documento/nombre del huesped. Si el texto es a la vez un numero de habitacion
     * con estadia activa y el numero de otra reserva, gana la habitacion.
     * @return la cuenta, o null si no hay una estadia activa que corresponda
     */
    public CuentaHabitacion cargarCuentaPorReferencia(String referencia) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        String limpio = referencia == null ? "" : referencia.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        try (Connection con = ConexionBD.conectar()) {
            Habitacion habitacion = habitacionDAO.buscarPorNumero(con, limpio);
            if (habitacion != null) {
                CuentaHabitacion cuenta = cuentaDAO.obtener(con, habitacion.getIdHabitacion());
                if (cuenta != null) {
                    return cuenta;
                }
            }
            Reserva porCodigo = reservaDAO.buscarPorCodigo(con, limpio);
            if (porCodigo != null) {
                return "CHECKIN".equals(porCodigo.getEstado())
                        ? cuentaDAO.obtener(con, porCodigo.getIdHabitacion()) : null;
            }
            List<Reserva> enCurso = reservaDAO.buscar(con, limpio, "CHECKIN", null, null);
            return enCurso.isEmpty() ? null : cuentaDAO.obtener(con, enCurso.get(0).getIdHabitacion());
        }
    }

    /** Una venta de tienda pendiente (sin comprobante todavia), junto con sus productos. */
    public static class VentaPendiente {
        public final VentaTienda venta;
        public final List<DetalleVenta> detalle;
        public VentaPendiente(VentaTienda venta, List<DetalleVenta> detalle) {
            this.venta = venta;
            this.detalle = detalle;
        }
    }

    /**
     * @return la venta y su detalle, o null si no existe esa venta o ya tiene comprobante
     * @throws IllegalStateException si la venta esta cargada a una habitacion (se factura con la estadia)
     */
    public VentaPendiente cargarVentaPorReferencia(String referencia) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        String soloDigitos = referencia == null ? "" : referencia.trim().replaceAll("\\D", "");
        if (soloDigitos.isEmpty() || soloDigitos.length() > 9) {
            return null;
        }
        int idVenta = Integer.parseInt(soloDigitos);
        try (Connection con = ConexionBD.conectar()) {
            VentaTienda venta = ventaDAO.buscarPendientePorId(con, idVenta);
            if (venta == null) {
                return null;
            }
            if (venta.getIdHabitacion() != null) {
                Habitacion cargada = habitacionDAO.buscarPorId(con, venta.getIdHabitacion());
                throw new IllegalStateException("La venta N.º " + idVenta + " está cargada a la habitación"
                        + (cargada == null ? "" : " " + cargada.getNumero())
                        + " y se factura junto con su estadía. Búscala con el origen «Cuenta de habitación».");
            }
            List<DetalleVenta> detalle = detalleDAO.listarPorVenta(con, idVenta);
            return new VentaPendiente(venta, detalle);
        }
    }

    /** Un comprobante ya emitido, por su numero (ej. "B001-000009"), listo para volver a imprimirlo; null si no existe. */
    public ComprobanteDocumento documentoPorNumero(String numero) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (numero == null || numero.isBlank()) {
            return null;
        }
        try (Connection con = ConexionBD.conectar()) {
            Comprobante c = comprobanteDAO.buscarPorNumero(con, numero.trim().toUpperCase(java.util.Locale.ROOT));
            return c == null ? null : armarDocumento(con, c);
        }
    }

    /** Un comprobante ya emitido, por su id, listo para imprimirlo; null si no existe. */
    public ComprobanteDocumento documento(int idComprobante) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            Comprobante c = comprobanteDAO.buscarPorId(con, idComprobante);
            return c == null ? null : armarDocumento(con, c);
        }
    }

    /** Las lineas salen de lo guardado: los productos enlazados al comprobante y, si es de una estadia, el alojamiento. */
    private ComprobanteDocumento armarDocumento(Connection con, Comprobante c) throws SQLException {
        List<ComprobanteDocumento.Linea> lineas = new ArrayList<>();
        if (c.isAnulado()) {
            return new ComprobanteDocumento(c, lineas); // ya no corresponde a ninguna operacion: se muestra sin detalle
        }
        BigDecimal consumos = BigDecimal.ZERO;
        for (DetalleVenta d : detalleDAO.listarPorComprobante(con, c.getIdComprobante())) {
            lineas.add(new ComprobanteDocumento.Linea(d.getNombreProducto(), d.getCantidad(),
                    d.getPrecioUnitario(), d.getSubtotal()));
            consumos = consumos.add(d.getSubtotal());
        }
        if (c.getIdReserva() != null) {
            Reserva r = reservaDAO.buscarPorId(con, c.getIdReserva(), false);
            BigDecimal alojamiento = c.getMontoTotal().subtract(consumos);
            String concepto = "Alojamiento";
            if (r != null) {
                long noches = ChronoUnit.DAYS.between(r.getFechaCheckin(), r.getFechaCheckout());
                concepto = "Alojamiento · Hab. " + r.getNumeroHabitacion() + " · "
                        + r.getFechaCheckin().format(FECHA) + " a " + r.getFechaCheckout().format(FECHA)
                        + " (" + noches + (noches == 1 ? " noche)" : " noches)");
            }
            lineas.add(0, new ComprobanteDocumento.Linea(concepto, 1, alojamiento, alojamiento));
        }
        return new ComprobanteDocumento(c, lineas);
    }

    /**
     * Genera el comprobante de una estadia y enlaza a el los consumos de tienda que quedaron
     * pendientes durante la estadia. Cada estadia tiene un solo comprobante, y la cuenta (total y saldo)
     * se vuelve a armar desde la base: lo que mostraba la pantalla pudo haber cambiado.
     * @throws IllegalStateException si todavia hay saldo pendiente o la estadia ya tiene comprobante
     */
    public Comprobante generarParaEstadia(CuentaHabitacion cuenta, String tipo, DatosCliente cliente) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        validarTipo(tipo);
        if (cuenta == null) {
            throw new IllegalArgumentException("Cargue primero la cuenta de la habitación.");
        }
        DatosCliente datos = validarCliente(tipo, cliente);
        int idUsuario = SesionActual.getUsuario().getIdUsuario();
        return emitirEnTransaccion(tipo, con -> {
            // la reserva queda bloqueada: dos personas no pueden emitir a la vez el comprobante de una estadia
            Reserva reserva = reservaDAO.buscarPorId(con, cuenta.getIdReserva(), true);
            if (reserva == null || !"CHECKIN".equals(reserva.getEstado())) {
                throw new IllegalStateException("La estadía ya no está en curso. Vuelve a cargar la operación.");
            }
            String existente = comprobanteDAO.numeroDeReserva(con, reserva.getIdReserva());
            if (existente != null) {
                throw new IllegalStateException("Esta estadía ya tiene el comprobante " + existente
                        + ". No se emite otro.");
            }
            CuentaHabitacion real = cuentaDAO.obtener(con, reserva.getIdHabitacion());
            if (real == null || real.getIdReserva() != reserva.getIdReserva()) {
                throw new IllegalStateException("La cuenta cambió. Vuelve a cargar la operación.");
            }
            if (real.getSaldo().signum() > 0) {
                throw new IllegalStateException("Aún hay un saldo pendiente de S/ "
                        + CuentaHabitacion.formato(real.getSaldo()) + ". Registra el pago antes de generar el comprobante.");
            }
            Comprobante c = nuevo(tipo, idUsuario, real.getTotalCuenta(), datos);
            c.setIdReserva(reserva.getIdReserva());
            int idComprobante = insertarConNumero(con, c);

            ventaDAO.enlazarComprobantePorEstadia(con, real.getIdHabitacion(), real.getIdHuesped(),
                    real.getIngreso(), idComprobante);
            auditoriaDAO.registrar(con, idUsuario, "COMPROBANTE_EMITIR", "comprobante", idComprobante,
                    c.getNumero() + " · estadía R-" + String.format("%04d", reserva.getIdReserva())
                    + " · S/ " + CuentaHabitacion.formato(c.getMontoTotal()));
            return c;
        });
    }

    /** Genera el comprobante de una venta de tienda que no estaba ligada a ninguna habitacion. */
    public Comprobante generarParaVenta(VentaTienda venta, String tipo, DatosCliente cliente) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        validarTipo(tipo);
        if (venta == null) {
            throw new IllegalArgumentException("Cargue primero la venta.");
        }
        DatosCliente datos = validarCliente(tipo, cliente);
        int idUsuario = SesionActual.getUsuario().getIdUsuario();
        return emitirEnTransaccion(tipo, con -> {
            // el total sale de la base y no de lo que trae la pantalla
            VentaTienda real = ventaDAO.buscarPendientePorId(con, venta.getIdVenta(), true);
            if (real == null) {
                throw new IllegalStateException("La venta no existe, ya tiene comprobante o está anulada.");
            }
            if (real.getIdHabitacion() != null) {
                throw new IllegalStateException("La venta está cargada a una habitación: se factura junto con su estadía.");
            }
            Comprobante c = nuevo(tipo, idUsuario, real.getTotal(), datos);
            int idComprobante = insertarConNumero(con, c);

            if (!ventaDAO.enlazarComprobante(con, real.getIdVenta(), idComprobante)) {
                throw new IllegalStateException("La venta ya tiene comprobante o está cargada a una habitación.");
            }
            auditoriaDAO.registrar(con, idUsuario, "COMPROBANTE_EMITIR", "comprobante", idComprobante,
                    c.getNumero() + " · venta N.º " + real.getIdVenta()
                    + " · S/ " + CuentaHabitacion.formato(c.getMontoTotal()));
            return c;
        });
    }

    /**
     * Anula un comprobante emitido (solo el administrador). No se borra y su numero no se vuelve a usar. Si era de
     * una venta de tienda, la venta queda otra vez sin comprobante y se puede emitir uno nuevo; si era de una
     * estadia, solo se puede mientras siga en curso (despues ya no se podria emitir otro) y sus consumos vuelven a
     * la cuenta de la habitacion.
     *
     * @return el comprobante ya anulado
     */
    public Comprobante anular(int idComprobante, String motivo) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 3 || razon.length() > 200) {
            throw new IllegalArgumentException("Indique el motivo de la anulación (entre 3 y 200 caracteres).");
        }
        int idUsuario = SesionActual.getUsuario().getIdUsuario();
        try (Connection con = ConexionBD.conectar()) {
            // lectura previa, fuera de la transaccion: solo sirve para saber que hay que bloquear primero
            Comprobante previo = comprobanteDAO.buscarPorId(con, idComprobante);
            if (previo == null) {
                throw new IllegalStateException("El comprobante ya no existe.");
            }
            con.setAutoCommit(false);
            try {
                // todas las operaciones bloquean en el mismo orden (estadia, ventas, comprobante) para no trabarse entre si
                Reserva reserva = null;
                if (previo.getIdReserva() != null) {
                    reserva = reservaDAO.buscarPorId(con, previo.getIdReserva(), true);
                } else {
                    ventaDAO.bloquearPorComprobante(con, idComprobante);
                }
                if (!comprobanteDAO.bloquear(con, idComprobante)) {
                    throw new IllegalStateException("El comprobante ya no existe.");
                }
                Comprobante c = comprobanteDAO.buscarPorId(con, idComprobante);
                if (c.isAnulado()) {
                    throw new IllegalStateException("El comprobante " + c.getNumero() + " ya está anulado.");
                }
                if (c.getIdReserva() != null && (reserva == null || !"CHECKIN".equals(reserva.getEstado()))) {
                    throw new IllegalStateException("El comprobante " + c.getNumero() + " es de una estadía que ya "
                            + "terminó (check-out hecho): ya no se puede anular porque no se podría emitir otro en su lugar.");
                }
                ventaDAO.desenlazarComprobante(con, idComprobante);
                if (!comprobanteDAO.anular(con, idComprobante, idUsuario, razon)) {
                    throw new IllegalStateException("El comprobante " + c.getNumero() + " ya está anulado.");
                }
                auditoriaDAO.registrar(con, idUsuario, "COMPROBANTE_ANULAR", "comprobante", idComprobante,
                        c.getNumero() + " · S/ " + CuentaHabitacion.formato(c.getMontoTotal()) + " · motivo: " + razon);
                Comprobante anulado = comprobanteDAO.buscarPorId(con, idComprobante);
                con.commit();
                return anulado;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private void validarTipo(String tipo) {
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Seleccione el tipo de comprobante.");
        }
    }

    /** Revisa los datos del cliente: en una factura son obligatorios; en lo demas, si se dan, deben ser validos. */
    private DatosCliente validarCliente(String tipoComprobante, DatosCliente cliente) {
        DatosCliente c = cliente == null ? DatosCliente.vacio() : cliente;
        String documento = limpio(c.documento());
        String nombre = limpio(c.nombre());
        String direccion = limpio(c.direccion());
        String tipoDocumento = null;
        if ("FACTURA".equals(tipoComprobante)) {
            if (documento == null || !Validador.esRucValido(documento)) {
                throw new IllegalArgumentException("Para una factura ingrese un RUC válido de 11 dígitos.");
            }
            if (nombre == null) {
                throw new IllegalArgumentException("Ingrese la razón social de la empresa.");
            }
            if (direccion == null) {
                throw new IllegalArgumentException("La dirección es obligatoria para una factura.");
            }
            tipoDocumento = "RUC";
        } else if (documento != null) {
            boolean ruc = "RUC".equals(c.tipoDocumento());
            if (!(ruc ? Validador.esRucValido(documento) : Validador.esDniValido(documento))) {
                throw new IllegalArgumentException(ruc ? "El RUC debe tener 11 dígitos válidos."
                        : "El DNI debe tener exactamente 8 dígitos.");
            }
            tipoDocumento = ruc ? "RUC" : "DNI";
        }
        if (nombre != null && nombre.length() > 150) {
            throw new IllegalArgumentException("El nombre o razón social no puede superar 150 caracteres.");
        }
        if (direccion != null && direccion.length() > 200) {
            throw new IllegalArgumentException("La dirección no puede superar 200 caracteres.");
        }
        return new DatosCliente(tipoDocumento, documento, nombre, direccion);
    }

    private String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Un comprobante nuevo, con el cliente y el desglose del total en subtotal e IGV (la nota de venta no desglosa). */
    private Comprobante nuevo(String tipo, int idUsuario, BigDecimal total, DatosCliente datos) {
        Comprobante c = new Comprobante();
        c.setIdUsuario(idUsuario);
        c.setTipo(tipo);
        c.setMontoTotal(total);
        c.setClienteTipoDoc(datos.tipoDocumento());
        c.setClienteDocumento(datos.documento());
        c.setClienteNombre(datos.nombre());
        c.setClienteDireccion(datos.direccion());
        if ("NOTA_VENTA".equals(tipo)) {
            c.setSubtotal(total);
            c.setIgv(BigDecimal.ZERO.setScale(2));
        } else {
            BigDecimal subtotal = total.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
            c.setSubtotal(subtotal);
            c.setIgv(total.subtract(subtotal));
        }
        return c;
    }

    /** Ejecuta la emision en una transaccion y suelta al final el turno de la serie. */
    private Comprobante emitirEnTransaccion(String tipo, Emision emision) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                Comprobante c = emision.emitir(con);
                con.commit();
                return c;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                comprobanteDAO.liberarSerie(con, tipo);
            }
        }
    }

    /**
     * Toma el siguiente numero de la serie. Las emisiones de un mismo tipo se hacen de una en una (aunque
     * sean desde dos PCs); si aun asi otra se adelantara con el mismo numero, se pide el siguiente.
     */
    private int insertarConNumero(Connection con, Comprobante c) throws SQLException {
        comprobanteDAO.bloquearSerie(con, c.getTipo());
        for (int intento = 1; ; intento++) {
            c.setNumero(comprobanteDAO.generarNumero(con, c.getTipo()));
            try {
                int id = comprobanteDAO.insertar(con, c);
                c.setIdComprobante(id);
                return id;
            } catch (SQLIntegrityConstraintViolationException duplicado) {
                if (intento >= INTENTOS_NUMERO) {
                    throw duplicado;
                }
            }
        }
    }
}
