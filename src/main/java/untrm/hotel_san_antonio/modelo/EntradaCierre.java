package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;

/** Lo que muestra la pantalla al cerrar: los importes que vio quien cierra y lo que contó. */
public record EntradaCierre(String turno, BigDecimal fondoInicial, BigDecimal cobros, BigDecimal otros,
                            BigDecimal salidas, BigDecimal contado, BigDecimal fondoSiguiente, String observacion) { }
