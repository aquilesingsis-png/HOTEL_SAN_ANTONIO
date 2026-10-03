package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Gasto {
    private int idGasto;
    private LocalDate fecha;
    private String concepto;
    private String categoria;
    private BigDecimal monto;
    private String metodoPago;
    private String observacion;
    private String motivoRegistroTardio;

    public int getIdGasto() { return idGasto; }
    public void setIdGasto(int idGasto) { this.idGasto = idGasto; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public String getMotivoRegistroTardio() { return motivoRegistroTardio; }
    public void setMotivoRegistroTardio(String motivoRegistroTardio) { this.motivoRegistroTardio = motivoRegistroTardio; }
}
