package untrm.hotel_san_antonio.modelo.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import untrm.hotel_san_antonio.modelo.conexion.ConexionBD;
import untrm.hotel_san_antonio.modelo.dao.ReportesDAO;
import untrm.hotel_san_antonio.modelo.seguridad.Permisos;

/**
 * Reportes del Administrador: ocupación, ingresos y ventas de tienda. Cada uno devuelve ya armada
 * la tabla, los totales y los datos del gráfico, para que la pantalla solo tenga que mostrarlos.
 */
public class ReportesService {

    /**
     * @param encabezados columnas de la tabla
     * @param filas       los datos como texto
     * @param numericas   columnas con números (van a la derecha)
     * @param totales     tarjetas de arriba: etiqueta y valor
     * @param etiquetas   categorías del gráfico
     * @param valores     altura de cada barra
     * @param tituloGrafico lo que mide el gráfico
     */
    public record Resultado(List<String> encabezados, List<List<String>> filas, Set<Integer> numericas,
                            List<String[]> totales, List<String> etiquetas, List<Double> valores,
                            String tituloGrafico) { }

    /** Una fila del reporte de ocupación: un tipo de habitación en un día. */
    public record FilaOcupacion(int id, List<String> celdas, LocalDate fecha) { }

    private static final int MAX_DIAS = 3660;
    private static final int MAX_BARRAS = 31;
    private static final int MAX_RANKING = 10;

    private static final class OcupacionDia {
        int disponibles;
        final Set<Integer> habitaciones = new HashSet<>();
        int reservas;
        int huespedes;
    }

    private final ReportesDAO dao = new ReportesDAO();

