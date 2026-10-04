package untrm.hotel_san_antonio.servicio;

import untrm.hotel_san_antonio.dao.DashboardDAO;
import untrm.hotel_san_antonio.dao.HabitacionDAO;
import untrm.hotel_san_antonio.dao.PagoDAO;
import untrm.hotel_san_antonio.dao.ReservaDAO;
import untrm.hotel_san_antonio.dao.VentaTiendaDAO;
import untrm.hotel_san_antonio.modelo.Habitacion;
import untrm.hotel_san_antonio.modelo.Reserva;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.SesionActual;

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
    private final DashboardDAO dashboardDAO = new DashboardDAO();

    /** Productos con este stock o menos se consideran "con stock bajo". */
    private static final int UMBRAL_STOCK_BAJO = 10;
    /** Días sin respaldo a partir de los cuales el administrador recibe un aviso. */
    private static final int DIAS_AVISO_RESPALDO = 7;

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
            r.huespedes = reservaDAO.sumarHuespedesActivos(con);

            r.llegadasHoy = reservaDAO.listarLlegadasHoy(con);
            r.reservasHoy = r.llegadasHoy.size();
            r.salidasHoy = dashboardDAO.salidasHoy(con);

            r.ingresosHabitacionesHoy = pagoDAO.sumarHoy(con);
            r.ingresosTiendaHoy = ventaTiendaDAO.sumarHoy(con);
            r.ventasTiendaHoyCantidad = ventaTiendaDAO.contarHoy(con);

            LocalDate hoy = LocalDate.now();
            LocalDate ayer = hoy.minusDays(1);

            // Para comparar contra ayer en las tarjetas KPI (con datos reales, no numeros fijos).
            r.ocupadasAyer = reservaDAO.contarOcupadasEnFecha(con, ayer);
            r.disponiblesAyer = Math.max(0, r.totalHabitaciones - r.ocupadasAyer);
            r.huespedesAyer = reservaDAO.sumarHuespedesEnFecha(con, ayer);
            r.reservasAyer = reservaDAO.contarLlegadasEnFecha(con, ayer);
            Map<LocalDate, BigDecimal> pagosAyerMapa = pagoDAO.sumarPorDia(con, ayer, ayer);
            Map<LocalDate, BigDecimal> ventasAyerMapa = ventaTiendaDAO.sumarPorDia(con, ayer, ayer);
            r.ingresosAyer = pagosAyerMapa.getOrDefault(ayer, BigDecimal.ZERO).add(ventasAyerMapa.getOrDefault(ayer, BigDecimal.ZERO));
            r.ventasTiendaAyer = ventasAyerMapa.getOrDefault(ayer, BigDecimal.ZERO);

            LocalDate hace6Dias = hoy.minusDays(6);
            Map<LocalDate, BigDecimal> pagosPorDia = pagoDAO.sumarPorDia(con, hace6Dias, hoy);
            Map<LocalDate, BigDecimal> ventasPorDia = ventaTiendaDAO.sumarPorDia(con, hace6Dias, hoy);
            for (LocalDate dia = hace6Dias; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
                r.ingresosUltimaSemana.put(dia,
                        pagosPorDia.getOrDefault(dia, BigDecimal.ZERO).add(ventasPorDia.getOrDefault(dia, BigDecimal.ZERO)));
            }

            // Lo que ve cada rol: el administrador, el movimiento de 30 días y sus avisos; Recepción, solo el día
            boolean admin = SesionActual.esAdministrador();
            r.movimientoPorHora = dashboardDAO.movimientoPorHora(con, admin);
            if (admin) {
                r.productosAgotados = dashboardDAO.productosAgotados(con);
                r.productosStockBajo = dashboardDAO.productosConStockBajo(con, UMBRAL_STOCK_BAJO);
                r.cuentasBloqueadas = dashboardDAO.cuentasBloqueadas(con);
                int dias = dashboardDAO.diasDesdeUltimoRespaldo(con);
                r.diasSinRespaldo = dias < 0 || dias >= DIAS_AVISO_RESPALDO ? dias : 0;
            }
            try {
                DiasSinUsoService.Estado estado = new DiasSinUsoService().estado();
                r.diasSinRegistrar = estado.pendientes().size();
                r.registroAbierto = estado.abierto();
            } catch (SecurityException ignorado) {
                // un rol sin acceso al registro de días pasados no ve ese aviso
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
        public List<String> salidasHoy = new ArrayList<>();
        public int productosAgotados;
        public int productosStockBajo;
        public int cuentasBloqueadas;
        /** -1 = nunca se hizo un respaldo; 0 = respaldo reciente (sin aviso); más = días sin respaldar. */
        public int diasSinRespaldo;
        public int diasSinRegistrar;
        public boolean registroAbierto;
        public java.util.Map<Integer, Integer> movimientoPorHora = new java.util.TreeMap<>();
        public BigDecimal ingresosHabitacionesHoy = BigDecimal.ZERO;
        public BigDecimal ingresosTiendaHoy = BigDecimal.ZERO;
        public int ventasTiendaHoyCantidad;
        public int ocupadasAyer;
        public int disponiblesAyer;
        public int huespedesAyer;
        public int reservasAyer;
        public BigDecimal ingresosAyer = BigDecimal.ZERO;
        public BigDecimal ventasTiendaAyer = BigDecimal.ZERO;
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
