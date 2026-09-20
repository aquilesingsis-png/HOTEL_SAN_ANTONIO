package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Comprobante {
    private int idComprobante;
    private Integer idReserva; // puede ser null (venta de tiendita sola)
    private int idUsuario;
    private String tipo;   // BOLETA, NOTA_VENTA, FACTURA
    private String numero;
    private LocalDateTime fechaEmision;
    private BigDecimal montoTotal;

    public Comprobante() {}

    public int getIdComprobante() { return idComprobante; }
    public void setIdComprobante(int idComprobante) { this.idComprobante = idComprobante; }

    public Integer getIdReserva() { return idReserva; }
    public void setIdReserva(Integer idReserva) { this.idReserva = idReserva; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }
}
