package untrm.hotel_san_antonio.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Carga los tokens de las APIs desde config.properties (en la raiz del
 * proyecto, junto a pom.xml). Ese archivo NO se sube a Git (esta en
 * .gitignore) — cada integrante debe copiar config.properties.example
 * como config.properties y pedir los tokens reales al equipo.
 */
public class ApiConfig {

    private static final Properties props = new Properties();
    private static boolean cargado = false;

    private static void cargarSiHaceFalta() {
        if (cargado) return;
        try (FileInputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException(
                "No se encontro config.properties en la raiz del proyecto. "
                + "Copia config.properties.example como config.properties y "
                + "pide los tokens reales al equipo.", e);
        }
        cargado = true;
    }

    public static String getReniecToken() {
        cargarSiHaceFalta();
        return props.getProperty("reniec.token");
    }

    public static String getSunatRucToken() {
        cargarSiHaceFalta();
        return props.getProperty("sunat.ruc.token");
    }

    public static String getDecolectaToken() {
        cargarSiHaceFalta();
        return props.getProperty("decolecta.token");
    }
}
