package com.miPortafolio.finanzas_api;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class RecordatorioService {

    private final TareaRepository tareaRepo;
    private final AsistenteBot bot;

    public RecordatorioService(TareaRepository tareaRepo, @Lazy AsistenteBot bot) {
        this.tareaRepo = tareaRepo;
        this.bot = bot;
    }

    @Scheduled(cron = "0 * * * * *")
    public void ejecutarAlertas() {
        ZoneId zona = ZoneId.of("America/Guayaquil");
        LocalDateTime ahora = LocalDateTime.now(zona);

        List<Tarea> pendientes = tareaRepo.findByCompletadaFalseAndFechaLimiteBefore(ahora);

        for (Tarea t : pendientes) {
            // El emoji de la campana con el nombre dinámico
            String msg = "🔔 *Atención Jefe*, recordatorio activo:\n\n👉 " + t.getDescripcion();
            bot.enviarMensajeMd(t.getChatId(), msg);

            t.setCompletada(true);
            tareaRepo.save(t);
        }
    }
}