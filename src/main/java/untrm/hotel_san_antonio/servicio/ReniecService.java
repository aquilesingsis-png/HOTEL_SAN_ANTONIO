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

/**
 * API 1: Consulta de DNI (RENIEC via ApiPeru).
 * Se ejecuta en segundo plano (Task) para no congelar la interfaz.
 */
public class ReniecService {

    private static final String ENDPOINT = "https://api.apiperu.pe/dni";

    /** Devuelve un Huesped con nombres/apellidos rellenados, o null si no se encontro. */
    public static Task<Huesped> consultarDni(String dni) {
        return new Task<>() {
            @Override
            protected Huesped call() throws Exception {
                String cuerpo = "{\"dni\":\"" + dni + "\"}";

                HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + ApiConfig.getReniecToken())
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                        .build();

                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.get("success").getAsBoolean()) {
                    return null;
                }

                JsonObject datos = json.getAsJsonObject("data");
                Huesped h = new Huesped();
                h.setTipoDocumento("DNI");
                h.setNumDocumento(datos.get("numero").getAsString());
                h.setNombres(datos.get("nombres").getAsString());
                h.setApellidos(datos.get("apellido_paterno").getAsString() + " " + datos.get("apellido_materno").getAsString());
                return h;
            }
        };
    }
}
