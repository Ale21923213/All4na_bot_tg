package com.miPortafolio.finanzas_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
public class AsistenteBot extends TelegramLongPollingBot {

    private final CerebroAlana            cerebroIA;
    private final VozAll4na               vozAll4na;
    private final UsuarioConfigRepository configRepo;
    private final TareaRepository         tareaRepo;
    private final HttpClient              httpClient = HttpClient.newHttpClient();

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    public AsistenteBot(CerebroAlana cerebroIA,
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
            if (!update.hasMessage()) return;

            int fechaMensaje = update.getMessage().getDate();
            int fechaActual  = (int) (System.currentTimeMillis() / 1000);
            if (fechaActual - fechaMensaje > 60) return;

            long   chatId        = update.getMessage().getChatId();
            String nombreUsuario = update.getMessage().getFrom().getFirstName();

            registrarUsuarioSiNuevo(chatId, nombreUsuario);

            String  modo      = configRepo.findById(chatId).map(UsuarioConfig::getModoActual).orElse("GENERAL");
            boolean vozActiva = configRepo.findById(chatId).map(UsuarioConfig::isVozActiva).orElse(true);

            if (update.getMessage().hasText()) {
                String texto = update.getMessage().getText();
                if (texto.startsWith("/")) {
                    manejarComando(chatId, texto, vozActiva);
                    return;
                }
                procesarYResponder(chatId, texto, modo, nombreUsuario, vozActiva);

            } else if (update.getMessage().hasVoice()) {
                enviarMensaje(chatId, "Procesando tu audio...");
                String transcrito = transcribirVoz(update.getMessage().getVoice());
                if (transcrito == null || transcrito.isBlank()) {
                    enviarMensaje(chatId, "No pude entender el audio, intenta de nuevo.");
                    return;
                }
                enviarMensaje(chatId, "Escuche: \"" + transcrito + "\"");
                procesarYResponder(chatId, transcrito, modo, nombreUsuario, vozActiva);
            }

        } catch (Exception e) {
            System.err.println("Error en onUpdateReceived: " + e.getMessage());
        }
    }

    // ── Registro automatico de usuario nuevo ─────────────────────────
    private void registrarUsuarioSiNuevo(long chatId, String nombre) {
        if (configRepo.findById(chatId).isEmpty()) {
            UsuarioConfig nuevo = new UsuarioConfig(chatId, "GENERAL");
            nuevo.setNombre(nombre);
            configRepo.save(nuevo);
            System.out.println("Nuevo usuario registrado: " + nombre + " (" + chatId + ")");
        }
    }

    // ── Logica central ───────────────────────────────────────────────
    // ── Logica central ───────────────────────────────────────────────
    private void procesarYResponder(long chatId, String texto, String modo,
                                    String nombreUsuario, boolean vozActiva) {
        String respuesta = cerebroIA.pensar(texto, modo, nombreUsuario);

        // 🚀 Búsqueda de la etiqueta secreta de la IA
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[RECORDATORIO\\|([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2})\\|(.*?)\\]").matcher(respuesta);

        if (matcher.find()) {
            try {
                String fechaString = matcher.group(1);
                String descripcion = matcher.group(2);

                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                java.time.LocalDateTime fechaLimite = java.time.LocalDateTime.parse(fechaString, formatter);

                // Guardamos la tarea con fecha y hora exacta
                tareaRepo.save(new Tarea(chatId, descripcion, fechaLimite));

                // Borramos la etiqueta para que no se vea en Telegram ni se escuche en el audio
                respuesta = respuesta.replace(matcher.group(0), "").trim();

            } catch (Exception e) {
                System.err.println("Error procesando etiqueta de recordatorio: " + e.getMessage());
            }
        }

        enviarMensaje(chatId, respuesta);

        if (vozActiva) {
            java.io.File audio = vozAll4na.generarAudio(respuesta);
            if (audio != null) enviarVoz(chatId, audio);
        }
    }

    // ── Comandos ─────────────────────────────────────────────────────
    private void manejarComando(long chatId, String comando, boolean vozActiva) {
        String cmd = comando.toLowerCase().split(" ")[0];
        switch (cmd) {
            case "/productividad" -> cambiarModo(chatId, "PRODUCTIVIDAD");
            case "/gaming"        -> cambiarModo(chatId, "GAMING");
            case "/general"       -> cambiarModo(chatId, "GENERAL");
            case "/tareas"        -> mostrarTareas(chatId);
            case "/voz"           -> toggleVoz(chatId, !vozActiva);
            case "/start"         -> enviarMensaje(chatId,
                    "Sistema en linea. Soy Alana.\n\n" +
                            "Comandos disponibles:\n" +
                            "/productividad - Modo enfoque y planificacion\n" +
                            "/gaming - Modo gaming (LoL y Minecraft)\n" +
                            "/general - Modo conversacional\n" +
                            "/tareas - Ver tus tareas pendientes\n" +
                            "/voz - Activar o desactivar respuestas de voz");
            default -> enviarMensaje(chatId, "Comando no reconocido.");
        }
    }

    private void cambiarModo(long chatId, String nuevoModo) {
        UsuarioConfig config = configRepo.findById(chatId)
                .orElse(new UsuarioConfig(chatId, nuevoModo));
        config.setModoActual(nuevoModo);
        configRepo.save(config);
        enviarMensaje(chatId, "Protocolo actualizado. Modo: " + nuevoModo);
    }

    private void toggleVoz(long chatId, boolean activar) {
        UsuarioConfig config = configRepo.findById(chatId)
                .orElse(new UsuarioConfig(chatId, "GENERAL"));
        config.setVozActiva(activar);
        configRepo.save(config);
        enviarMensaje(chatId, activar ? "Voz activada." : "Voz desactivada. Solo texto.");
    }

    private void mostrarTareas(long chatId) {
        List<Tarea> tareas = tareaRepo.findByChatId(chatId);
        if (tareas.isEmpty()) {
            enviarMensaje(chatId, "No tienes tareas registradas.");
            return;
        }
        StringBuilder sb = new StringBuilder("Tus tareas:\n\n```\n");
        sb.append(String.format("%-3s %-26s %-12s %-4s%n", "#", "TAREA", "FECHA LIM.", "OK"));
        sb.append("-".repeat(47)).append("\n");
        for (int i = 0; i < tareas.size(); i++) {
            Tarea t = tareas.get(i);
            String desc  = t.getDescripcion().length() > 24
                    ? t.getDescripcion().substring(0, 21) + "..."
                    : t.getDescripcion();
            String fecha = t.getFechaLimite() != null ? t.getFechaLimite().toString() : "Sin fecha";
            String ok    = t.isCompletada() ? "Si" : "No";
            sb.append(String.format("%-3d %-26s %-12s %-4s%n", i + 1, desc, fecha, ok));
        }
        sb.append("```");
        enviarMensajeMd(chatId, sb.toString());
    }

    // ── Transcripcion Groq Whisper ───────────────────────────────────
    private String transcribirVoz(Voice voice) {
        try {
            GetFile getFile = new GetFile(voice.getFileId());
            File telegramFile = execute(getFile);
            String fileUrl = "https://api.telegram.org/file/bot" + botToken
                    + "/" + telegramFile.getFilePath();

            HttpRequest downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl)).GET().build();
            HttpResponse<InputStream> downloadResp = httpClient.send(
                    downloadReq, HttpResponse.BodyHandlers.ofInputStream());

            Path archivoTemp = Files.createTempFile("voz_", ".ogg");
            Files.copy(downloadResp.body(), archivoTemp, StandardCopyOption.REPLACE_EXISTING);

            String boundary    = "----AlanaVozBoundary" + System.currentTimeMillis();
            byte[] audioBytes  = Files.readAllBytes(archivoTemp);
            byte[] headerBytes = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.ogg\"\r\n"
                    + "Content-Type: audio/ogg\r\n\r\n").getBytes();
            byte[] footerBytes = ("\r\n--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                    + "whisper-large-v3\r\n"
                    + "--" + boundary + "--\r\n").getBytes();

            byte[] body = new byte[headerBytes.length + audioBytes.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
            System.arraycopy(audioBytes,  0, body, headerBytes.length, audioBytes.length);
            System.arraycopy(footerBytes, 0, body, headerBytes.length + audioBytes.length, footerBytes.length);

            HttpRequest whisperReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + cerebroIA.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> whisperResp = httpClient.send(
                    whisperReq, HttpResponse.BodyHandlers.ofString());

            Files.deleteIfExists(archivoTemp);

            String respBody   = whisperResp.body();
            int    inicio     = respBody.indexOf("\"text\"");
            if (inicio == -1) return null;
            int comillaAbre   = respBody.indexOf("\"", inicio + 7);
            int comillaCierra = respBody.indexOf("\"", comillaAbre + 1);
            return respBody.substring(comillaAbre + 1, comillaCierra);

        } catch (Exception e) {
            System.err.println("Error transcribiendo voz: " + e.getMessage());
            return null;
        }
    }

    // ── Envios ───────────────────────────────────────────────────────

    /**
     * Envía texto plano sin Markdown.
     * Usado para respuestas de la IA — el contenido viene de Groq y puede
     * tener asteriscos o guiones bajos sin cerrar que rompen el parser de Telegram.
     * Publico para que RecordatorioService también lo use.
     */
    public void enviarMensaje(long chatId, String texto) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Envía texto con Markdown — SOLO para mensajes del sistema que
     * nosotros controlamos (tablas, recordatorios, confirmaciones).
     * Nunca pasar aquí texto que venga de la IA o del usuario.
     */
    public void enviarMensajeMd(long chatId, String texto) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        // Quitamos el setParseMode para que Telegram trate todo como texto plano
        try {
            execute(sm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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