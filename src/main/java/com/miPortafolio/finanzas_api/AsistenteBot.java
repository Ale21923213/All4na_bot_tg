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

@Component
public class AsistenteBot extends TelegramLongPollingBot {

    private final CerebroFinanzas         cerebroIA;
    private final OnboardingService       onboardingService;
    private final DetectorGastos          detectorGastos;
    private final GastoRepository         gastoRepo;
    private final UsuarioConfigRepository configRepo;
    private final ChatMessageRepository   chatRepo; // ✅ Memoria integrada

    @Value("${telegram.bot.token}")    private String botToken;
    @Value("${telegram.bot.username}") private String botUsername;

    public AsistenteBot(CerebroFinanzas cerebroIA,
                        @Lazy OnboardingService onboardingService,
                        DetectorGastos detectorGastos,
                        GastoRepository gastoRepo,
                        UsuarioConfigRepository configRepo,
                        ChatMessageRepository chatRepo) { // ✅ Inyectado
        this.cerebroIA         = cerebroIA;
        this.onboardingService = onboardingService;
        this.detectorGastos    = detectorGastos;
        this.gastoRepo         = gastoRepo;
        this.configRepo        = configRepo;
        this.chatRepo          = chatRepo;
    }

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken()    { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            long chatId;

            // ── Botones inline ────────────────────
            if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
                String data = update.getCallbackQuery().getData();

                UsuarioConfig config = configRepo.findById(chatId).orElse(null);
                if (config != null && onboardingService.estaEnOnboarding(config)) {
                    onboardingService.procesarCallback(chatId, data);
                }
                return;
            }

            // ── Texto ──────────────────────────────────────────────────
            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            chatId = update.getMessage().getChatId();
            String nombre = update.getMessage().getFrom().getFirstName();
            String texto = update.getMessage().getText();

            UsuarioConfig config = configRepo.findById(chatId)
                    .orElseGet(() -> configRepo.save(new UsuarioConfig(chatId, nombre)));

            if (texto.startsWith("/start")) {
                onboardingService.iniciar(chatId, nombre);
                return;
            }

            if (onboardingService.estaEnOnboarding(config)) {
                onboardingService.procesarTexto(chatId, texto);
                return;
            }

            procesarMensajeDiario(chatId, texto, nombre);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void procesarMensajeDiario(long chatId, String texto, String nombre) {
        // 1. Guardar mensaje del usuario en BD
        chatRepo.save(new ChatMessage(String.valueOf(chatId), "user", texto));

        // 2. Detectar y guardar gasto (Lógica original)
        DetectorGastos.ResultadoDeteccion deteccion = detectorGastos.detectar(texto);
        if (deteccion.esGasto()) {
            gastoRepo.save(new Gasto(chatId, deteccion.monto(), deteccion.categoria(), deteccion.descripcion()));
        }

        // 3. Recuperar historial de BD para contexto
        List<ChatMessage> history = chatRepo.findTop15ByChatIdOrderByTimestampAsc(String.valueOf(chatId));
        List<Map<String, String>> historialParaIA = new ArrayList<>();
        for (ChatMessage msg : history) {
            historialParaIA.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // 4. Enviar a IA
        String respuesta = cerebroIA.responder(texto, nombre, chatId, historialParaIA);

        // 5. Guardar respuesta de la IA en BD
        chatRepo.save(new ChatMessage(String.valueOf(chatId), "assistant", respuesta));

        enviarMensaje(chatId, respuesta);
    }

    public void enviarMensaje(long chatId, String texto) {
        try { execute(new SendMessage(String.valueOf(chatId), texto)); } catch (Exception e) { e.printStackTrace(); }
    }

    public void enviarConBotones(long chatId, String texto, List<List<InlineKeyboardButton>> botones) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(botones);
        sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
}