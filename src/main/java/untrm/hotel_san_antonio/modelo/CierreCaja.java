package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un cierre de caja ya guardado (borrador o confirmado); confirmado es la hora en que se confirmó. */
public record CierreCaja(int id, LocalDate fecha, String turno, String responsable, BigDecimal esperado,
                         BigDecimal contado, BigDecimal diferencia, String estado, LocalDateTime confirmado) { }
