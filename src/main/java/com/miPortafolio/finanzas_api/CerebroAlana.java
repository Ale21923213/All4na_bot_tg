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

    private static final String URL_GROQ = "https://api.groq.com/openai/v1/chat/completions";
    private final HttpClient    client    = HttpClient.newHttpClient();
    private final ObjectMapper  mapper    = new ObjectMapper();
    private final BuscadorWeb   buscador;

    public CerebroAlana(BuscadorWeb buscador) { this.buscador = buscador; }

    public String pensar(String mensajeUsuario, String modo, String nombreUsuario) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode();
            cuerpo.put("model", "llama-3.1-8b-instant");
            cuerpo.put("temperature", 0.3);
            var messages = cuerpo.putArray("messages");

            // 🚀 HORA EXACTA DE ECUADOR
            ZoneId zona = ZoneId.of("America/Guayaquil");
            LocalDateTime ahora = LocalDateTime.now(zona);
            String fechaHoraActual = ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            String identidad = "Hablas con " + nombreUsuario + ". Si es Alejo, trátalo como 'Jefe'. Si es su novia, sé muy amable.";

            String base = "Eres Alana de Guayaquil. " + identidad + " La fecha y hora actual exacta es: " + fechaHoraActual + ".\n" +
                    "REGLAS ABSOLUTAS E INQUEBRANTABLES:\n" +
                    "1. CERO MARKDOWN: Escribe en texto 100% plano sin asteriscos ni guiones bajos.\n" +
                    "2. ANTI-ALUCINACIÓN: Si el contexto web no tiene datos exactos, di la verdad y no inventes.\n" +
                    "3. SISTEMA DE RECORDATORIOS (MUY IMPORTANTE): Si el usuario te pide que le recuerdes algo o agendes una tarea, calcula la fecha y hora exacta basándote en la hora actual ("+fechaHoraActual+"). Al final de tu respuesta, DEBES incluir obligatoriamente esta etiqueta oculta con el formato exacto: [RECORDATORIO|yyyy-MM-dd HH:mm|Descripción corta de la tarea].\n" +
                    "Ejemplo si pide 'recuérdame apagar el horno en 15 minutos': 'Entendido Jefe, le avisaré. [RECORDATORIO|2026-04-23 11:45|Apagar el horno]'\n";

            String systemPrompt = switch (modo) {
                case "PRODUCTIVIDAD" -> base + "\nModo Pro: Especialista en Obsidian, Rifa Solidaria y finanzas.";
                case "GAMING" -> base + "\nModo Gaming: Coach de LoL y Minecraft 1.21.11.";
                default -> base + "\nModo General.";
            };

            messages.addObject().put("role", "system").put("content", systemPrompt);

            if (buscador.necesitaBusqueda(mensajeUsuario, modo)) {
                String contexto = buscador.buscar(mensajeUsuario);
                if (contexto != null) {
                    messages.addObject().put("role", "system").put("content", "Contexto web actualizado:\n" + contexto);
                }
            }

            messages.addObject().put("role", "user").put("content", mensajeUsuario);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL_GROQ))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            String textoFinal = root.path("choices").get(0).path("message").path("content").asText();
            return textoFinal.replaceAll("[*_`\\[\\]#](?![RECORDATORIO])", ""); // Limpiamos formato pero dejamos la etiqueta a salvo

        } catch (Exception e) {
            return "Interferencia, Jefe: " + e.getMessage();
        }
    }

    // Ya no necesitamos esTarea() porque la etiqueta secreta hará el trabajo
    public String getApiKey() { return apiKey; }
}