package untrm.hotel_san_antonio.modelo;

import java.time.LocalDateTime;

/** Un cambio hecho a una cuenta de usuario: qué pasó, a quién, cuándo y quién lo hizo. */
public record CambioUsuario(LocalDateTime fecha, String afectado, String accion, String detalle, String realizadoPor) { }
