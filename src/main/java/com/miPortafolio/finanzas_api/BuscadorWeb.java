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

    private static final String URL_SERPER = "https://google.serper.dev/search";

    private final HttpClient   client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String buscar(String query) {
        try {
            // 🚀 TRUCO DE BÚSQUEDA FRESCA: Forzamos a Google a buscar cosas del año actual
            int anioActual = java.time.LocalDate.now().getYear();
            String busquedaOptimizada = query + " " + anioActual;

            String cuerpo = "{\"q\":\"" + escaparJson(busquedaOptimizada) + "\",\"num\":5,\"hl\":\"es\",\"gl\":\"ec\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL_SERPER))
                    .header("X-API-KEY", serperApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Serper error: " + response.statusCode() + " - " + response.body());
                return null;
            }

            JsonNode root = mapper.readTree(response.body());

            StringBuilder contexto = new StringBuilder();
            contexto.append("Información actual de internet (").append(java.time.LocalDate.now()).append("):\n\n");

            JsonNode organicos = root.path("organic");
            if (organicos.isArray()) {
                for (int i = 0; i < Math.min(4, organicos.size()); i++) {
                    JsonNode r = organicos.get(i);
                    String titulo  = r.path("title").asText("");
                    String snippet = r.path("snippet").asText("");
                    if (!snippet.isBlank()) {
                        contexto.append("• ").append(titulo).append(": ").append(snippet).append("\n");
                    }
                }
            }

            JsonNode answerBox = root.path("answerBox");
            if (!answerBox.isMissingNode()) {
                String answer = answerBox.path("answer").asText("");
                if (answer.isBlank()) answer = answerBox.path("snippet").asText("");
                if (!answer.isBlank()) {
                    contexto.insert(contexto.indexOf("\n\n") + 2, "Respuesta directa: " + answer + "\n");
                }
            }

            String resultado = contexto.toString().trim();

            // 🚀 CONSOLA DEBUG: Así puedes espiar lo que Serper le envía a Alana
            if (resultado.length() > 50) {
                System.out.println("\n--- [MODULO SERPER] DATOS ENCONTRADOS ---");
                System.out.println(resultado);
                System.out.println("-----------------------------------------\n");
                return resultado;
            } else {
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error en BuscadorWeb: " + e.getMessage());
            return null;
        }
    }

    public boolean necesitaBusqueda(String mensaje, String modo) {
        String lower = mensaje.toLowerCase();

        String[] señalesGenerales = {
                "clima", "tiempo", "temperatura", "lluvia", "noticias", "hoy",
                "ahora", "actualmente", "precio", "dólar", "cotización",
                "quién ganó", "resultado", "último", "reciente", "esta semana",
                "este mes", "en vivo", "directo", "trending", "viral",
                "cuánto cuesta", "qué pasó", "novedades", "parche"
        };

        for (String s : señalesGenerales) {
            if (lower.contains(s)) return true;
        }

        return false;
    }

    private String escaparJson(String texto) {
        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}