package com.miPortafolio.finanzas_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class BuscadorWeb {

    @Value("${serper.api.key}")
    private String serperApiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String buscar(String query) {
        try {
            String q = query + " " + java.time.LocalDate.now().getYear();
            String cuerpo = "{\"q\":\"" + q + "\",\"num\":4,\"hl\":\"es\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://google.serper.dev/search"))
                    .header("X-API-KEY", serperApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            StringBuilder sb = new StringBuilder();
            root.path("organic").forEach(node -> sb.append("- ").append(node.path("snippet").asText()).append("\n"));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public boolean necesitaBusqueda(String mensaje, String modo) {
        String m = mensaje.toLowerCase();
        // Universales
        if (m.contains("noticias") || m.contains("precio") || m.contains("dolar") || m.contains("quien es")) return true;
        // Gaming específico
        if (modo.equals("GAMING") && (m.contains("buff") || m.contains("nerf") || m.contains("parche") || m.contains("cambio"))) return true;
        return false;
    }
}