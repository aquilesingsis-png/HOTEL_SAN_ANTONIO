package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.PagoDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Junta, en una sola consulta a la BD, todos los numeros que muestra el Dashboard. */
public class DashboardService {

    private final HabitacionDAO habitacionDAO = new HabitacionDAO();
    private final ReservaDAO reservaDAO = new ReservaDAO();
    private final PagoDAO pagoDAO = new PagoDAO();
    private final VentaTiendaDAO ventaTiendaDAO = new VentaTiendaDAO();

    public Resumen obtenerResumen() throws SQLException {
        try (Connection con = ConexionBD.conectar()) {

            Resumen r = new Resumen();

            List<Habitacion> habitaciones = habitacionDAO.listar(con);
            r.totalHabitaciones = habitaciones.size();
            for (Habitacion h : habitaciones) {
                switch (h.getEstado()) {
                    case "OCUPADA" -> r.ocupadas++;
                    case "DISPONIBLE" -> r.disponibles++;
                    case "LIMPIEZA" -> r.limpieza++;
                    case "MANTENIMIENTO" -> {
                        r.mantenimiento++;
                        r.enMantenimiento.add(h);
                    }
                    default -> { }
                }
            }
            // Sin un conteo de personas por reserva, se usa la habitacion ocupada como aproximacion.
            r.huespedes = r.ocupadas;

            r.llegadasHoy = reservaDAO.listarLlegadasHoy(con);
            r.reservasHoy = r.llegadasHoy.size();
            r.pendientesConfirmar = reservaDAO.buscar(con, null, "PENDIENTE", null, null).size();

            r.ingresosHabitacionesHoy = pagoDAO.sumarHoy(con);
            r.ingresosTiendaHoy = ventaTiendaDAO.sumarHoy(con);
            r.ventasTiendaHoyCantidad = ventaTiendaDAO.contarHoy(con);

            LocalDate hoy = LocalDate.now();
            LocalDate hace6Dias = hoy.minusDays(6);
            Map<LocalDate, BigDecimal> pagosPorDia = pagoDAO.sumarPorDia(con, hace6Dias, hoy);
            Map<LocalDate, BigDecimal> ventasPorDia = ventaTiendaDAO.sumarPorDia(con, hace6Dias, hoy);
            for (LocalDate dia = hace6Dias; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
                r.ingresosUltimaSemana.put(dia,
                        pagosPorDia.getOrDefault(dia, BigDecimal.ZERO).add(ventasPorDia.getOrDefault(dia, BigDecimal.ZERO)));
            }

            return r;
        }
    }

    /** Numeros y listas que necesita dashboard_2.fxml; sin logica, solo datos ya calculados. */
    public static class Resumen {
        public int totalHabitaciones;
        public int ocupadas;
        public int disponibles;
        public int limpieza;
        public int mantenimiento;
        public int huespedes;
        public int reservasHoy;
        public int pendientesConfirmar;
        public BigDecimal ingresosHabitacionesHoy = BigDecimal.ZERO;
        public BigDecimal ingresosTiendaHoy = BigDecimal.ZERO;
        public int ventasTiendaHoyCantidad;
        public List<Reserva> llegadasHoy = new ArrayList<>();
        public List<Habitacion> enMantenimiento = new ArrayList<>();
        public java.util.LinkedHashMap<LocalDate, BigDecimal> ingresosUltimaSemana = new java.util.LinkedHashMap<>();

        public BigDecimal getIngresosHoy() {
            return ingresosHabitacionesHoy.add(ingresosTiendaHoy);
        }

        public int getPorcentajeOcupacion() {
            return totalHabitaciones == 0 ? 0 : Math.round(ocupadas * 100f / totalHabitaciones);
        }
    }
}
