package untrm.hotel_san_antonio.util;

import java.util.Set;
import untrm.hotel_san_antonio.modelo.Usuario;

/** Autorización central usada por navegación y operaciones sensibles. */
public final class Permisos {
    private static final String BASE = "/untrm/hotel_san_antonio/fxml/";
    private static final Set<String> RECEPCION = Set.of(
            BASE + "dashboard/dashboard_2.fxml",
            BASE + "habitaciones/habitaciones.fxml",
            BASE + "habitaciones/reserva_form.fxml",
            BASE + "habitaciones/cuenta_habitacion.fxml",
            BASE + "habitaciones/piso_seccion.fxml",
            BASE + "habitaciones/tarjeta_habitacion.fxml",
            BASE + "carrito/carrito_tienda.fxml",
            BASE + "comprobante/comprobante.fxml",
            BASE + "nuevos/caja/registrar_pagos.fxml");

    private Permisos() {}

    public static void requerir(String... roles) {
        Usuario actual = SesionActual.getUsuario();
        if (actual == null || !actual.isActivo()) throw new SecurityException("Inicie sesión nuevamente.");
        for (String rol : roles) {
            if (rol.equals(actual.getRol())) return;
        }
        throw new SecurityException("Su rol no tiene permiso para esta operación.");
    }

    public static void requerirRuta(String ruta) {
        if (BASE.concat("login/login.fxml").equals(ruta)
                || BASE.concat("login/recuperacion.fxml").equals(ruta)) return;
        Usuario actual = SesionActual.getUsuario();
        if (actual == null || !actual.isActivo()) throw new SecurityException("Inicie sesión nuevamente.");
        String rol = actual.getRol();
        if ("ADMINISTRADOR".equals(rol)) return;
        if ("RECEPCIONISTA".equals(rol) && (RECEPCION.contains(ruta)
                || (ruta.startsWith(BASE + "Reserva/")
                    && !ruta.equals(BASE + "Reserva/registro_historico.fxml"))
                || ruta.equals(BASE + "principal/principal_recepcionista.fxml"))) return;
        if ("LIMPIEZA".equals(rol) && (ruta.equals(BASE + "principal/principal_limpieza.fxml")
                || ruta.equals(BASE + "habitaciones/limpieza.fxml"))) return;
        throw new SecurityException("Su rol no puede abrir esta pantalla.");
    }
}
