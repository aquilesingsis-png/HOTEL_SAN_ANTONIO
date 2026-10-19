package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Un comprobante emitido tal como se ve al imprimirlo: el comprobante (con su cliente y su desglose) y las
 * lineas de lo que se cobro. Se arma siempre desde lo guardado en la base, asi se puede volver a imprimir
 * igual que la primera vez.
 */
public class ComprobanteDocumento {

    /** Una linea del detalle: alojamiento o producto vendido. */
    public record Linea(String concepto, int cantidad, BigDecimal precio, BigDecimal importe) { }

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final BigDecimal IGV = new BigDecimal("1.18");

    private final Comprobante comprobante;
    private final List<Linea> lineas;

    public ComprobanteDocumento(Comprobante comprobante, List<Linea> lineas) {
        this.comprobante = comprobante;
        this.lineas = lineas;
    }

    public Comprobante getComprobante() {
        return comprobante;
    }

    public List<Linea> getLineas() {
        return lineas;
    }

    /** El nombre del tipo, como va en el encabezado. */
    public static String nombreTipo(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "BOLETA" -> "BOLETA DE VENTA";
            case "FACTURA" -> "FACTURA";
            case "NOTA_VENTA" -> "NOTA DE VENTA";
            default -> "COMPROBANTE";
        };
    }

    /** El comprobante escrito en lineas de texto (es lo que se pasa al PDF). */
    public String aTexto() {
        Comprobante c = comprobante;
        StringBuilder texto = new StringBuilder();
        texto.append("HOTEL SAN ANTONIO\n");
        texto.append(nombreTipo(c.getTipo())).append("   N.º ").append(c.getNumero()).append('\n');
        if (c.isAnulado()) {
            texto.append("*** COMPROBANTE ANULADO ***\n");
        }
        if (c.getFechaEmision() != null) {
            texto.append("Fecha de emisión: ").append(c.getFechaEmision().format(FECHA_HORA)).append('\n');
        }
        texto.append("Cliente: ").append(vacio(c.getClienteNombre()) ? "Cliente varios" : c.getClienteNombre()).append('\n');
        if (!vacio(c.getClienteDocumento())) {
            texto.append(vacio(c.getClienteTipoDoc()) ? "Documento" : c.getClienteTipoDoc())
                    .append(": ").append(c.getClienteDocumento()).append('\n');
        }
        if (!vacio(c.getClienteDireccion())) {
            texto.append("Dirección: ").append(c.getClienteDireccion()).append('\n');
        }
        texto.append('\n');
        for (Linea l : lineas) {
            texto.append(String.format(Locale.US, "%s  x%d  S/ %.2f%n", l.concepto(), l.cantidad(), l.importe()));
        }
        texto.append('\n');
        BigDecimal total = c.getMontoTotal();
        if (!"NOTA_VENTA".equals(c.getTipo())) {
            BigDecimal subtotal = c.getSubtotal() != null ? c.getSubtotal()
                    : total.divide(IGV, 2, RoundingMode.HALF_UP);
            BigDecimal igv = c.getIgv() != null ? c.getIgv() : total.subtract(subtotal);
            texto.append(String.format(Locale.US, "Subtotal: S/ %.2f%n", subtotal));
            texto.append(String.format(Locale.US, "IGV (18%%): S/ %.2f%n", igv));
        }
        texto.append(String.format(Locale.US, "TOTAL: S/ %.2f%n", total));
        if (!vacio(c.getEmitidoPor())) {
            texto.append("\nEmitido por: ").append(c.getEmitidoPor()).append('\n');
        }
        return texto.toString();
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
