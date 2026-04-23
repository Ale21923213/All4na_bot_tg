package com.miPortafolio.finanzas_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TareaRepository extends JpaRepository<Tarea, Long> {

    // Todas las tareas de un usuario específico
    List<Tarea> findByChatId(Long chatId);

    // Solo las pendientes de un usuario
    List<Tarea> findByChatIdAndCompletada(Long chatId, boolean completada);
}