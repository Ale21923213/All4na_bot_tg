package com.miPortafolio.finanzas_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AsistenteBot extends TelegramLongPollingBot {

    private final CerebroAlana            cerebroIA;
    private final VozAll4na               vozAll4na;   // ✅ RE-INYECTADO
    private final UsuarioConfigRepository configRepo;
    private final TareaRepository         tareaRepo;

    // Memoria de conversación por usuario
    private final Map<Long, List<Map<String, String>>> historialChat   = new ConcurrentHashMap<>();
    private final Map<Long, Long>                      ultimaActividad = new ConcurrentHashMap<>();

    @Value("${telegram.bot.token}")    private String botToken;
    @Value("${telegram.bot.username}") private String botUsername;

    public AsistenteBot(@Lazy CerebroAlana cerebroIA,
                        VozAll4na vozAll4na,
                        UsuarioConfigRepository configRepo,
                        TareaRepository tareaRepo) {
        this.cerebroIA  = cerebroIA;
        this.vozAll4na  = vozAll4na;
        this.configRepo = configRepo;
        this.tareaRepo  = tareaRepo;
    }

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken()    { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // ── Botones inline ─────────────────────────────────────────
            if (update.hasCallbackQuery()) {
                manejarBotones(update);
                return;
            }

            if (!update.hasMessage()) return;

            long   chatId = update.getMessage().getChatId();
            String nombre = update.getMessage().getFrom().getFirstName();

            registrarUsuarioSiNuevo(chatId, nombre);

            long ahora = System.currentTimeMillis();
            boolean sesionNueva = !ultimaActividad.containsKey(chatId)
                    || (ahora - ultimaActividad.get(chatId) > 1_800_000); // 30 min

            if (sesionNueva) {
                historialChat.remove(chatId);
                enviarMenuModos(chatId, nombre);
                ultimaActividad.put(chatId, ahora);
                return;
            }
            ultimaActividad.put(chatId, ahora);

            // ── Texto ──────────────────────────────────────────────────
            if (update.getMessage().hasText()) {
                String texto = update.getMessage().getText();
                if (texto.startsWith("/")) {
                    manejarComando(chatId, texto);
                    return;
                }
                UsuarioConfig config = configRepo.findById(chatId)
                        .orElse(new UsuarioConfig(chatId, "GENERAL"));
                procesarConContexto(chatId, texto, config.getModoActual(), nombre, config.isVozActiva());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Lógica principal ─────────────────────────────────────────────
    private void procesarConContexto(long chatId, String texto, String modo,
                                     String nombre, boolean vozActiva) {
        List<Map<String, String>> historial =
                historialChat.computeIfAbsent(chatId, k -> new ArrayList<>());

        String respuesta = cerebroIA.pensarConContexto(texto, modo, nombre, historial);

        // Actualizar memoria (max 7 turnos = 14 mensajes)
        historial.add(Map.of("role", "user",      "content", texto));
        historial.add(Map.of("role", "assistant",  "content", respuesta));
        if (historial.size() > 14) historial.subList(0, 2).clear();

        // Detectar y guardar recordatorio embebido en la respuesta
        var matcher = java.util.regex.Pattern
                .compile("\\[RECORDATORIO\\|([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2})\\|(.*?)\\]")
                .matcher(respuesta);
        if (matcher.find()) {
            java.time.LocalDateTime fecha = java.time.LocalDateTime.parse(
                    matcher.group(1),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            tareaRepo.save(new Tarea(chatId, matcher.group(2), fecha));
            respuesta = respuesta.replace(matcher.group(0), "").trim();
        }

        // Enviar texto
        enviarMensaje(chatId, respuesta);

        // ✅ Enviar audio si la voz está activa
        if (vozActiva) {
            java.io.File audio = vozAll4na.generarAudio(respuesta);
            if (audio != null) enviarVoz(chatId, audio);
        }
    }

    // ── Menú de modos con botones ─────────────────────────────────────
    private void enviarMenuModos(long chatId, String nombre) {
        String saludo = "Alejo".equalsIgnoreCase(nombre) ? "Jefe" : nombre;
        SendMessage sm = new SendMessage(String.valueOf(chatId),
                "Sistema en linea. Hola " + saludo + ", selecciona el modo de operacion:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(crearBoton("PRODUCTIVIDAD", "SET_MODO_PRODUCTIVIDAD")));
        rows.add(List.of(crearBoton("GAMING",        "SET_MODO_GAMING")));
        rows.add(List.of(crearBoton("GENERAL",       "SET_MODO_GENERAL")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private InlineKeyboardButton crearBoton(String texto, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(texto);
        b.setCallbackData(data);
        return b;
    }

    private void manejarBotones(Update update) {
        long   chatId = update.getCallbackQuery().getMessage().getChatId();
        String data   = update.getCallbackQuery().getData();
        if (data.startsWith("SET_MODO_")) {
            String modo = data.replace("SET_MODO_", "");
            UsuarioConfig c = configRepo.findById(chatId)
                    .orElse(new UsuarioConfig(chatId, modo));
            c.setModoActual(modo);
            configRepo.save(c);
            enviarMensaje(chatId, "Modo " + modo + " activado.");
        }
    }

    // ── Comandos ─────────────────────────────────────────────────────
    private void manejarComando(long chatId, String comando) {
        switch (comando.toLowerCase().split(" ")[0]) {
            case "/tareas" -> mostrarTareas(chatId);
            case "/voz"    -> toggleVoz(chatId);
            case "/start"  -> enviarMenuModos(chatId, "Jefe");
            default        -> enviarMensaje(chatId, "Comando no reconocido.");
        }
    }

    private void toggleVoz(long chatId) {
        UsuarioConfig c = configRepo.findById(chatId)
                .orElse(new UsuarioConfig(chatId, "GENERAL"));
        boolean nuevo = !c.isVozActiva();
        c.setVozActiva(nuevo);
        configRepo.save(c);
        enviarMensaje(chatId, nuevo ? "Voz activada." : "Voz desactivada.");
    }

    private void mostrarTareas(long chatId) {
        List<Tarea> ts = tareaRepo.findByChatId(chatId);
        if (ts.isEmpty()) { enviarMensaje(chatId, "No hay tareas pendientes."); return; }
        StringBuilder sb = new StringBuilder("Tareas pendientes:\n\n```\n");
        sb.append(String.format("%-3s %-26s %-16s%n", "#", "TAREA", "FECHA LIMITE"));
        sb.append("-".repeat(47)).append("\n");
        for (int i = 0; i < ts.size(); i++) {
            Tarea t = ts.get(i);
            String desc  = t.getDescripcion().length() > 24
                    ? t.getDescripcion().substring(0, 21) + "..." : t.getDescripcion();
            String fecha = t.getFechaLimite() != null ? t.getFechaLimite().toString() : "Sin fecha";
            sb.append(String.format("%-3d %-26s %-16s%n", i + 1, desc, fecha));
        }
        sb.append("```");
        enviarMensajeMd(chatId, sb.toString());
    }

    private void registrarUsuarioSiNuevo(long chatId, String nombre) {
        if (configRepo.findById(chatId).isEmpty()) {
            UsuarioConfig u = new UsuarioConfig(chatId, "GENERAL");
            u.setNombre(nombre);
            configRepo.save(u);
            System.out.println("Nuevo usuario: " + nombre + " (" + chatId + ")");
        }
    }

    // ── Envíos ───────────────────────────────────────────────────────

    // Texto plano — para respuestas de la IA (evita errores de Markdown)
    public void enviarMensaje(long chatId, String texto) {
        try {
            execute(new SendMessage(String.valueOf(chatId), texto));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Markdown — solo para mensajes del sistema que nosotros controlamos
    public void enviarMensajeMd(long chatId, String texto) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        sm.setParseMode("Markdown");
        try {
            execute(sm);
        } catch (Exception e) {
            // Fallback: si el Markdown falla, enviamos texto plano limpio
            enviarMensaje(chatId, texto.replaceAll("[*_`\\[\\]]", ""));
        }
    }

    // Nota de voz — se reproduce automáticamente solo el último en Telegram móvil
    private void enviarVoz(long chatId, java.io.File audio) {
        SendVoice sv = new SendVoice();
        sv.setChatId(String.valueOf(chatId));
        sv.setVoice(new InputFile(audio));
        try {
            execute(sv);
            if (audio.exists()) audio.delete();
        } catch (Exception e) {
            System.err.println("Fallo al enviar voz: " + e.getMessage());
        }
    }
}