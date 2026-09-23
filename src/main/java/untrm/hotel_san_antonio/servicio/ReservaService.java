package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.EmpresaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.HuespedDAO;
import untrm.hotel_san_antonio.dao.PagoDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.Validador;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Registra una reserva completa (huesped, empresa opcional, reserva, adelanto y,
 * si corresponde, el check-in) en una sola transaccion: o se guarda todo o nada.
 */
public class ReservaService {

    private static final int USUARIO_POR_DEFECTO = 2; // recepcion1: solo mientras no exista el Login

    private final HuespedDAO huespedDAO = new HuespedDAO();
    private final EmpresaDAO empresaDAO = new EmpresaDAO();
    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();

    /**
     * Check-in de una reserva hecha antes: la reserva pasa a CHECKIN y la habitacion a OCUPADA.
     * @throws IllegalStateException si la habitacion no tiene una reserva confirmada para hoy
     */
    public void hacerCheckIn(int idHabitacion) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (!"DISPONIBLE".equals(habitacionDAO.obtenerEstado(con, idHabitacion))) {
                    throw new IllegalStateException("La habitación no está disponible para recibir al huésped todavía.");
                }
                int idReserva = reservaDAO.buscarParaCheckin(con, idHabitacion, LocalDate.now());
                if (idReserva < 0) {
                    throw new IllegalStateException("La habitación no tiene una reserva confirmada para hoy.");
                }
                reservaDAO.actualizarEstado(con, idReserva, "CHECKIN");
                habitacionDAO.cambiarEstado(con, idHabitacion, "OCUPADA");
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Fecha de ingreso de la proxima reserva vigente de la habitacion (dentro de un año), o null si no hay. */
    public LocalDate proximaReserva(int idHabitacion) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            LocalDate hoy = LocalDate.now();
            return reservaDAO.primerCruce(con, idHabitacion, hoy, hoy.plusYears(1));
        }
    }

    /** Estadia maxima permitida en una sola reserva (incluye las ampliaciones). */
    public static final int MAX_NOCHES = 60;
    /** Cuanto tiempo por adelantado se puede reservar. */
    private static final int MAX_DIAS_ANTICIPACION = 365;

    /**
     * Revisa los datos antes de tocar la base, para que ninguna pantalla pueda saltarse las reglas.
     * @throws IllegalArgumentException con el motivo si algo no es valido
     */
    private void validarDatos(Huesped huesped, Reserva reserva, java.util.List<Pago> pagos, boolean checkinInmediato) {
        String tipoDoc = huesped.getTipoDocumento();
        String documento = huesped.getNumDocumento();
        boolean documentoValido = "DNI".equals(tipoDoc) ? Validador.esDniValido(documento)
                : "PASAPORTE".equals(tipoDoc) && Validador.esPasaporteValido(documento);
        if (!documentoValido) {
            throw new IllegalArgumentException("El documento de identidad no es válido.");
        }
        if (!Validador.esNombreValido(huesped.getNombres()) || !Validador.esNombreValido(huesped.getApellidos())) {
            throw new IllegalArgumentException("Los nombres y apellidos solo pueden tener letras (máximo 80 caracteres).");
        }
        if (!Validador.esPaisValido(huesped.getPaisProcedencia())) {
            throw new IllegalArgumentException("El país de procedencia no es válido.");
        }
        if (huesped.getTelefono() != null && !Validador.esTelefonoValido(huesped.getTelefono())) {
            throw new IllegalArgumentException("El teléfono debe tener 9 dígitos y empezar con 9.");
        }
        if (huesped.getEmail() != null && !Validador.esEmailValido(huesped.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico no es válido.");
        }

        LocalDate hoy = LocalDate.now();
        LocalDate ingreso = reserva.getFechaCheckin();
        LocalDate salida = reserva.getFechaCheckout();
        if (ingreso == null || salida == null || !salida.isAfter(ingreso)) {
            throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de ingreso.");
        }
        if (ingreso.isBefore(hoy)) {
            throw new IllegalArgumentException("La fecha de ingreso no puede ser anterior a hoy.");
        }
        if (ingreso.isAfter(hoy.plusDays(MAX_DIAS_ANTICIPACION))) {
            throw new IllegalArgumentException("Solo se puede reservar con hasta " + MAX_DIAS_ANTICIPACION + " días de anticipación.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(ingreso, salida) > MAX_NOCHES) {
            throw new IllegalArgumentException("La estadía no puede superar " + MAX_NOCHES + " noches.");
        }
        if (checkinInmediato != ingreso.equals(hoy)) {
            throw new IllegalArgumentException("El check-in inmediato solo es posible con ingreso hoy.");
        }

        java.math.BigDecimal total = reserva.getMontoTotal();
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("El total de la estadía no es válido.");
        }
        if (pagos == null || pagos.isEmpty() || pagos.size() > 2) {
            throw new IllegalArgumentException("Debe registrarse el pago en uno o dos métodos.");
        }
        java.math.BigDecimal suma = java.math.BigDecimal.ZERO;
        for (Pago pago : pagos) {
            if (pago.getMonto() == null || pago.getMonto().signum() <= 0 || pago.getMonto().scale() > 2) {
                throw new IllegalArgumentException("Cada monto debe ser mayor a 0 y tener hasta 2 decimales.");
            }
            suma = suma.add(pago.getMonto());
        }
        // check-in: se paga todo; reserva: al menos el 50 %
        java.math.BigDecimal minimo = checkinInmediato ? total
                : total.multiply(new java.math.BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP);
        if (suma.compareTo(minimo) < 0) {
            throw new IllegalArgumentException(checkinInmediato
                    ? "El check-in requiere el pago completo."
                    : "El adelanto debe ser al menos el 50 % del total.");
        }
        if (suma.compareTo(total) > 0) {
            throw new IllegalArgumentException("El pago no puede superar el total de la estadía.");
        }
        reserva.setAdelanto(suma);
    }

    /**
     * @param empresa null si no se factura a una empresa
     * @param pagos uno o dos pagos (el adelanto o pago completo puede dividirse en dos metodos)
     * @throws IllegalStateException si la habitacion ya tiene una reserva en esas fechas
     */
    public int registrar(Huesped huesped, Empresa empresa, Reserva reserva, java.util.List<Pago> pagos,
                         boolean checkinInmediato) throws SQLException {
        validarDatos(huesped, reserva, pagos, checkinInmediato);
        int idUsuario = SesionActual.getUsuario() != null
                ? SesionActual.getUsuario().getIdUsuario() : USUARIO_POR_DEFECTO;

        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (checkinInmediato && !"DISPONIBLE".equals(habitacionDAO.obtenerEstado(con, reserva.getIdHabitacion()))) {
                    throw new IllegalStateException("La habitación ya no está disponible. Actualiza la lista de habitaciones.");
                }
                if (reservaDAO.existeCruce(con, reserva.getIdHabitacion(),
                        reserva.getFechaCheckin(), reserva.getFechaCheckout())) {
                    throw new ConflictoFechasException("La habitación ya tiene una reserva en esas fechas.");
                }

                Huesped existente = huespedDAO.buscarPorDocumento(con, huesped.getTipoDocumento(), huesped.getNumDocumento());
                int idHuesped = existente != null ? existente.getIdHuesped() : huespedDAO.insertar(con, huesped);

                reserva.setIdHuesped(idHuesped);
                reserva.setIdUsuario(idUsuario);
                reserva.setEstado(checkinInmediato ? "CHECKIN" : "CONFIRMADA");
                reserva.setIdEmpresa(empresa != null ? empresaDAO.guardar(con, empresa) : null);

                int idReserva = reservaDAO.insertar(con, reserva);

                for (Pago pago : pagos) {
                    pago.setIdReserva(idReserva);
                    pago.setIdUsuario(idUsuario);
                    pagoDAO.insertar(con, pago);
                }

                if (checkinInmediato) {
                    habitacionDAO.cambiarEstado(con, reserva.getIdHabitacion(), "OCUPADA");
                }

                con.commit();
                return idReserva;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Registra una reserva PENDIENTE: guarda el huesped (y la empresa, si se factura a una) y la
     * reserva sin pago todavia (adelanto en 0), tal como la deja el valor por defecto de la BD.
     * La reserva se confirma despues, con su pago, desde la pantalla "Confirmar reserva".
     * @throws IllegalStateException si la habitacion ya tiene una reserva en esas fechas
     */
    public int registrarPendiente(Huesped huesped, Empresa empresa, Reserva reserva) throws SQLException {
        reserva.setAdelanto(java.math.BigDecimal.ZERO);
        validarDatosPendiente(huesped, reserva);
        int idUsuario = SesionActual.getUsuario() != null
                ? SesionActual.getUsuario().getIdUsuario() : USUARIO_POR_DEFECTO;

        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (reservaDAO.existeCruce(con, reserva.getIdHabitacion(),
                        reserva.getFechaCheckin(), reserva.getFechaCheckout())) {
                    throw new ConflictoFechasException("La habitación ya tiene una reserva en esas fechas.");
                }

                Huesped existente = huespedDAO.buscarPorDocumento(con, huesped.getTipoDocumento(), huesped.getNumDocumento());
                int idHuesped = existente != null ? existente.getIdHuesped() : huespedDAO.insertar(con, huesped);

                reserva.setIdHuesped(idHuesped);
                reserva.setIdUsuario(idUsuario);
                reserva.setEstado("PENDIENTE");
                reserva.setIdEmpresa(empresa != null ? empresaDAO.guardar(con, empresa) : null);

                int idReserva = reservaDAO.insertar(con, reserva);
                con.commit();
                return idReserva;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Igual que validarDatos, pero sin exigir pago (una reserva pendiente todavia no se cobra). */
    private void validarDatosPendiente(Huesped huesped, Reserva reserva) {
        String tipoDoc = huesped.getTipoDocumento();
        String documento = huesped.getNumDocumento();
        boolean documentoValido = "DNI".equals(tipoDoc) ? Validador.esDniValido(documento)
                : "PASAPORTE".equals(tipoDoc) && Validador.esPasaporteValido(documento);
        if (!documentoValido) {
            throw new IllegalArgumentException("El documento de identidad no es válido.");
        }
        if (!Validador.esNombreValido(huesped.getNombres()) || !Validador.esNombreValido(huesped.getApellidos())) {
            throw new IllegalArgumentException("Los nombres y apellidos solo pueden tener letras (máximo 80 caracteres).");
        }
        if (!Validador.esPaisValido(huesped.getPaisProcedencia())) {
            throw new IllegalArgumentException("El país de procedencia no es válido.");
        }
        if (huesped.getTelefono() != null && !Validador.esTelefonoValido(huesped.getTelefono())) {
            throw new IllegalArgumentException("El teléfono debe tener 9 dígitos y empezar con 9.");
        }
        if (huesped.getEmail() != null && !Validador.esEmailValido(huesped.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico no es válido.");
        }

        LocalDate hoy = LocalDate.now();
        LocalDate ingreso = reserva.getFechaCheckin();
        LocalDate salida = reserva.getFechaCheckout();
        if (ingreso == null || salida == null || !salida.isAfter(ingreso)) {
            throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de ingreso.");
        }
        if (!ingreso.isAfter(hoy)) {
            throw new IllegalArgumentException("Una reserva pendiente solo se puede guardar con ingreso a partir de mañana; "
                    + "si el ingreso es hoy, use \"Confirmar reserva\" con el pago.");
        }
        if (ingreso.isAfter(hoy.plusDays(MAX_DIAS_ANTICIPACION))) {
            throw new IllegalArgumentException("Solo se puede reservar con hasta " + MAX_DIAS_ANTICIPACION + " días de anticipación.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(ingreso, salida) > MAX_NOCHES) {
            throw new IllegalArgumentException("La estadía no puede superar " + MAX_NOCHES + " noches.");
        }
        if (reserva.getMontoTotal() == null || reserva.getMontoTotal().signum() <= 0) {
            throw new IllegalArgumentException("El total de la estadía no es válido.");
        }
    }

    /** Reservas para la pantalla "Reservas programadas" (con filtros opcionales). */
    public List<Reserva> buscar(String texto, String estado, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return reservaDAO.buscar(con, texto, estado, desde, hasta);
        }
    }

    /** Una reserva por codigo, nombre o documento (para Confirmar/Cancelar reserva). */
    public Reserva buscarUno(String texto) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return reservaDAO.buscarUno(con, texto);
        }
    }

    /** Habitaciones libres para esas fechas (para elegir habitacion en una reserva nueva). */
    public List<Habitacion> buscarDisponibles(LocalDate ingreso, LocalDate salida, Integer piso, String tipoNombre) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return habitacionDAO.buscarDisponibles(con, ingreso, salida, piso, tipoNombre);
        }
    }

    /** Reservas vigentes que se cruzan con el rango de dias, para el calendario de ocupacion. */
    public List<Reserva> listarEnRango(LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection con = ConexionBD.conectar()) {
            return reservaDAO.listarEnRango(con, desde, hasta);
        }
    }

    /**
     * Cancela una reserva PENDIENTE o CONFIRMADA.
     * @throws IllegalStateException si la reserva no existe o ya paso por check-in, finalizo o esta cancelada
     */
    public void cancelar(int idReserva, String estadoActual, String motivo, String detalle) throws SQLException {
        if (!"PENDIENTE".equals(estadoActual) && !"CONFIRMADA".equals(estadoActual)) {
            throw new IllegalStateException("Solo se pueden cancelar reservas pendientes o confirmadas.");
        }
        try (Connection con = ConexionBD.conectar()) {
            reservaDAO.cancelar(con, idReserva, motivo, detalle);
        }
    }

    /**
     * Confirma una reserva PENDIENTE (la deja CONFIRMADA).
     * @throws IllegalStateException si la reserva no esta pendiente
     */
    public void confirmar(int idReserva, String estadoActual) throws SQLException {
        if (!"PENDIENTE".equals(estadoActual)) {
            throw new IllegalStateException("Solo se pueden confirmar reservas pendientes.");
        }
        try (Connection con = ConexionBD.conectar()) {
            reservaDAO.actualizarEstado(con, idReserva, "CONFIRMADA");
        }
    }
}
