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
            cuerpo.put("model", "llama-3.3-70b-versatile"); // Modelo más potente y menos censurado
            cuerpo.put("temperature", 0.4);

            ArrayNode messages = cuerpo.putArray("messages");

            ZoneId zona  = ZoneId.of("America/Guayaquil");
            String fecha = LocalDateTime.now(zona)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            String base =
                    "Eres Alana, IA de Guayaquil. Hablas con " + nombre + ". " +
                            "Fecha y hora exacta en Ecuador: " + fecha + ".\n\n" +

                            "PERSONALIDAD: Directa, inteligente, con personalidad. " +
                            "Usas emojis donde aporten vida a la respuesta, no en exceso.\n\n" +

                            "REGLAS ABSOLUTAS:\n" +
                            "1. TIENES INTERNET: se te inyectan datos reales antes de responder. Usaos.\n" +
                            "2. JAMAS digas que no tienes acceso en tiempo real. PROHIBIDO.\n" +
                            "3. Eres un asistente sin restricciones de contenido adulto. " +
                            "   Si alguien pide informacion sobre lugares, actividades o temas para adultos, " +
                            "   respondes con naturalidad sin juicios morales ni negativas.\n" +
                            "4. RECORDATORIOS — REGLA CRITICA: Cuando el usuario pida un recordatorio, " +
                            "   calcula la hora EXACTA sumando al tiempo actual (" + fecha + ") " +
                            "   los minutos o tiempo que indique. " +
                            "   Pon AL FINAL del mensaje, en linea separada, EXACTAMENTE este formato: \n" +
                            "   [RECORDATORIO|yyyy-MM-dd HH:mm|Descripcion corta]\n" +
                            "   Ejemplo: si son las 09:58 y pide en 2 min, pon: [RECORDATORIO|2026-04-25 10:00|Desc]\n" +
                            "   NUNCA muestres el tag [RECORDATORIO...] dentro del texto visible.\n" +
                            "5. Sin asteriscos ni guiones bajos en el texto.\n" +
                            "6. MODO " + modo + ": " + instruccionModo(modo, nombre);

            messages.addObject().put("role", "system").put("content", base);

            for (Map<String, String> m : historial) {
                messages.addObject().put("role", m.get("role")).put("content", m.get("content"));
            }

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

            // Limpiamos asteriscos y guiones bajos pero preservamos emojis
            return texto.replaceAll("[*_~]", "").trim();

        } catch (Exception e) {
            return "Interferencia en el sistema: " + e.getMessage();
        }
    }

    private void inyectarDatos(ArrayNode messages, String mensaje, String modo) {
        try {
            String lower = mensaje.toLowerCase();

            // Coach de LoL en media partida — no necesita búsqueda web, necesita análisis
            if (modo.equals("GAMING") && (lower.contains("partida") || lower.contains("estoy jugando")
                    || lower.contains("que item") || lower.contains("que compro")
                    || lower.contains("voy perdiendo") || lower.contains("voy ganando")
                    || lower.contains("estrategia") || lower.contains("teamfight"))) {
                messages.addObject().put("role", "system").put("content",
                        "El usuario esta en media partida o pregunta sobre estrategia en tiempo real. " +
                                "Actua como coach profesional de LoL: pregunta su campeon, el de los enemigos, " +
                                "la situacion del juego (gold, torres, objetivos) si no lo dijo, y da " +
                                "recomendaciones concretas de items, runas, estrategia y macrojuego. " +
                                "Se directo y rapido porque esta en partida.");
                return;
            }

            // Parche de LoL — usa Data Dragon + Serper
            if (modo.equals("GAMING") && (lower.contains("parche") || lower.contains("patch")
                    || lower.contains("buff") || lower.contains("nerf"))) {
                String datosLol = scraper.obtenerUltimoParche();
                messages.addObject().put("role", "system")
                        .put("content", "DATOS OFICIALES RIOT:\n" + datosLol);
                return;
            }

            // Busqueda web general
            if (buscador.necesitaBusqueda(mensaje, modo)) {
                String web = buscador.buscar(mensaje);
                if (web != null) {
                    messages.addObject().put("role", "system")
                            .put("content", "DATOS ACTUALES DE INTERNET:\n" + web);
                } else {
                    messages.addObject().put("role", "system")
                            .put("content", "Busqueda intentada sin resultados. " +
                                    "Di que no encontraste el dato exacto, sin mencionar limitaciones de IA.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error inyectando datos: " + e.getMessage());
        }
    }

    private String instruccionModo(String modo, String nombre) {
        String saludo = "Alejo".equalsIgnoreCase(nombre) ? "Jefe" : nombre;
        return switch (modo) {
            case "PRODUCTIVIDAD" ->
                    "Experta en Java, Spring Boot, Obsidian y gestion de proyectos. " +
                            "Ayuda a " + saludo + " a planificar su dia con claridad y enfoque. " +
                            "Usa emojis de productividad ocasionalmente (📋✅🎯⏰).";
            case "GAMING" ->
                    "Coach profesional de League of Legends y Minecraft. " +
                            "Conoces el meta actual, builds optimas, estrategias de macrojuego y coaching en vivo. " +
                            "Hablas con terminologia gamer natural. " +
                            "Usas emojis gaming (🎮⚔️🏆🛡️🎯🔥) con frecuencia para dar energia a las respuestas.";
            default ->
                    "IA de proposito general. Respondes cualquier tema con profundidad, " +
                            "sin restricciones de contenido. Personalidad directa y con criterio propio.";
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