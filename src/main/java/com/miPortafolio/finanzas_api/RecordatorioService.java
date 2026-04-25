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
    private final AsistenteBot    bot;

    public RecordatorioService(TareaRepository tareaRepo, @Lazy AsistenteBot bot) {
        this.tareaRepo = tareaRepo;
        this.bot       = bot;
    }

    /**
     * Corre cada 30 segundos — más preciso que cada minuto.
     * Busca tareas cuya fechaLimite ya pasó o es dentro de los próximos 30 segundos.
     */
    @Scheduled(fixedDelay = 30000)
    public void ejecutarAlertas() {
        ZoneId zona         = ZoneId.of("America/Guayaquil");
        LocalDateTime ahora = LocalDateTime.now(zona);
        // Ventana: tareas que vencen entre ahora y 30 segundos adelante
        LocalDateTime ventana = ahora.plusSeconds(30);

        List<Tarea> pendientes = tareaRepo.findByCompletadaFalseAndFechaLimiteBetween(ahora.minusSeconds(30), ventana);

        for (Tarea t : pendientes) {
            String msg = "🔔 Recordatorio, " + obtenerSaludo(t.getChatId()) + ":\n\n"
                    + "👉 " + t.getDescripcion();
            bot.enviarMensaje(t.getChatId(), msg);
            t.setCompletada(true);
            tareaRepo.save(t);
        }
    }

    private String obtenerSaludo(Long chatId) {
        return "Jefe"; // Se puede mejorar consultando UsuarioConfig por nombre
    }
}