package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VentaTienda {
    private int idVenta;
    private Integer idHuesped;      // puede ser null (cliente externo)
    private Integer idHabitacion;   // puede ser null (no se carga a ninguna habitacion)
    private int idUsuario;
    private Integer idComprobante;  // null mientras esta "pendiente" en la cuenta
    private String clienteExterno;
    private LocalDateTime fechaVenta;
    private BigDecimal total;

    public VentaTienda() {}

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public Integer getIdHuesped() { return idHuesped; }
    public void setIdHuesped(Integer idHuesped) { this.idHuesped = idHuesped; }

    public Integer getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(Integer idHabitacion) { this.idHabitacion = idHabitacion; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public Integer getIdComprobante() { return idComprobante; }
    public void setIdComprobante(Integer idComprobante) { this.idComprobante = idComprobante; }

    public String getClienteExterno() { return clienteExterno; }
    public void setClienteExterno(String clienteExterno) { this.clienteExterno = clienteExterno; }

    public LocalDateTime getFechaVenta() { return fechaVenta; }
    public void setFechaVenta(LocalDateTime fechaVenta) { this.fechaVenta = fechaVenta; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
