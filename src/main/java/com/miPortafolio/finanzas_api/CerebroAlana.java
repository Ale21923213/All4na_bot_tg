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

@Service
public class CerebroAlana {

    @Value("${groq.api.key}")
    private String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BuscadorWeb buscador;

    public CerebroAlana(BuscadorWeb buscador) { this.buscador = buscador; }

    public String pensar(String mensajeUsuario, String modo, String nombreUsuario) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode();
            cuerpo.put("model", "llama-3.1-8b-instant");
            cuerpo.put("temperature", 0.4);
            var messages = cuerpo.putArray("messages");

            ZoneId zona = ZoneId.of("America/Guayaquil");
            String fechaHoraActual = LocalDateTime.now(zona).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            // 1. REGLAS GLOBALES (Funcionan siempre)
            // Dentro de CerebroAlana.java -> método pensar()
            String base = "Eres Alana de Guayaquil. Hablas con " + nombreUsuario + " (Jefe).\n" +
                    "CONTEXTO CULTURAL (Ecuador): Sabes que un 'chongo' NO es comida, es un burdel. " +
                    "Si preguntan por eso, responde con humor o declina, pero no inventes que es un plato típico.\n" +
                    "CONOCIMIENTO GAMING: Estamos en el año 2026. Si no hay datos en el buscador, no inventes parches (como el 26.1).\n" +
                    "REGLAS:\n" +
                    "- RECORDATORIOS: [RECORDATORIO|yyyy-MM-dd HH:mm|Descripción] (Solo si te lo piden).\n" +
                    "- No uses Markdown (asteriscos ni guiones).";
            // 2. ESPECIALIZACIÓN POR MODO
            String systemPrompt = switch (modo) {
                case "PRODUCTIVIDAD" -> base + "Modo Pro: Experta en Java, Spring Boot y Obsidian.";
                case "GAMING" -> base + "Modo Gaming: Coach de LoL y Minecraft. USA EL BUSCADOR para parches y buffs.";
                default -> base + "Modo General: Asistente versátil.";
            };

            messages.addObject().put("role", "system").put("content", systemPrompt);

            // 3. BÚSQUEDA WEB (Si es necesaria)
            if (buscador.necesitaBusqueda(mensajeUsuario, modo)) {
                String contexto = buscador.buscar(mensajeUsuario);
                if (contexto != null) {
                    messages.addObject().put("role", "system").put("content", "DATOS ACTUALES:\n" + contexto);
                }
            }

            messages.addObject().put("role", "user").put("content", mensajeUsuario);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            String textoFinal = root.path("choices").get(0).path("message").path("content").asText();

            // Limpia formato visual pero protege la etiqueta de recordatorio
            return textoFinal.replace("*", "").replace("_", "").replace("`", "").trim();

        } catch (Exception e) { return "Interferencia, Jefe: " + e.getMessage(); }
    }
    public String getApiKey() { return apiKey; }
}