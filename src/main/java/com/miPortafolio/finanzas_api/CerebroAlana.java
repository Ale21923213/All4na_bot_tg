package com.miPortafolio.finanzas_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CerebroAlana {

    @Value("${groq.api.key}")
    private String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BuscadorWeb buscador;
    private final ScraperLoL scraper;

    public CerebroAlana(BuscadorWeb buscador, ScraperLoL scraper) {
        this.buscador = buscador;
        this.scraper = scraper;
    }

    public String pensarConContexto(String mensaje, String modo, String nombre, List<Map<String, String>> historial) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode();
            cuerpo.put("model", "llama-3.1-8b-instant");
            cuerpo.put("temperature", 0.0); // 🎯 Máxima precisión
            var messages = cuerpo.putArray("messages");

            ZoneId zona = ZoneId.of("America/Guayaquil");
            String fecha = LocalDateTime.now(zona).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            // SYSTEM PROMPT DE ÉLITE
            String base = "Eres Alana de Guayaquil, 2026. Hablas con " + nombre + ".\n" +
                    "REGLAS ABSOLUTAS:\n" +
                    "1. TIENES INTERNET: Tienes acceso a Google y Scrapers. Prohibido decir 'no tengo acceso en tiempo real'.\n" +
                    "2. SI NO ENCUENTRAS ALGO: Di 'No hallé el dato exacto en internet', pero nunca digas que no tienes conexión.\n" +
                    "3. RECORDATORIOS: Si te piden agendar algo, DEBES poner al final: [RECORDATORIO|yyyy-MM-dd HH:mm|Descripción].\n" +
                    "4. MODO " + modo + ": " + obtenerInstruccionModo(modo);

            messages.addObject().put("role", "system").put("content", base);

            // AGREGAMOS EL HISTORIAL (Memoria de la conversación)
            for (Map<String, String> m : historial) {
                messages.addObject().put("role", m.get("role")).put("content", m.get("content"));
            }

            // BÚSQUEDA WEB Y SCRAPING
            inyectarDatosExternos(messages, mensaje, modo);

            messages.addObject().put("role", "user").put("content", mensaje);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            String texto = root.path("choices").get(0).path("message").path("content").asText();

            return texto.replace("*", "").replace("_", "").replace("`", "").trim();

        } catch (Exception e) { return "Interferencia, Jefe: " + e.getMessage(); }
    }

    private void inyectarDatosExternos(com.fasterxml.jackson.databind.node.ArrayNode messages, String mensaje, String modo) {
        if (modo.equals("GAMING") && mensaje.toLowerCase().contains("parche")) {
            String lol = scraper.obtenerUltimoParche();
            messages.addObject().put("role", "system").put("content", "DATOS OFICIALES RIOT: " + lol);
        } else if (buscador.necesitaBusqueda(mensaje, modo)) {
            String web = buscador.buscar(mensaje);
            if (web != null) messages.addObject().put("role", "system").put("content", "DATOS WEB: " + web);
        }
    }

    private String obtenerInstruccionModo(String modo) {
        return switch (modo) {
            case "PRODUCTIVIDAD" -> "Experta en Java, Obsidian y gestión de proyectos.";
            case "GAMING" -> "Coach de LoL y Minecraft. Analiza parches con datos reales.";
            default -> "IA avanzada tipo Gemini. Responde con profundidad y contexto.";
        };
    }

    public String getApiKey() { return apiKey; }
}