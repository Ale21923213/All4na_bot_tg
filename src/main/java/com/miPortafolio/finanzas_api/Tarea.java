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
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private boolean completada = false;

    // 🚀 Cambiamos a LocalDateTime para soportar horas y minutos
    private LocalDateTime fechaLimite;

    public Tarea() {}

    public Tarea(Long chatId, String descripcion, LocalDateTime fechaLimite) {
        this.chatId      = chatId;
        this.descripcion = descripcion;
        this.fechaLimite = fechaLimite;
    }

    public Long getId()                     { return id; }
    public Long getChatId()                 { return chatId; }
    public String getDescripcion()          { return descripcion; }
    public boolean isCompletada()           { return completada; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaLimite()   { return fechaLimite; }

    public void setCompletada(boolean completada)     { this.completada = completada; }
    public void setDescripcion(String descripcion)    { this.descripcion = descripcion; }
    public void setFechaLimite(LocalDateTime fechaLimite) { this.fechaLimite = fechaLimite; }
}