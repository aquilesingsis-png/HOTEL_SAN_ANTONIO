package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.AuditoriaDAO;
import untrm.hotel_san_antonio.dao.CuentaDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.PagoDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.modelo.CuentaHabitacion;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Pago;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Operaciones sobre la cuenta de una habitacion ocupada: consultar, cobrar y hacer check-out. */
public class CuentaService {

    private static final Set<String> METODOS_PAGO = Set.of("YAPE", "TRANSFERENCIA", "EFECTIVO", "TARJETA");

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

    /** @return la cuenta, o null si la habitacion no tiene una estadia activa */
    public CuentaHabitacion obtener(int idHabitacion) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            return cuentaDAO.obtener(con, idHabitacion);
        }
    }

    /**
     * Registra un pago posterior al adelanto, en uno o dos metodos (una fila de pago por metodo).
     * @param partes cada Pago trae solo monto y metodo; el servicio completa el resto
     * @throws IllegalStateException si la estadia ya no esta activa o los montos no son validos
     */
    public void registrarPagos(int idHabitacion, java.util.List<Pago> partes) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (partes == null || partes.isEmpty()) {
            throw new IllegalStateException("Indica el monto del pago.");
        }
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                CuentaHabitacion cuenta = cuentaDAO.obtener(con, idHabitacion);
                if (cuenta == null) {
                    throw new IllegalStateException("La habitación ya no tiene una estadía activa.");
                }
                BigDecimal suma = BigDecimal.ZERO;
                for (Pago parte : partes) {
                    if (parte.getMonto() == null || parte.getMonto().signum() <= 0 || parte.getMonto().scale() > 2) {
                        throw new IllegalStateException("Cada monto debe ser mayor a 0 y tener hasta 2 decimales.");
                    }
                    if (parte.getMetodoPago() == null || !METODOS_PAGO.contains(parte.getMetodoPago())) {
                        throw new IllegalStateException("Selecciona un método de pago válido.");
                    }
                    suma = suma.add(parte.getMonto());
                }
                if (suma.compareTo(cuenta.getSaldo()) > 0) {
                    throw new IllegalStateException("El pago no puede superar el saldo pendiente.");
                }

                int idUsuario = SesionActual.getUsuario().getIdUsuario();
                StringBuilder detalle = new StringBuilder("hab. " + numeroHabitacion(con, idHabitacion) + " ·");
                for (Pago parte : partes) {
                    parte.setIdReserva(cuenta.getIdReserva());
                    parte.setIdUsuario(idUsuario);
                    parte.setTipoPago("SALDO");
                    pagoDAO.insertar(con, parte);
                    detalle.append(" S/ ").append(CuentaHabitacion.formato(parte.getMonto()))
                            .append(' ').append(parte.getMetodoPago());
                }
                auditoriaDAO.registrar(con, idUsuario, "PAGO_CUENTA", "reserva", cuenta.getIdReserva(),
                        detalle.toString());

                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Agrega noches a la estadia en curso, a la tarifa pactada, sin crear otra reserva.
     * @throws IllegalStateException si la estadia no esta activa o otra reserva ocupa esas noches
     */
    public void ampliarEstadia(int idHabitacion, int noches) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                CuentaHabitacion cuenta = cuentaDAO.obtener(con, idHabitacion);
                if (cuenta == null) {
                    throw new IllegalStateException("La habitación ya no tiene una estadía activa.");
                }
                if (noches < 1) {
                    throw new IllegalStateException("Indica al menos una noche.");
                }
                if (cuenta.getNoches() + noches > ReservaService.MAX_NOCHES) {
                    throw new IllegalStateException("La estadía no puede superar " + ReservaService.MAX_NOCHES
                            + " noches (lleva " + cuenta.getNoches() + ").");
                }
                LocalDate salidaActual = cuenta.getSalida();
                LocalDate nuevaSalida = salidaActual.plusDays(noches);

                LocalDate reservadaDesde = reservaDAO.primerCruce(con, idHabitacion, salidaActual, nuevaSalida);
                if (reservadaDesde != null) {
                    throw new IllegalStateException("No se puede ampliar: la habitación está reservada desde el "
                            + reservadaDesde.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
                }

                BigDecimal incremento = cuenta.getTarifaNoche().multiply(BigDecimal.valueOf(noches));
                reservaDAO.ampliar(con, cuenta.getIdReserva(), nuevaSalida, incremento);
                auditoriaDAO.registrar(con, SesionActual.getUsuario().getIdUsuario(), "AMPLIAR_ESTADIA", "reserva",
                        cuenta.getIdReserva(), "hab. " + numeroHabitacion(con, idHabitacion) + " · +" + noches
                        + (noches == 1 ? " noche" : " noches") + " · sale el "
                        + nuevaSalida.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Cierra la estadia: la reserva pasa a FINALIZADA y la habitacion a LIMPIEZA.
     * @throws IllegalStateException si aun hay saldo pendiente
     */
    public void checkOut(int idHabitacion) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                CuentaHabitacion cuenta = cuentaDAO.obtener(con, idHabitacion);
                if (cuenta == null) {
                    throw new IllegalStateException("La habitación ya no tiene una estadía activa.");
                }
                if (cuenta.getSaldo().signum() > 0) {
                    throw new IllegalStateException("Aún hay un saldo pendiente de S/ "
                            + CuentaHabitacion.formato(cuenta.getSaldo()) + ".");
                }
                reservaDAO.finalizar(con, cuenta.getIdReserva());
                habitacionDAO.cambiarEstado(con, idHabitacion, "LIMPIEZA");
                auditoriaDAO.registrar(con, SesionActual.getUsuario().getIdUsuario(), "CHECKOUT", "reserva",
                        cuenta.getIdReserva(), "hab. " + numeroHabitacion(con, idHabitacion) + " · "
                        + cuenta.getHuesped());
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private String numeroHabitacion(Connection con, int idHabitacion) throws SQLException {
        Habitacion h = habitacionDAO.buscarPorId(con, idHabitacion);
        return h == null ? String.valueOf(idHabitacion) : h.getNumero();
    }
}