    public List<String> categorias() throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        return dao.categorias();
    }

    public List<String> tiposHabitacion() throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        return dao.tiposHabitacion();
    }

    private void validar(LocalDate desde, LocalDate hasta) {
        Permisos.requerir("ADMINISTRADOR");
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indique las fechas desde y hasta.");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS) {
            throw new IllegalArgumentException("El período no puede superar " + MAX_DIAS + " días.");
        }
    }

    // ---------------------------------------------------------------- ocupación

    /** Cuenta todas las noches del período, incluidas las de ocupación cero. */
    public List<FilaOcupacion> ocupacionDiaria(LocalDate desde, LocalDate hasta) throws SQLException {
        Permisos.requerir("ADMINISTRADOR");
        LocalDate inicio = desde == null ? LocalDate.now().minusDays(29) : desde;
        LocalDate fin = hasta == null ? LocalDate.now() : hasta;
        long noches = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (noches <= 0 || noches > MAX_DIAS)
            throw new IllegalArgumentException("El período de ocupación debe tener entre 1 y 3660 días.");
        TreeMap<LocalDate, Map<String, OcupacionDia>> porFecha = new TreeMap<>();
        try (Connection con = ConexionBD.conectar()) {
            Map<String, Integer> inventario = dao.habitacionesPorTipo(con);
            for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
                Map<String, OcupacionDia> tipos = new TreeMap<>();
                for (var tipo : inventario.entrySet()) {
                    OcupacionDia dia = new OcupacionDia();
                    dia.disponibles = tipo.getValue();
                    tipos.put(tipo.getKey(), dia);
                }
                porFecha.put(fecha, tipos);
            }
            for (ReportesDAO.ReservaEnPeriodo reserva : dao.reservasQueCruzan(con, inicio, fin)) {
                for (LocalDate fecha = reserva.ingreso().isBefore(inicio) ? inicio : reserva.ingreso();
                     fecha.isBefore(reserva.salida()) && !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
                    OcupacionDia dia = porFecha.get(fecha).get(reserva.tipo());
                    dia.habitaciones.add(reserva.idHabitacion());
                    dia.reservas++;
                    dia.huespedes += reserva.huespedes();
                }
            }
        }
        List<FilaOcupacion> resultado = new ArrayList<>();
        for (var fecha : porFecha.descendingMap().entrySet()) {
            for (var tipo : fecha.getValue().entrySet()) {
                OcupacionDia dia = tipo.getValue();
                int ocupadas = dia.habitaciones.size();
                String porcentaje = dia.disponibles == 0 ? "0.0" : BigDecimal.valueOf(ocupadas * 100L)
                        .divide(BigDecimal.valueOf(dia.disponibles), 1, RoundingMode.HALF_UP).toPlainString();
                resultado.add(new FilaOcupacion(0, List.of(fecha.getKey().toString(), tipo.getKey(),
                        String.valueOf(dia.disponibles), String.valueOf(ocupadas), porcentaje,
                        String.valueOf(dia.reservas), String.valueOf(dia.huespedes)), fecha.getKey()));
            }
        }
        return resultado;
    }

    /** @param agrupacion Día, Semana o Mes; @param tipo nombre del tipo de habitación o null (todos) */
    public Resultado ocupacion(LocalDate desde, LocalDate hasta, String tipo, String agrupacion) throws SQLException {
        validar(desde, hasta);
        // por cada período y tipo: noches disponibles, ocupadas, reservas y huéspedes
        Map<String, long[]> porPeriodoYTipo = new LinkedHashMap<>();
        Map<String, long[]> porPeriodo = new LinkedHashMap<>();
        Map<String, LocalDate> inicioPeriodo = new LinkedHashMap<>();
        List<FilaOcupacion> dias = ocupacionDiaria(desde, hasta);
        for (int i = dias.size() - 1; i >= 0; i--) { // del más antiguo al más reciente
            FilaOcupacion dia = dias.get(i);
            List<String> c = dia.celdas();
            if (tipo != null && !tipo.equals(c.get(1))) {
                continue;
            }
            LocalDate fecha = LocalDate.parse(c.get(0));
            LocalDate inicio = switch (agrupacion) {
                case "Semana" -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case "Mes" -> fecha.withDayOfMonth(1);
                default -> fecha;
            };
            String periodo = agrupacion.equals("Mes") ? inicio.toString().substring(0, 7) : inicio.toString();
            inicioPeriodo.putIfAbsent(periodo, inicio);
            long[] valores = {Long.parseLong(c.get(2)), Long.parseLong(c.get(3)),
                Long.parseLong(c.get(5)), Long.parseLong(c.get(6))};
            sumar(porPeriodoYTipo.computeIfAbsent(periodo + "|" + c.get(1), k -> new long[4]), valores);
            sumar(porPeriodo.computeIfAbsent(periodo, k -> new long[4]), valores);
        }
        List<List<String>> filas = new ArrayList<>();
        long disponibles = 0;
        long ocupadas = 0;
        for (var entrada : porPeriodoYTipo.entrySet()) {
            long[] v = entrada.getValue();
            int corte = entrada.getKey().indexOf('|');
            filas.add(List.of(entrada.getKey().substring(0, corte), entrada.getKey().substring(corte + 1),
                    String.valueOf(v[0]), String.valueOf(v[1]), porcentaje(v[1], v[0]),
                    String.valueOf(v[2]), String.valueOf(v[3])));
            disponibles += v[0];
            ocupadas += v[1];
        }
        List<String> etiquetas = new ArrayList<>();
        List<Double> valoresGrafico = new ArrayList<>();
        for (var entrada : porPeriodo.entrySet()) {
            etiquetas.add(entrada.getKey());
            valoresGrafico.add(Double.parseDouble(porcentaje(entrada.getValue()[1], entrada.getValue()[0])));
        }
        recortar(etiquetas, valoresGrafico, true, MAX_BARRAS);
        return new Resultado(
                List.of("Período", "Tipo de habitación", "Noches disponibles", "Noches ocupadas", "Ocupación (%)",
                        "Reservas", "Huéspedes"),
                filas, Set.of(2, 3, 4, 5, 6),
                List.of(new String[] {"Noches disponibles", String.valueOf(disponibles)},
                        new String[] {"Noches ocupadas", String.valueOf(ocupadas)},
                        new String[] {"Ocupación (%)", porcentaje(ocupadas, disponibles)}),
                etiquetas, valoresGrafico, "Ocupación (%) por período");
    }

    private void sumar(long[] destino, long[] valores) {
        for (int i = 0; i < destino.length; i++) {
            destino[i] += valores[i];
        }
    }

    private String porcentaje(long parte, long total) {
        return total == 0 ? "0.0" : BigDecimal.valueOf(parte * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toPlainString();
    }

    // ----------------------------------------------------------------- ingresos

    /**
     * Dinero cobrado: pagos de alojamiento y ventas de tienda cobradas en el momento. Lo cargado a
     * una habitación no se cuenta aquí porque se cobra después con el pago de la habitación.
     *
     * @param agrupacion Día, Mes, Medio de pago u Origen
     */
    public Resultado ingresos(LocalDate desde, LocalDate hasta, String origen, String metodo,
                              String agrupacion) throws SQLException {
        validar(desde, hasta);
        // grupo -> [alojamiento, tienda, operaciones]
        Map<String, BigDecimal[]> grupos = new LinkedHashMap<>();
        BigDecimal alojamiento = BigDecimal.ZERO;
        BigDecimal tienda = BigDecimal.ZERO;
        for (ReportesDAO.Cobro cobro : dao.cobros(desde, hasta, origen, metodo)) {
            LocalDate dia = cobro.dia();
            String o = cobro.origen();
            String grupo = switch (agrupacion) {
                case "Mes" -> dia.toString().substring(0, 7);
                case "Medio de pago" -> nombreMetodo(cobro.metodo());
                case "Origen" -> o;
                default -> dia.toString();
            };
            BigDecimal[] g = grupos.computeIfAbsent(grupo,
                    k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal monto = cobro.monto();
            if (o.equals("Alojamiento")) {
                g[0] = g[0].add(monto);
                alojamiento = alojamiento.add(monto);
            } else {
                g[1] = g[1].add(monto);
                tienda = tienda.add(monto);
            }
            g[2] = g[2].add(BigDecimal.ONE);
        }
        List<List<String>> filas = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        List<Double> valoresGrafico = new ArrayList<>();
        for (var entrada : grupos.entrySet()) {
            BigDecimal[] g = entrada.getValue();
            BigDecimal total = g[0].add(g[1]);
            filas.add(List.of(entrada.getKey(), dinero(g[0]), dinero(g[1]), dinero(total), g[2].toPlainString()));
            etiquetas.add(entrada.getKey());
            valoresGrafico.add(total.doubleValue());
        }
        if (agrupacion.equals("Medio de pago") || agrupacion.equals("Origen")) {
            ordenarPorValor(filas, etiquetas, valoresGrafico);
        }
        recortar(etiquetas, valoresGrafico, agrupacion.equals("Día") || agrupacion.equals("Mes"),
                agrupacion.equals("Día") || agrupacion.equals("Mes") ? MAX_BARRAS : MAX_RANKING);
        return new Resultado(
                List.of(agrupacion.equals("Día") || agrupacion.equals("Mes") ? "Período" : agrupacion,
                        "Alojamiento (S/)", "Tienda (S/)", "Total cobrado (S/)", "Operaciones"),
                filas, Set.of(1, 2, 3, 4),
                List.of(new String[] {"Cobros de alojamiento (S/)", dinero(alojamiento)},
                        new String[] {"Cobros de tienda (S/)", dinero(tienda)},
                        new String[] {"Total cobrado (S/)", dinero(alojamiento.add(tienda))}),
                etiquetas, valoresGrafico, "Total cobrado (S/)");
    }

    private String nombreMetodo(String metodo) {
        return switch (metodo) {
            case "EFECTIVO" -> "Efectivo";
            case "YAPE" -> "Yape";
            case "TRANSFERENCIA" -> "Transferencia";
            case "TARJETA" -> "Tarjeta";
            default -> "No registrado";
        };
    }

    // ------------------------------------------------------------------- ventas

    /**
     * Ventas de la tienda, por producto, categoría, día o mes.
     *
     * @param categoria nombre de la categoría o null (todas)
     * @param texto     parte del nombre o del código de barras del producto (puede ser null)
     */
    public Resultado ventas(LocalDate desde, LocalDate hasta, String categoria, String texto,
                            String agrupacion) throws SQLException {
        validar(desde, hasta);
        // grupo -> [unidades, importe]
        Map<String, BigDecimal[]> grupos = new LinkedHashMap<>();
        Set<Integer> ventas = new HashSet<>();
        BigDecimal unidadesTotal = BigDecimal.ZERO;
        BigDecimal importeTotal = BigDecimal.ZERO;
        for (ReportesDAO.LineaVendida linea : dao.lineasVendidas(desde, hasta, categoria, texto)) {
            LocalDate dia = linea.dia();
            String grupo = switch (agrupacion) {
                case "Categoría" -> linea.categoria();
                case "Día" -> dia.toString();
                case "Mes" -> dia.toString().substring(0, 7);
                default -> linea.producto() + "  (" + linea.codigoBarra() + ")";
            };
            BigDecimal cantidad = BigDecimal.valueOf(linea.cantidad());
            BigDecimal subtotal = linea.subtotal();
            BigDecimal[] g = grupos.computeIfAbsent(grupo, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            g[0] = g[0].add(cantidad);
            g[1] = g[1].add(subtotal);
            unidadesTotal = unidadesTotal.add(cantidad);
            importeTotal = importeTotal.add(subtotal);
            ventas.add(linea.idVenta());
        }
        List<List<String>> filas = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        List<Double> valoresGrafico = new ArrayList<>();
        for (var entrada : grupos.entrySet()) {
            BigDecimal[] g = entrada.getValue();
            filas.add(List.of(entrada.getKey(), g[0].toPlainString(),
                    dinero(g[1].divide(g[0], 2, RoundingMode.HALF_UP)), dinero(g[1])));
            etiquetas.add(entrada.getKey());
            valoresGrafico.add(g[1].doubleValue());
        }
        if (agrupacion.equals("Producto") || agrupacion.equals("Categoría")) {
            ordenarPorValor(filas, etiquetas, valoresGrafico);
        }
        recortar(etiquetas, valoresGrafico, agrupacion.equals("Día") || agrupacion.equals("Mes"),
                agrupacion.equals("Día") || agrupacion.equals("Mes") ? MAX_BARRAS : MAX_RANKING);
        return new Resultado(
                List.of(agrupacion.equals("Día") || agrupacion.equals("Mes") ? "Período" : agrupacion,
                        "Unidades vendidas", "Precio promedio (S/)", "Importe vendido (S/)"),
                filas, Set.of(1, 2, 3),
                List.of(new String[] {"Ventas registradas", String.valueOf(ventas.size())},
                        new String[] {"Unidades vendidas", unidadesTotal.toPlainString()},
                        new String[] {"Importe vendido (S/)", dinero(importeTotal)}),
                etiquetas, valoresGrafico, "Importe vendido (S/)");
    }

    // ------------------------------------------------------------------ comunes

    private String dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Ordena filas, etiquetas y valores del mayor al menor valor (para rankings). */
    private void ordenarPorValor(List<List<String>> filas, List<String> etiquetas, List<Double> valores) {
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < valores.size(); i++) {
            orden.add(i);
        }
        orden.sort((a, b) -> Double.compare(valores.get(b), valores.get(a)));
        List<List<String>> f = new ArrayList<>();
        List<String> e = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        for (int i : orden) {
            f.add(filas.get(i));
            e.add(etiquetas.get(i));
            v.add(valores.get(i));
        }
        filas.clear();
        filas.addAll(f);
        etiquetas.clear();
        etiquetas.addAll(e);
        valores.clear();
        valores.addAll(v);
    }

    /**
     * El gráfico muestra pocas barras para que se lea: en una serie en el tiempo las más recientes
     * (últimas = true); en un ranking las primeras (las mayores).
     */
    private void recortar(List<String> etiquetas, List<Double> valores, boolean ultimas, int maximo) {
        while (etiquetas.size() > maximo) {
            int quitar = ultimas ? 0 : etiquetas.size() - 1;
            etiquetas.remove(quitar);
            valores.remove(quitar);
        }
    }
}
