package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un renglón del libro de caja. tipo: INGRESO, EGRESO, CARGO (venta cargada a una habitación) o ANULADA
 * (venta anulada: se ve pero no suma). idOrigen es el número de la reserva, venta, movimiento o gasto.
 */
public record MovimientoLibro(LocalDateTime fecha, String tipo, String concepto, BigDecimal monto,
                              String metodo, String documento, String cliente, String responsable,
                              String origen, int idOrigen) { }
