package untrm.hotel_san_antonio.servicio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import untrm.hotel_san_antonio.modelo.Huesped;
import untrm.hotel_san_antonio.util.ApiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import untrm.hotel_san_antonio.util.Validador;

/**
 * API 1: Consulta de DNI (RENIEC via ApiPeru).
 * Se ejecuta en segundo plano (Task) para no congelar la interfaz.
 */
public class ReniecService {

    private static final String ENDPOINT = "https://api.apiperu.pe/dni";
    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** Devuelve un Huesped con nombres/apellidos rellenados, o null si no se encontro. */
    public static Task<Huesped> consultarDni(String dni) {
        return new Task<>() {
            @Override
            protected Huesped call() throws Exception {
                if (!Validador.esDniValido(dni)) {
                    throw new IllegalArgumentException("DNI inválido.");
                }
                String token = ApiConfig.getReniecToken();
                if (token == null || token.isBlank() || token.startsWith("PEGAR_")) {
                    throw new IllegalStateException("Configure reniec.token para consultar ApiPeru.");
                }
                String cuerpo = "{\"dni\":\"" + dni + "\"}";

                HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                        .build();

                HttpResponse<String> response = CLIENTE.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) return null;
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("El proveedor de identidad no está disponible.");
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    return null;
                }

                JsonObject datos = json.getAsJsonObject("data");
                if (datos == null || !datos.has("numero") || !datos.has("nombres")
                        || !datos.has("apellido_paterno") || !datos.has("apellido_materno")
                        || !dni.equals(datos.get("numero").getAsString())) {
                    throw new IllegalStateException("El proveedor devolvió una identidad inválida.");
                }
                Huesped h = new Huesped();
                h.setTipoDocumento("DNI");
                h.setNumDocumento(datos.get("numero").getAsString());
                h.setNombres(datos.get("nombres").getAsString());
                h.setApellidos(datos.get("apellido_paterno").getAsString() + " " + datos.get("apellido_materno").getAsString());
                h.setPaisProcedencia("Perú");
                if (!Validador.esNombreValido(h.getNombres()) || !Validador.esNombreValido(h.getApellidos())) {
                    throw new IllegalStateException("El proveedor devolvió nombres inválidos.");
                }
                return h;
            }
        };
    }
}
