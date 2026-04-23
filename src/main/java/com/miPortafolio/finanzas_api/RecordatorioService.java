package com.miPortafolio.finanzas_api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RecordatorioService {

    private final TareaRepository          tareaRepo;
    private final UsuarioConfigRepository  usuarioRepo;
    private final AsistenteBot             bot;

    public RecordatorioService(TareaRepository tareaRepo,
                               UsuarioConfigRepository usuarioRepo,
                               AsistenteBot bot) {
        this.tareaRepo   = tareaRepo;
        this.usuarioRepo = usuarioRepo;
        this.bot         = bot;
    }

    // 🚀 Se ejecuta cada minuto en el segundo 00
    @Scheduled(cron = "0 * * * * *")
    public void recordatoriosExactosPorMinuto() {
        ZoneId zona = ZoneId.of("America/Guayaquil");
        LocalDateTime ahora = LocalDateTime.now(zona).truncatedTo(ChronoUnit.MINUTES);

        List<Tarea> pendientes = tareaRepo.findAll().stream()
                .filter(t -> !t.isCompletada() && t.getFechaLimite() != null)
                .toList();

        for (Tarea t : pendientes) {
            // Si la hora límite coincide con la hora actual, enviamos la alerta
            if (t.getFechaLimite().truncatedTo(ChronoUnit.MINUTES).equals(ahora)) {
                String saludo = esOwner(t.getChatId()) ? "Jefe" : "Atención";
                String msg = "🔔 *" + saludo + "*, es la hora de su recordatorio:\n\n👉 " + t.getDescripcion();

                bot.enviarMensajeMd(t.getChatId(), msg);

                // Marcamos como completada para no volver a enviarla el siguiente minuto
                t.setCompletada(true);
                tareaRepo.save(t);
            }
        }
    }

    private boolean esOwner(long chatId) {
        return usuarioRepo.findById(chatId)
                .map(u -> "Alejo".equalsIgnoreCase(u.getNombre()))
                .orElse(false);
    }
}