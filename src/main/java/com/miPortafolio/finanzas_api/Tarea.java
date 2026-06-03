package com.miPortafolio.finanzas_api;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String descripcion;
    private LocalDateTime fechaLimite;
    private boolean completada = false;

    public Tarea() {}

    public Tarea(Long chatId, String descripcion, LocalDateTime fechaLimite) {
        this.chatId = chatId;
        this.descripcion = descripcion;
        this.fechaLimite = fechaLimite;
    }

    public Long getId() { return id; }
    public Long getChatId() { return chatId; }
    public String getDescripcion() { return descripcion; }
    public LocalDateTime getFechaLimite() { return fechaLimite; }
    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }
}
