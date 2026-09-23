package untrm.hotel_san_antonio.servicio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import untrm.hotel_san_antonio.modelo.TipoCambio;
import untrm.hotel_san_antonio.util.ApiConfig;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * API 3 (una de las 3 del documento): Tipo de cambio SUNAT via Decolecta.
 * Se muestra como widget informativo en el Dashboard.
 */
public class DecolectaTipoCambioService {

    private static final String ENDPOINT = "https://api.decolecta.com/v1/tipo-cambio/sunat";

    public static Task<TipoCambio> consultarHoy() {
        return new Task<>() {
            @Override
            protected TipoCambio call() throws Exception {
                HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + ApiConfig.getDecolectaToken())
                        .GET()
                        .build();

                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                TipoCambio tc = new TipoCambio();
                tc.setCompra(new BigDecimal(json.get("buy_price").getAsString()));
                tc.setVenta(new BigDecimal(json.get("sell_price").getAsString()));
                tc.setMonedaBase(json.get("base_currency").getAsString());
                tc.setMonedaDestino(json.get("quote_currency").getAsString());
                tc.setFecha(json.get("date").getAsString());
                return tc;
            }
        };
    }
}
