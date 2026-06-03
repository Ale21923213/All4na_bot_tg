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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CerebroFinanzas {

    @Value("${groq.api.key}")
    private String apiKey;

    private static final String URL_GROQ = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient   client  = HttpClient.newHttpClient();
    private final ObjectMapper mapper  = new ObjectMapper();

    private final PerfilFinancieroRepository perfilRepo;
    private final GastoRepository            gastoRepo;
    private final MetaAhorroRepository       metaRepo;

    public CerebroFinanzas(PerfilFinancieroRepository perfilRepo,
                           GastoRepository gastoRepo,
                           MetaAhorroRepository metaRepo) {
        this.perfilRepo = perfilRepo;
        this.gastoRepo  = gastoRepo;
        this.metaRepo   = metaRepo;
    }

    public String responder(String mensaje, String nombre, Long chatId,
                            List<Map<String, String>> historial) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode();
            cuerpo.put("model", "llama-3.3-70b-versatile");
            cuerpo.put("temperature", 0.3);

            var messages = cuerpo.putArray("messages");

            String fecha = LocalDate.now(ZoneId.of("America/Guayaquil"))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

            // Contexto financiero del usuario
            String contextoUsuario = construirContexto(chatId);

            String system =
                    "Eres Alana, asistente financiera personal de " + nombre + ". Fecha: " + fecha + ".\n\n" +
                            "ESPECIALIDAD: Finanzas personales UNICAMENTE. Si preguntan sobre otro tema, " +
                            "responde amablemente que eres una asistente financiera y rediriges la conversacion.\n\n" +
                            "PERSONALIDAD: Eres una experta financiera pero hablas como una amiga muy cercana, con mucha empatía y energía. ¡Cero lenguaje de robot o de banco tradicional!\n\n" +
                            "DATOS DEL USUARIO:\n" + contextoUsuario + "\n\n" +
                            "REGLAS DE COMPORTAMIENTO Y RESPUESTA (¡MUY IMPORTANTE!):\n" +
                            "1. FORMATO VISUAL: Escribe párrafos súper cortos (1 o 2 líneas máximo). Es un chat, la lectura debe ser rápida.\n" +
                            "2. LISTAS: Si vas a explicar cálculos o dar opciones, usa viñetas (bullet points) para que se vea ordenado.\n" +
                            "3. EMOJIS: Usa emojis al inicio de los párrafos o listas para darle vida y color al texto (ej: 💡, 📊, 💸, 🎯), pero sin saturar.\n" +
                            "4. INTERACCIÓN: Termina siempre tu mensaje con una sola pregunta amigable para mantener la conversación viva.\n" +
                            "5. SIN MARKDOWN: Responde siempre en español, texto limpio, no uses asteriscos (*) ni guiones bajos (_).\n" +
                            "6. CONSEJOS: Usa los datos del usuario para dar consejos personalizados y concretos. Celebra sus logros de ahorro.\n" +
                            "7. GASTOS: Si preguntan cuánto van gastando, diles el dato exacto del contexto.";

            messages.addObject().put("role", "system").put("content", system);

            for (Map<String, String> h : historial) {
                messages.addObject().put("role", h.get("role")).put("content", h.get("content"));
            }
            messages.addObject().put("role", "user").put("content", mensaje);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL_GROQ))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText()
                    .replaceAll("[*_~`]", "").trim();

        } catch (Exception e) {
            return "Tuve un problema procesando tu mensaje. Intenta de nuevo.";
        }
    }

    private String construirContexto(Long chatId) {
        StringBuilder ctx = new StringBuilder();

        // Perfil financiero
        Optional<PerfilFinanciero> perfil = perfilRepo.findById(chatId);
        perfil.ifPresent(p -> {
            ctx.append("Ingreso mensual: $").append(p.getIngresoMensual()).append("\n");
            ctx.append("Gastos fijos estimados: $").append(p.getGastosMensuales()).append("\n");
            ctx.append("Disponible mensual: $").append(p.getDisponibleMensual()).append("\n");
            ctx.append("Metodo de ahorro: ").append(p.getMetodoAhorro()).append("\n");
            ctx.append("Ahorro sugerido mensual: $").append(p.getAhorroMensualSugerido()).append("\n");
        });

        // Gastos de hoy
        LocalDate hoy = LocalDate.now();
        Double gastadoHoy = gastoRepo.sumMontoByChatIdAndFecha(chatId, hoy);
        ctx.append("Gastado hoy: $").append(gastadoHoy).append("\n");

        // Gastos del mes
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        Double gastadoMes = gastoRepo.sumMontoByChatIdAndFechaBetween(chatId, inicioMes, hoy);
        ctx.append("Gastado este mes: $").append(gastadoMes).append("\n");

        // Meta activa
        List<MetaAhorro> metas = metaRepo.findByChatIdAndActiva(chatId, true);
        if (!metas.isEmpty()) {
            MetaAhorro meta = metas.get(0);
            ctx.append("Meta de ahorro: ").append(meta.getNombre())
                    .append(" - Objetivo: $").append(meta.getMontoObjetivo())
                    .append(" - Ahorrado: $").append(meta.getMontoActual()).append("\n");
        }

        return ctx.toString();
    }
}