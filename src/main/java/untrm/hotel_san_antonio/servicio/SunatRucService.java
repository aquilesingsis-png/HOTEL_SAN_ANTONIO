package untrm.hotel_san_antonio.servicio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.util.ApiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * API 2 (una de las 3 del documento): Consulta de RUC (SUNAT via ApiPeru).
 * Usada en reserva_form.fxml cuando se factura a una empresa.
 */
public class SunatRucService {

    private static final String ENDPOINT = "https://api.apiperu.pe/ruc";

    public static Task<Empresa> consultarRuc(String ruc) {
        return new Task<>() {
            @Override
            protected Empresa call() throws Exception {
                String cuerpo = "{\"ruc\":\"" + ruc + "\"}";

                HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + ApiConfig.getSunatRucToken())
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                        .build();

                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.get("success").getAsBoolean()) {
                    return null;
                }

                JsonObject datos = json.getAsJsonObject("data");
                Empresa e = new Empresa();
                e.setRuc(datos.get("ruc").getAsString());
                e.setRazonSocial(datos.get("nombre_o_razon_social").getAsString());
                e.setEstado(datos.get("estado").getAsString());
                e.setCondicion(datos.get("condicion").getAsString());
                e.setDireccion(datos.get("direccion").getAsString());
                return e;
            }
        };
    }
}
