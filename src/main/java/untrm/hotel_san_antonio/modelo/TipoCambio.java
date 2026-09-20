package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;

/** No es tabla de la BD -- es el resultado de consultar la API de tipo de cambio. */
public class TipoCambio {
    private BigDecimal compra;
    private BigDecimal venta;
    private String monedaBase;
    private String monedaDestino;
    private String fecha;

    public TipoCambio() {}

    public BigDecimal getCompra() { return compra; }
    public void setCompra(BigDecimal compra) { this.compra = compra; }

    public BigDecimal getVenta() { return venta; }
    public void setVenta(BigDecimal venta) { this.venta = venta; }

    public String getMonedaBase() { return monedaBase; }
    public void setMonedaBase(String monedaBase) { this.monedaBase = monedaBase; }

    public String getMonedaDestino() { return monedaDestino; }
    public void setMonedaDestino(String monedaDestino) { this.monedaDestino = monedaDestino; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}
