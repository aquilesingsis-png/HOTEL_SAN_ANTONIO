package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;

/** Lo registrado para un día pasado: ventas del carrito y pagos de reservas. */
public record ResumenDia(int ventas, BigDecimal totalVentas, int pagos, BigDecimal totalPagos) { }
