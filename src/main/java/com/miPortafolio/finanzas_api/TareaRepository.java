package com.miPortafolio.finanzas_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TareaRepository extends JpaRepository<Tarea, Long> {

    List<Tarea> findByChatId(long chatId);

    List<Tarea> findByChatIdAndCompletada(long chatId, boolean completada);

    // Para el scheduler — busca tareas en una ventana de tiempo exacta
    List<Tarea> findByCompletadaFalseAndFechaLimiteBetween(LocalDateTime desde, LocalDateTime hasta);
}