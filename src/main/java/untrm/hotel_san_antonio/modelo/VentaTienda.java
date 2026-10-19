package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class VentaTienda {
    private int idVenta;
    private Integer idHuesped;      // puede ser null (cliente externo)
    private Integer idHabitacion;   // puede ser null (no se carga a ninguna habitacion)
    private int idUsuario;
    private Integer idComprobante;  // null mientras esta "pendiente" en la cuenta
    private String clienteExterno;
    private String metodoPago;      // EFECTIVO, YAPE, TRANSFERENCIA o TARJETA; null si se carga a una habitacion
    private LocalDateTime fechaVenta;
    private LocalDate fechaEvento;  // solo si se registro tarde: el dia en que de verdad ocurrio
    private BigDecimal total;
    private String estado = "ACTIVA"; // ACTIVA o ANULADA

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

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public LocalDateTime getFechaVenta() { return fechaVenta; }
    public void setFechaVenta(LocalDateTime fechaVenta) { this.fechaVenta = fechaVenta; }

    public LocalDate getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDate fechaEvento) { this.fechaEvento = fechaEvento; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public boolean isAnulada() { return "ANULADA".equals(estado); }
}
