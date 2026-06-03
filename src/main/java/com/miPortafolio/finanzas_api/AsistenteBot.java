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

    private final CerebroFinanzas         cerebroIA; // ✅ Conectado a la IA de finanzas
    private final OnboardingService       onboardingService;
    private final DetectorGastos          detectorGastos;
    private final GastoRepository         gastoRepo;
    private final UsuarioConfigRepository configRepo;

    // Memoria de conversación por usuario
    private final Map<Long, List<Map<String, String>>> historialChat = new ConcurrentHashMap<>();

    @Value("${telegram.bot.token}")    private String botToken;
    @Value("${telegram.bot.username}") private String botUsername;

    public AsistenteBot(CerebroFinanzas cerebroIA,
                        @Lazy OnboardingService onboardingService,
                        DetectorGastos detectorGastos,
                        GastoRepository gastoRepo,
                        UsuarioConfigRepository configRepo) {
        this.cerebroIA         = cerebroIA;
        this.onboardingService = onboardingService;
        this.detectorGastos    = detectorGastos;
        this.gastoRepo         = gastoRepo;
        this.configRepo        = configRepo;
    }

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken()    { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            long chatId;

            // ── Botones inline (Onboarding u otros) ────────────────────
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

            // Asegurar que el usuario exista en DB
            UsuarioConfig config = configRepo.findById(chatId)
                    .orElseGet(() -> configRepo.save(new UsuarioConfig(chatId, nombre)));

            // 1. Comando de inicio (Fase 0)
            if (texto.startsWith("/start")) {
                historialChat.remove(chatId); // Reiniciar memoria de charla
                onboardingService.iniciar(chatId, nombre);
                return;
            }

            // 2. Si está en medio del cuestionario de Onboarding
            if (onboardingService.estaEnOnboarding(config)) {
                onboardingService.procesarTexto(chatId, texto);
                return;
            }

            // 3. Fase 1: Uso diario normal (Registro de Gastos + IA)
            procesarMensajeDiario(chatId, texto, nombre);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void procesarMensajeDiario(long chatId, String texto, String nombre) {
        // A. Detectar si el texto contiene un gasto
        DetectorGastos.ResultadoDeteccion deteccion = detectorGastos.detectar(texto);

        if (deteccion.esGasto()) {
            // Guardar automáticamente
            Gasto nuevoGasto = new Gasto(chatId, deteccion.monto(), deteccion.categoria(), deteccion.descripcion());
            gastoRepo.save(nuevoGasto);
            System.out.println("Gasto detectado y guardado: $" + deteccion.monto() + " en " + deteccion.categoria());
            // Al guardar en BD antes de llamar a la IA, la IA ya lo verá en su "Contexto" actualizado.
        }

        // B. Enviar mensaje a Groq IA para que responda naturalmente
        List<Map<String, String>> historial = historialChat.computeIfAbsent(chatId, k -> new ArrayList<>());

        String respuesta = cerebroIA.responder(texto, nombre, chatId, historial);

        // Actualizar memoria (max 5 turnos = 10 mensajes para no saturar tokens)
        historial.add(Map.of("role", "user", "content", texto));
        historial.add(Map.of("role", "assistant", "content", respuesta));
        if (historial.size() > 10) historial.subList(0, 2).clear();

        enviarMensaje(chatId, respuesta);
    }

    // ── Utilidades de Envío ─────────────────────────────────────────

    public void enviarMensaje(long chatId, String texto) {
        try {
            execute(new SendMessage(String.valueOf(chatId), texto));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ✅ Este método faltaba y el OnboardingService lo necesita
    public void enviarConBotones(long chatId, String texto, List<List<InlineKeyboardButton>> botones) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), texto);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(botones);
        sm.setReplyMarkup(markup);
        try {
            execute(sm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}