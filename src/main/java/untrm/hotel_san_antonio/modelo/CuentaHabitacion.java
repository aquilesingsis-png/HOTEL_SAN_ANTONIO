package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuenta de una habitacion ocupada: datos del huesped y la estadia, lo que debe
 * (alojamiento + consumos), lo que ya pago y el saldo. No es una tabla: se arma
 * con consultas a reserva, pago y venta_tienda.
 */
public class CuentaHabitacion {

    private int idReserva;
    private int idHabitacion;
    private int idHuesped;
    private LocalDate ingreso;
    private String huesped;
    private String tipoDocumento;
    private String numDocumento;
    private String telefono;
    private String empresa; // razon social y RUC, null si no se factura a empresa
    private String fechaIngreso;
    private String fechaSalida;
    private LocalDate salida;       // fecha de salida como fecha, para poder ampliarla
    private int noches;           // noches de alojamiento que lleva la estadia
    private BigDecimal tarifaNoche;  // tarifa pactada por noche (monto total / noches)
    private final List<Linea> cargos = new ArrayList<>();
    private final List<PagoLinea> pagos = new ArrayList<>();

    public BigDecimal getTotalCuenta() {
        BigDecimal total = BigDecimal.ZERO;
        for (Linea l : cargos) {
            total = total.add(l.getSubtotalValor());
        }
        return total;
    }

    public BigDecimal getTotalPagos() {
        BigDecimal total = BigDecimal.ZERO;
        for (PagoLinea p : pagos) {
            total = total.add(p.getMontoValor());
        }
        return total;
    }

    public BigDecimal getSaldo() {
        return getTotalCuenta().subtract(getTotalPagos());
    }

    public static String formato(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Una fila de la tabla de cargos (alojamiento o consumo de tienda). */
    public static class Linea {
        private final String fecha;     // inicio (la noche empieza ese dia, o el dia de la venta)
        private final String fechaFin; // fin de la noche; "—" en los consumos de tienda
        private final String area;
        private final String detalle;
        private final int cantidad;
        private final BigDecimal precio;
        private final BigDecimal subtotal;

        public Linea(String fecha, String fechaFin, String area, String detalle, int cantidad, BigDecimal precio, BigDecimal subtotal) {
            this.fecha = fecha;
            this.fechaFin = fechaFin;
            this.area = area;
            this.detalle = detalle;
            this.cantidad = cantidad;
            this.precio = precio;
            this.subtotal = subtotal;
        }

        public String getFecha() { return fecha; }
        public String getFechaFin() { return fechaFin; }
        public String getArea() { return area; }
        public String getDetalle() { return detalle; }
        public int getCantidad() { return cantidad; }
        public String getPrecio() { return formato(precio); }
        public BigDecimal getPrecioValor() { return precio; }
        public String getSubtotal() { return formato(subtotal); }
        public BigDecimal getSubtotalValor() { return subtotal; }
    }

    /** Una fila de la tabla de pagos. */
    public static class PagoLinea {
        private final String fecha;
        private final String detalle;
        private final String metodo;
        private final BigDecimal monto;

        public PagoLinea(String fecha, String detalle, String metodo, BigDecimal monto) {
            this.fecha = fecha;
            this.detalle = detalle;
            this.metodo = metodo;
            this.monto = monto;
        }

        public String getFecha() { return fecha; }
        public String getDetalle() { return detalle; }
        public String getMetodo() { return metodo; }
        public String getMonto() { return formato(monto); }
        public BigDecimal getMontoValor() { return monto; }
    }

    public int getIdReserva() { return idReserva; }
    public void setIdReserva(int idReserva) { this.idReserva = idReserva; }

    public int getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(int idHabitacion) { this.idHabitacion = idHabitacion; }

    public int getIdHuesped() { return idHuesped; }
    public void setIdHuesped(int idHuesped) { this.idHuesped = idHuesped; }

    public LocalDate getIngreso() { return ingreso; }
    public void setIngreso(LocalDate ingreso) { this.ingreso = ingreso; }

    public String getHuesped() { return huesped; }
    public void setHuesped(String huesped) { this.huesped = huesped; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumDocumento() { return numDocumento; }
    public void setNumDocumento(String numDocumento) { this.numDocumento = numDocumento; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(String fechaIngreso) { this.fechaIngreso = fechaIngreso; }

    public String getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(String fechaSalida) { this.fechaSalida = fechaSalida; }

    public LocalDate getSalida() { return salida; }
    public void setSalida(LocalDate salida) { this.salida = salida; }

    public int getNoches() { return noches; }
    public void setNoches(int noches) { this.noches = noches; }

    public BigDecimal getTarifaNoche() { return tarifaNoche; }
    public void setTarifaNoche(BigDecimal tarifaNoche) { this.tarifaNoche = tarifaNoche; }

    public List<Linea> getCargos() { return cargos; }
    public List<PagoLinea> getPagos() { return pagos; }
}
