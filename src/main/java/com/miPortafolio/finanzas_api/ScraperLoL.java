package com.miPortafolio.finanzas_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Obtiene las notas del último parche de LoL usando la API pública de Riot
 * y Serper como respaldo — sin scraping frágil de HTML.
 *
 * Riot expone un JSON con la versión actual en:
 *   https://ddragon.leagueoflegends.com/api/versions.json
 * Y las notas completas no tienen API pública, así que usamos Serper
 * para buscar las notas oficiales del parche actual.
 */
@Service
public class ScraperLoL {

    @Value("${serper.api.key}")
    private String serperApiKey;

    private final HttpClient   client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String obtenerUltimoParche() {
        try {
            // 1. Obtener la versión actual de LoL desde la API oficial de Riot
            String versionActual = obtenerVersionActual();

            // 2. Buscar las notas de ese parche exacto con Serper
            String query = "League of Legends patch " + versionActual + " notes changes "
                    + java.time.LocalDate.now().getYear();
            String cuerpo = "{\"q\":\"" + query + "\",\"num\":4,\"hl\":\"es\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://google.serper.dev/search"))
                    .header("X-API-KEY", serperApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            StringBuilder sb = new StringBuilder();
            sb.append("VERSION ACTUAL DE LOL: ").append(versionActual).append("\n");
            sb.append("Fecha: ").append(java.time.LocalDate.now()).append("\n\n");
            sb.append("NOTAS DEL PARCHE ").append(versionActual).append(":\n");

            // Answer box si lo hay
            JsonNode ab = root.path("answerBox");
            if (!ab.isMissingNode()) {
                String ans = ab.path("snippet").asText("");
                if (!ans.isBlank()) sb.append("Resumen: ").append(ans).append("\n\n");
            }

            // Resultados
            root.path("organic").forEach(node -> {
                String titulo  = node.path("title").asText("").trim();
                String snippet = node.path("snippet").asText("").trim();
                if (!snippet.isBlank()) {
                    sb.append("- ").append(titulo).append(": ").append(snippet).append("\n");
                }
            });

            return sb.toString();

        } catch (Exception e) {
            System.err.println("Error ScraperLoL: " + e.getMessage());
            // Fallback: al menos informamos la versión si podemos
            try {
                String version = obtenerVersionActual();
                return "Version actual de LoL: " + version + " (fecha: " + java.time.LocalDate.now()
                        + "). No pude cargar las notas completas del parche.";
            } catch (Exception ex) {
                return "No pude conectar con los servidores de Riot ni con el buscador.";
            }
        }
    }

    /**
     * Llama a la Data Dragon API de Riot — siempre tiene la versión más reciente.
     * Ejemplo de respuesta: ["15.8.1","15.7.1","15.6.1",...]
     */
    private String obtenerVersionActual() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://ddragon.leagueoflegends.com/api/versions.json"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode versions = mapper.readTree(resp.body());
        // El primer elemento es siempre la versión más reciente
        return versions.get(0).asText();
    }
}