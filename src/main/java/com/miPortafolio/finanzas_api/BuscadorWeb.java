package com.miPortafolio.finanzas_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class BuscadorWeb {

    @Value("${serper.api.key}")
    private String serperApiKey;

    private final HttpClient   client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String buscar(String query) {
        try {
            // Añadimos el año para forzar resultados recientes
            String q = query + " " + java.time.LocalDate.now().getYear();
            String cuerpo = "{\"q\":\"" + escapar(q) + "\",\"num\":5,\"hl\":\"es\",\"gl\":\"ec\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://google.serper.dev/search"))
                    .header("X-API-KEY", serperApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Serper error " + response.statusCode());
                return null;
            }

            JsonNode root = mapper.readTree(response.body());
            StringBuilder sb = new StringBuilder("Fecha actual: ")
                    .append(java.time.LocalDate.now()).append("\n");

            // Answer box — respuesta directa de Google (ideal para clima y datos puntuales)
            JsonNode ab = root.path("answerBox");
            if (!ab.isMissingNode()) {
                String ans = ab.path("answer").asText("");
                if (ans.isBlank()) ans = ab.path("snippet").asText("");
                if (!ans.isBlank()) sb.append("Respuesta directa: ").append(ans).append("\n");
            }

            // Knowledge graph
            JsonNode kg = root.path("knowledgeGraph");
            if (!kg.isMissingNode()) {
                String desc = kg.path("description").asText("");
                if (!desc.isBlank()) sb.append("Dato clave: ").append(desc).append("\n");
            }

            // Resultados orgánicos
            root.path("organic").forEach(node -> {
                String snippet = node.path("snippet").asText("").trim();
                if (!snippet.isBlank()) sb.append("- ").append(snippet).append("\n");
            });

            String resultado = sb.toString().trim();
            return resultado.length() > 30 ? resultado : null;

        } catch (Exception e) {
            System.err.println("Error BuscadorWeb: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lista ampliada — cubre clima, noticias, precios, gaming y más.
     * Si la IA no tiene dato reciente, es mejor buscar que no buscar.
     */
    public boolean necesitaBusqueda(String mensaje, String modo) {
        String m = mensaje.toLowerCase();

        // ── Señales universales ───────────────────────────────────────
        String[] universales = {
                // Clima y tiempo
                "clima", "tiempo", "temperatura", "lluvia", "calor", "frio",
                "pronostico", "humedad", "viento",
                // Noticias y actualidad
                "noticias", "que paso", "que paso con", "ultimo", "reciente",
                "hoy", "esta semana", "este mes", "ahora", "actualmente",
                // Precios y finanzas
                "precio", "dolar", "cotizacion", "cuanto cuesta", "valor",
                "inflacion", "economia",
                // Personas y entidades
                "quien es", "quienes son", "donde esta", "cuando fue",
                // Resultados y eventos
                "resultado", "gano", "perdio", "campeon", "final",
                // Tecnología
                "lanzamiento", "nuevo", "actualiza", "version", "estrena"
        };

        for (String s : universales) {
            if (m.contains(s)) return true;
        }

        // ── Señales específicas de GAMING ─────────────────────────────
        if (modo.equals("GAMING")) {
            String[] gaming = {
                    "parche", "patch", "buff", "nerf", "meta", "tier",
                    "campeón", "champion", "actualizacion", "update", "lol",
                    "league", "minecraft", "skin", "temporada", "season",
                    "torneo", "worlds", "ranked", "build", "runa"
            };
            for (String s : gaming) {
                if (m.contains(s)) return true;
            }
        }

        return false;
    }

    private String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", "");
    }
}