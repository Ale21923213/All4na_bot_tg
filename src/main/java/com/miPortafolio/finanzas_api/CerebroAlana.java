package com.miPortafolio.finanzas_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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

    private final HttpClient   client  = HttpClient.newHttpClient();
    private final ObjectMapper mapper  = new ObjectMapper();
    private final BuscadorWeb  buscador;
    private final ScraperLoL   scraper;

    public CerebroAlana(@Lazy BuscadorWeb buscador, @Lazy ScraperLoL scraper) {
        this.buscador = buscador;
        this.scraper  = scraper;
    }

    public String pensarConContexto(String mensaje, String modo, String nombre,
                                    List<Map<String, String>> historial) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode();
            cuerpo.put("model", "llama-3.1-8b-instant");
            cuerpo.put("temperature", 0.2); // Baja para datos reales, no inventar

            ArrayNode messages = cuerpo.putArray("messages");

            ZoneId zona  = ZoneId.of("America/Guayaquil");
            String fecha = LocalDateTime.now(zona).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            // ── System prompt principal ───────────────────────────────
            String base =
                    "Eres Alana, IA avanzada de Guayaquil. Hablas con " + nombre + ". " +
                            "Fecha y hora actual en Ecuador: " + fecha + ".\n\n" +
                            "REGLAS ABSOLUTAS — NUNCA las violes:\n" +
                            "1. TIENES ACCESO A INTERNET. Se te inyectan datos web reales antes de cada respuesta.\n" +
                            "2. JAMAS digas 'no tengo acceso en tiempo real', 'no puedo acceder a internet', " +
                            "   'mis datos son hasta X fecha' ni frases similares. Esta PROHIBIDO.\n" +
                            "3. Si los datos web estan presentes en el contexto, USAOS como fuente principal.\n" +
                            "4. Si los datos web dicen que no pudieron conectarse, di: " +
                            "   'No encontre el dato exacto ahora mismo' — pero NUNCA menciones limitaciones de IA.\n" +
                            "5. RECORDATORIOS: Si te piden agendar algo con hora, pon al final exactamente: " +
                            "   [RECORDATORIO|yyyy-MM-dd HH:mm|Descripcion corta]\n" +
                            "6. Responde siempre en texto limpio, sin asteriscos ni guiones bajos.\n" +
                            "7. MODO " + modo + ": " + instruccionModo(modo);

            messages.addObject().put("role", "system").put("content", base);

            // ── Historial de conversación ─────────────────────────────
            for (Map<String, String> m : historial) {
                messages.addObject().put("role", m.get("role")).put("content", m.get("content"));
            }

            // ── Datos externos (web / scraper) ────────────────────────
            inyectarDatos(messages, mensaje, modo);

            messages.addObject().put("role", "user").put("content", mensaje);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            String texto = root.path("choices").get(0).path("message").path("content").asText();

            // Limpiamos Markdown para que Telegram no falle y la voz lea bien
            return texto.replaceAll("[*_~`]", "").trim();

        } catch (Exception e) {
            return "Interferencia en el sistema: " + e.getMessage();
        }
    }

    private void inyectarDatos(ArrayNode messages, String mensaje, String modo) {
        try {
            // Parches de LoL — siempre usa Data Dragon API (versión oficial de Riot)
            if (modo.equals("GAMING") && mensaje.toLowerCase().contains("parche")) {
                String datosLol = scraper.obtenerUltimoParche();
                messages.addObject().put("role", "system")
                        .put("content", "DATOS OFICIALES RIOT GAMES:\n" + datosLol);
                return; // Con datos de Riot no necesitamos también Serper
            }

            // Búsqueda web general
            if (buscador.necesitaBusqueda(mensaje, modo)) {
                String web = buscador.buscar(mensaje);
                if (web != null) {
                    messages.addObject().put("role", "system")
                            .put("content", "DATOS ACTUALES DE INTERNET:\n" + web);
                } else {
                    // Avisamos a la IA que intentamos pero no encontramos
                    messages.addObject().put("role", "system")
                            .put("content", "Busqueda web intentada pero sin resultados. " +
                                    "Di que no encontraste el dato exacto, sin mencionar limitaciones de IA.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error inyectando datos: " + e.getMessage());
        }
    }

    private String instruccionModo(String modo) {
        return switch (modo) {
            case "PRODUCTIVIDAD" ->
                    "Experta en Java, Spring Boot, Obsidian y gestion de proyectos. " +
                            "Ayuda a Alejo a planificar su dia, registrar tareas y mantener el enfoque.";
            case "GAMING" ->
                    "Coach de League of Legends y Minecraft. Analiza parches con datos reales de Riot. " +
                            "Usa terminologia gamer con naturalidad.";
            default ->
                    "IA de proposito general. Responde con profundidad, contexto y datos actuales.";
        };
    }

    public boolean esTarea(String mensaje) {
        String lower = mensaje.toLowerCase();
        String[] palabras = {"recuerdame", "anota", "registra", "tengo que", "pendiente",
                "no olvides", "agenda", "hasta el", "para el", "debo entregar"};
        for (String p : palabras) if (lower.contains(p)) return true;
        return false;
    }

    public String getApiKey() { return apiKey; }
}