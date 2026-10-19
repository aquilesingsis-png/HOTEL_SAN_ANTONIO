package untrm.hotel_san_antonio.servicio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import untrm.hotel_san_antonio.modelo.Empresa;
import untrm.hotel_san_antonio.util.ApiConfig;
import untrm.hotel_san_antonio.util.Validador;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * API 2 (una de las 3 del documento): Consulta de RUC (SUNAT via ApiPeru).
 * Usada en reserva_form.fxml cuando se factura a una empresa.
 * Se ejecuta en segundo plano (Task) para no congelar la interfaz.
 */
public class SunatRucService {

    private static final String ENDPOINT = "https://api.apiperu.pe/ruc";
    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** Devuelve la empresa, o null si el RUC no existe. */
    public static Task<Empresa> consultarRuc(String ruc) {
        return new Task<>() {
            @Override
            protected Empresa call() throws Exception {
                if (!Validador.esRucValido(ruc)) {
                    throw new IllegalArgumentException("RUC inválido.");
                }
                String token = ApiConfig.getSunatRucToken();
                if (token == null || token.isBlank() || token.startsWith("PEGAR_")) {
                    throw new IllegalStateException("Configure sunat.ruc.token para consultar ApiPeru.");
                }
                JsonObject cuerpo = new JsonObject();
                cuerpo.addProperty("ruc", ruc);

                HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                        .build();

                HttpResponse<String> response = CLIENTE.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    return null;
                }
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("El servicio de la SUNAT no está disponible.");
                }

                JsonObject json;
                try {
                    json = JsonParser.parseString(response.body()).getAsJsonObject();
                } catch (JsonParseException | IllegalStateException error) {
                    throw new IllegalStateException("La SUNAT devolvió una respuesta que no se entiende.", error);
                }
                if (!json.has("success") || !json.get("success").getAsBoolean()
                        || !json.has("data") || !json.get("data").isJsonObject()) {
                    return null;
                }

                JsonObject datos = json.getAsJsonObject("data");
                String rucDevuelto = texto(datos, "ruc");
                String razonSocial = texto(datos, "nombre_o_razon_social");
                if (!ruc.equals(rucDevuelto) || razonSocial == null || razonSocial.isBlank()) {
                    throw new IllegalStateException("La SUNAT devolvió datos de empresa inválidos.");
                }
                Empresa e = new Empresa();
                e.setRuc(rucDevuelto);
                e.setRazonSocial(razonSocial);
                e.setEstado(texto(datos, "estado"));
                e.setCondicion(texto(datos, "condicion"));
                e.setDireccion(texto(datos, "direccion"));
                return e;
            }
        };
    }

    /** El texto de un campo, o null si falta o viene vacío (la API a veces manda null). */
    private static String texto(JsonObject datos, String campo) {
        if (!datos.has(campo) || datos.get(campo).isJsonNull() || !datos.get(campo).isJsonPrimitive()) {
            return null;
        }
        return datos.get(campo).getAsString();
    }
}
