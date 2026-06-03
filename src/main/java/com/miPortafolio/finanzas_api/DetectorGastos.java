package com.miPortafolio.finanzas_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DetectorGastos {

    @Value("${groq.api.key}")
    private String apiKey;

    private final HttpClient   client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Palabras que indican que hubo un gasto
    private static final String[] VERBOS_GASTO = {
            "gaste", "gasté", "pague", "pagué", "compre", "compré",
            "almorce", "almorcé", "desayune", "desayuné", "cene", "cené",
            "costo", "costó", "me salio", "me salió", "me costó", "me costo",
            "se me fueron", "gaste en", "invierte en", "pague por"
    };

    private static final Pattern MONTO_PATTERN = Pattern.compile(
            "\\$?\\s*(\\d+(?:[.,]\\d{1,2})?)\\s*(?:dólares?|dolares?|dls?|usd)?",
            Pattern.CASE_INSENSITIVE
    );

    public record ResultadoDeteccion(boolean esGasto, double monto, String categoria, String descripcion) {}

    /**
     * Intenta detectar un gasto en el mensaje.
     * Primero usa regex, luego Groq si no es suficiente.
     */
    public ResultadoDeteccion detectar(String mensaje) {
        String lower = mensaje.toLowerCase().trim();

        // 1. Verificar que hay un verbo de gasto
        boolean tieneVerbo = false;
        for (String verbo : VERBOS_GASTO) {
            if (lower.contains(verbo)) { tieneVerbo = true; break; }
        }

        // 2. Verificar que hay un monto numérico
        Matcher m = MONTO_PATTERN.matcher(lower);
        boolean tieneMonto = m.find();

        if (!tieneVerbo || !tieneMonto) {
            return new ResultadoDeteccion(false, 0, null, null);
        }

        // 3. Extraer el monto
        double monto;
        try {
            String montoStr = m.group(1).replace(",", ".");
            monto = Double.parseDouble(montoStr);
        } catch (NumberFormatException e) {
            return new ResultadoDeteccion(false, 0, null, null);
        }

        // 4. Detectar categoría por keywords
        String categoria    = detectarCategoria(lower);
        String descripcion  = limpiarDescripcion(mensaje, monto);

        return new ResultadoDeteccion(true, monto, categoria, descripcion);
    }

    private String detectarCategoria(String texto) {
        if (texto.matches(".*(bus|metro|taxi|uber|pasaje|gasolina|combustible|moto|parqueo|parqueadero|transporte).*"))
            return "TRANSPORTE";
        if (texto.matches(".*(almuerzo|almorce|almorcé|desayuno|cena|comida|restaurante|tienda|super|mercado|bazar|lunch|snack|merienda).*"))
            return "ALIMENTACION";
        if (texto.matches(".*(cine|fiesta|trago|tragos|discoteca|bares?|netflix|spotify|entretenimiento|juego|gaming).*"))
            return "ENTRETENIMIENTO";
        if (texto.matches(".*(luz|agua|internet|telefono|teléfono|arriendo|alquiler|servicio|plan|suscripcion|suscripción).*"))
            return "SERVICIOS";
        if (texto.matches(".*(farmacia|medicina|doctor|clinica|clínica|hospital|salud|pastilla|medicamento).*"))
            return "SALUD";
        return "OTRO";
    }

    private String limpiarDescripcion(String mensaje, double monto) {
        // Quitar el monto y los verbos para dejar solo la descripción del gasto
        String limpio = mensaje
                .replaceAll("(?i)gast[eé]|pagu[eé]|compr[eé]|almorc[eé]|desayun[eé]|cen[eé]", "")
                .replaceAll("\\$?\\s*" + monto + "\\s*(?:dólares?|dolares?|dls?|usd)?", "")
                .replaceAll("(?i)\\s*(en|de|por|en el|en la|al|a la)\\s*", " ")
                .trim();
        return limpio.isEmpty() ? "gasto registrado" : limpio;
    }
}
