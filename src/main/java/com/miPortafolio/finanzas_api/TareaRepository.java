package com.miPortafolio.finanzas_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TareaRepository extends JpaRepository<Tarea, Long> {
    // Busca tareas que vencieron y no han sido notificadas
    List<Tarea> findByCompletadaFalseAndFechaLimiteBefore(LocalDateTime fecha);

    List<Tarea> findByChatId(long chatId);
}