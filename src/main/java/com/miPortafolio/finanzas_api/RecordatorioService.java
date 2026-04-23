package com.miPortafolio.finanzas_api;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class RecordatorioService {

    private final TareaRepository          tareaRepo;
    private final UsuarioConfigRepository  usuarioRepo;
    private final AsistenteBot             bot;

    public RecordatorioService(TareaRepository tareaRepo,
                               UsuarioConfigRepository usuarioRepo,
                               @Lazy AsistenteBot bot) {
        this.tareaRepo   = tareaRepo;
        this.usuarioRepo = usuarioRepo;
        this.bot         = bot;
    }

    @Scheduled(cron = "0 * * * * *") // Revisa cada minuto exacto
    public void ejecutarProtocoloAlerta() {
        ZoneId zona = ZoneId.of("America/Guayaquil");
        LocalDateTime ahora = LocalDateTime.now(zona);

        List<Tarea> pendientes = tareaRepo.findByCompletadaFalseAndFechaLimiteBefore(ahora);

        for (Tarea t : pendientes) {
            String saludo = esOwner(t.getChatId()) ? "Jefe" : "Atención";
            String aviso = "🔔 *" + saludo + "*, recordatorio activo:\n\n👉 " + t.getDescripcion();

            bot.enviarMensajeMd(t.getChatId(), aviso);

            t.setCompletada(true);
            tareaRepo.save(t);
        }
    }

    private boolean esOwner(long chatId) {
        return usuarioRepo.findById(chatId)
                .map(u -> u.getNombre() != null && u.getNombre().toLowerCase().contains("alejo"))
                .orElse(false);
    }
}