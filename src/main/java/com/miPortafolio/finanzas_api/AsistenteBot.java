package com.miPortafolio.finanzas_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AsistenteBot extends TelegramLongPollingBot {

    private final CerebroAlana cerebroIA;
    private final UsuarioConfigRepository configRepo;
    private final TareaRepository tareaRepo;

    // 🧠 MEMORIA: Guarda el contexto de la charla
    private final Map<Long, List<Map<String, String>>> historialChat = new ConcurrentHashMap<>();
    // ⏱️ SESIÓN: Si pasan 30 min, saludamos de nuevo
    private final Map<Long, Long> ultimaActividad = new ConcurrentHashMap<>();

    @Value("${telegram.bot.token}") private String botToken;
    @Value("${telegram.bot.username}") private String botUsername;

    public AsistenteBot(@Lazy CerebroAlana cerebroIA, UsuarioConfigRepository configRepo, TareaRepository tareaRepo) {
        this.cerebroIA = cerebroIA;
        this.configRepo = configRepo;
        this.tareaRepo = tareaRepo;
    }

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) { manejarBotones(update); return; }
            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            long chatId = update.getMessage().getChatId();
            String texto = update.getMessage().getText();
            String nombre = update.getMessage().getFrom().getFirstName();

            registrarUsuarioSiNuevo(chatId, nombre);
            long ahora = System.currentTimeMillis();

            // 🚀 REINICIO DE SESIÓN: Si ha pasado más de 30 minutos
            if (!ultimaActividad.containsKey(chatId) || (ahora - ultimaActividad.get(chatId) > 1800000)) {
                historialChat.remove(chatId); // Olvida la charla anterior para no mezclar temas
                enviarMenuModos(chatId, nombre);
                ultimaActividad.put(chatId, ahora);
                return;
            }
            ultimaActividad.put(chatId, ahora);

            if (texto.startsWith("/")) { manejarComando(chatId, texto); return; }

            UsuarioConfig config = configRepo.findById(chatId).get();
            procesarConContexto(chatId, texto, config.getModoActual(), nombre);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void procesarConContexto(long chatId, String texto, String modo, String nombre) {
        List<Map<String, String>> historial = historialChat.computeIfAbsent(chatId, k -> new ArrayList<>());

        String respuesta = cerebroIA.pensarConContexto(texto, modo, nombre, historial);

        // Actualizamos memoria (Usuario y Asistente)
        historial.add(Map.of("role", "user", "content", texto));
        historial.add(Map.of("role", "assistant", "content", respuesta));
        if (historial.size() > 14) historial.subList(0, 2).clear(); // Mantenemos 7 turnos

        // PROCESAR RECORDATORIO
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[RECORDATORIO\\|([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2})\\|(.*?)\\]").matcher(respuesta);
        if (matcher.find()) {
            tareaRepo.save(new Tarea(chatId, matcher.group(2), java.time.LocalDateTime.parse(matcher.group(1), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
            respuesta = respuesta.replace(matcher.group(0), "").trim();
        }

        enviarMensaje(chatId, respuesta);
    }

    private void enviarMenuModos(long chatId, String nombre) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), "🌟 *Protocolo Alana v3.5*\n\nHola Jefe, he despertado sus sistemas. ¿En qué modo operaremos hoy?");
        sm.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(crearBoton("🚀 PRODUCTIVIDAD", "SET_MODO_PRODUCTIVIDAD")));
        rows.add(List.of(crearBoton("🎮 GAMING", "SET_MODO_GAMING")));
        rows.add(List.of(crearBoton("💬 GENERAL", "SET_MODO_GENERAL")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private InlineKeyboardButton crearBoton(String t, String d) {
        InlineKeyboardButton b = new InlineKeyboardButton(); b.setText(t); b.setCallbackData(d); return b;
    }

    private void manejarBotones(Update u) {
        long chatId = u.getCallbackQuery().getMessage().getChatId();
        String data = u.getCallbackQuery().getData();
        if (data.startsWith("SET_MODO_")) {
            String m = data.replace("SET_MODO_", "");
            UsuarioConfig c = configRepo.findById(chatId).get();
            c.setModoActual(m);
            configRepo.save(c);
            enviarMensaje(chatId, "✅ Modo " + m + " activado.");
        }
    }

    public void enviarMensaje(long chatId, String t) {
        try { execute(new SendMessage(String.valueOf(chatId), t)); } catch (Exception e) { e.printStackTrace(); }
    }

    public void enviarMensajeMd(long chatId, String t) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), t);
        sm.setParseMode("Markdown");
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void registrarUsuarioSiNuevo(long id, String n) {
        if (configRepo.findById(id).isEmpty()) {
            UsuarioConfig u = new UsuarioConfig(id, "GENERAL"); u.setNombre(n); configRepo.save(u);
        }
    }

    private void manejarComando(long id, String c) {
        if (c.startsWith("/tareas")) mostrarTareas(id);
        else if (c.startsWith("/start")) enviarMenuModos(id, "Jefe");
    }

    private void mostrarTareas(long id) {
        List<Tarea> ts = tareaRepo.findByChatId(id);
        if (ts.isEmpty()) { enviarMensaje(id, "No hay tareas pendientes."); return; }
        StringBuilder sb = new StringBuilder("*Tareas:* \n\n");
        for (Tarea t : ts) sb.append("- ").append(t.getDescripcion()).append(" (").append(t.getFechaLimite()).append(")\n");
        enviarMensajeMd(id, sb.toString());
    }
}