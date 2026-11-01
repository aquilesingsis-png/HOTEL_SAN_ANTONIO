package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;

/** Ingresos, egresos y efectivo en caja (ingresos menos egresos en efectivo) de todo lo que cumple los filtros. */
public record TotalesCaja(BigDecimal ingresos, BigDecimal egresos, BigDecimal efectivo) { }
