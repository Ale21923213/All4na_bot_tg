package com.miPortafolio.finanzas_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class AsistenteBot extends TelegramLongPollingBot {

    private final CerebroAlana cerebroIA;
    private final VozAll4na vozAll4na;
    private final UsuarioConfigRepository configRepo;
    private final TareaRepository tareaRepo;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    // Usamos @Lazy para evitar que el bot y el RecordatorioService choquen al arrancar
    public AsistenteBot(@Lazy CerebroAlana cerebroIA,
                        @Lazy VozAll4na vozAll4na,
                        UsuarioConfigRepository configRepo,
                        TareaRepository tareaRepo) {
        this.cerebroIA = cerebroIA;
        this.vozAll4na = vozAll4na;
        this.configRepo = configRepo;
        this.tareaRepo = tareaRepo;
    }

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // 1. MANEJO DE BOTONES (Interacción del Menú)
            if (update.hasCallbackQuery()) {
                manejarBotones(update);
                return;
            }

            if (!update.hasMessage()) return;

            // Filtro de seguridad: Mensajes viejos
            int fechaMensaje = update.getMessage().getDate();
            int fechaActual = (int) (System.currentTimeMillis() / 1000);
            if (fechaActual - fechaMensaje > 60) return;

            long chatId = update.getMessage().getChatId();
            String nombreUsuario = update.getMessage().getFrom().getFirstName();

            // Aseguramos que el usuario esté en la DB
            registrarUsuarioSiNuevo(chatId, nombreUsuario);

            // Obtenemos configuración actual
            UsuarioConfig config = configRepo.findById(chatId).get();
            String modo = config.getModoActual();
            boolean vozActiva = config.isVozActiva();

            // 2. LOGICA DE TEXTO
            if (update.getMessage().hasText()) {
                String texto = update.getMessage().getText();
                String textoLow = texto.toLowerCase();

                // DETECTOR DE BIENVENIDA: Si saluda o dice "Alana"
                if (textoLow.matches(".*(hola|alana|buenos dias|buenas noches|hey|oe|saludos).*")) {
                    enviarMenuModos(chatId, nombreUsuario);
                    return;
                }

                // COMANDOS DIRECTOS
                if (texto.startsWith("/")) {
                    manejarComando(chatId, texto, vozActiva);
                    return;
                }

                // RESPUESTA NORMAL CON IA
                procesarYResponder(chatId, texto, modo, nombreUsuario, vozActiva);

            }
            // 3. LOGICA DE VOZ
            else if (update.getMessage().hasVoice()) {
                enviarMensaje(chatId, "Procesando audio, Jefe... 🎧");
                String transcrito = transcribirVoz(update.getMessage().getVoice());

                if (transcrito == null || transcrito.isBlank()) {
                    enviarMensaje(chatId, "No capté nada, ¿podría repetir?");
                    return;
                }

                enviarMensaje(chatId, "Escuché: \"" + transcrito + "\"");
                procesarYResponder(chatId, transcrito, modo, nombreUsuario, vozActiva);
            }

        } catch (Exception e) {
            System.err.println("🚨 Error crítico en el bot: " + e.getMessage());
        }
    }

    private void enviarMenuModos(long chatId, String nombre) {
        SendMessage sm = new SendMessage();
        sm.setChatId(String.valueOf(chatId));
        sm.setParseMode("Markdown");
        sm.setText("🌟 *Protocolo Alana v2.5*\n\nHola Jefe, he detectado su presencia. Seleccione el modo operativo para esta sesión:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(crearBoton("🚀 PRODUCTIVIDAD", "SET_MODO_PRODUCTIVIDAD")));
        rows.add(List.of(crearBoton("🎮 GAMING", "SET_MODO_GAMING")));
        rows.add(List.of(crearBoton("💬 GENERAL", "SET_MODO_GENERAL")));

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
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();

        if (data.startsWith("SET_MODO_")) {
            String nuevoModo = data.replace("SET_MODO_", "");
            cambiarModo(chatId, nuevoModo);
            enviarMensaje(chatId, "✅ Modo " + nuevoModo + " activado satisfactoriamente.");
        }
    }

    private void registrarUsuarioSiNuevo(long chatId, String nombre) {
        if (configRepo.findById(chatId).isEmpty()) {
            UsuarioConfig nuevo = new UsuarioConfig(chatId, "GENERAL");
            nuevo.setNombre(nombre);
            configRepo.save(nuevo);
        }
    }

    private void procesarYResponder(long chatId, String texto, String modo, String nombreUsuario, boolean vozActiva) {
        String respuesta = cerebroIA.pensar(texto, modo, nombreUsuario);

        // REGEX de Recordatorios (Detecta la etiqueta secreta)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[RECORDATORIO\\|([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2})\\|(.*?)\\]").matcher(respuesta);

        if (matcher.find()) {
            try {
                java.time.LocalDateTime fecha = java.time.LocalDateTime.parse(matcher.group(1), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                tareaRepo.save(new Tarea(chatId, matcher.group(2), fecha));
                respuesta = respuesta.replace(matcher.group(0), "").trim();
            } catch (Exception e) { System.err.println("Fallo al guardar recordatorio."); }
        }

        enviarMensaje(chatId, respuesta);

        if (vozActiva) {
            java.io.File audio = vozAll4na.generarAudio(respuesta);
            if (audio != null) enviarVoz(chatId, audio);
        }
    }

    private void manejarComando(long chatId, String comando, boolean vozActiva) {
        String cmd = comando.toLowerCase().split(" ")[0];
        switch (cmd) {
            case "/tareas" -> mostrarTareas(chatId);
            case "/voz" -> toggleVoz(chatId, !vozActiva);
            case "/start" -> enviarMenuModos(chatId, "Jefe");
            default -> enviarMensaje(chatId, "Comando no reconocido.");
        }
    }

    private void cambiarModo(long chatId, String nuevoModo) {
        UsuarioConfig config = configRepo.findById(chatId).orElse(new UsuarioConfig(chatId, nuevoModo));
        config.setModoActual(nuevoModo);
        configRepo.save(config);
    }

    private void toggleVoz(long chatId, boolean activar) {
        UsuarioConfig config = configRepo.findById(chatId).get();
        config.setVozActiva(activar);
        configRepo.save(config);
        enviarMensaje(chatId, activar ? "🔊 Voz encendida." : "🔇 Voz apagada.");
    }

    private void mostrarTareas(long chatId) {
        List<Tarea> tareas = tareaRepo.findByChatId(chatId);
        if (tareas.isEmpty()) {
            enviarMensaje(chatId, "No hay tareas, Jefe. Está libre.");
            return;
        }
        StringBuilder sb = new StringBuilder("*Lista de Tareas Pendientes:*\n\n```\n");
        sb.append(String.format("%-3s %-20s %-12s%n", "#", "DESCRIPCIÓN", "FECHA"));
        sb.append("---------------------------------------\n");
        for (int i = 0; i < tareas.size(); i++) {
            Tarea t = tareas.get(i);
            String desc = t.getDescripcion().length() > 18 ? t.getDescripcion().substring(0, 15) + "..." : t.getDescripcion();
            sb.append(String.format("%-3d %-20s %-12s%n", i + 1, desc, t.getFechaLimite().toString().replace("T", " ")));
        }
        sb.append("```");
        enviarMensajeMd(chatId, sb.toString());
    }

    public void enviarMensaje(long chatId, String texto) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    public void enviarMensajeMd(long chatId, String texto) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        sm.setParseMode("Markdown");
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void enviarVoz(long chatId, java.io.File audio) {
        SendVoice sv = new SendVoice();
        sv.setChatId(String.valueOf(chatId));
        sv.setVoice(new InputFile(audio));
        try {
            execute(sv);
            if (audio.exists()) audio.delete();
        } catch (Exception e) { System.err.println("Error al enviar audio."); }
    }

    // Código de Transcripción Whisper (Se mantiene igual)
    private String transcribirVoz(Voice voice) {
        try {
            GetFile getFile = new GetFile(voice.getFileId());
            File telegramFile = execute(getFile);
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + telegramFile.getFilePath();

            HttpRequest downloadReq = HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build();
            HttpResponse<InputStream> downloadResp = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());

            Path archivoTemp = Files.createTempFile("voz_", ".ogg");
            Files.copy(downloadResp.body(), archivoTemp, StandardCopyOption.REPLACE_EXISTING);

            String boundary = "----AlanaVozBoundary" + System.currentTimeMillis();
            byte[] audioBytes = Files.readAllBytes(archivoTemp);
            byte[] headerBytes = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.ogg\"\r\nContent-Type: audio/ogg\r\n\r\n").getBytes();
            byte[] footerBytes = ("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-large-v3\r\n--" + boundary + "--\r\n").getBytes();

            byte[] body = new byte[headerBytes.length + audioBytes.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
            System.arraycopy(audioBytes, 0, body, headerBytes.length, audioBytes.length);
            System.arraycopy(footerBytes, 0, body, headerBytes.length + audioBytes.length, footerBytes.length);

            HttpRequest whisperReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + cerebroIA.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();

            HttpResponse<String> whisperResp = httpClient.send(whisperReq, HttpResponse.BodyHandlers.ofString());
            Files.deleteIfExists(archivoTemp);

            String respBody = whisperResp.body();
            int inicio = respBody.indexOf("\"text\"");
            if (inicio == -1) return null;
            int c1 = respBody.indexOf("\"", inicio + 7);
            int c2 = respBody.indexOf("\"", c1 + 1);
            return respBody.substring(c1 + 1, c2);
        } catch (Exception e) { return null; }
    }
}