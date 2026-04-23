package com.miPortafolio.finanzas_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class ConfiguracionTelegram {

    @Bean
    public TelegramBotsApi telegramBotsApi(AsistenteBot asistenteBot) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(asistenteBot);
        System.out.println("Alana registrada y en línea.");
        return botsApi;
    }
}