package com.miPortafolio.finanzas_api;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class MetaAhorro { // ✅ Corregido a mayúscula inicial

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String nombre;
    private Double montoObjetivo;
    private Double montoActual = 0.0;
    private LocalDate fechaCreacion = LocalDate.now();
    private boolean activa = true;

    public MetaAhorro() {}

    public MetaAhorro(Long chatId, String nombre, Double montoObjetivo) {
        this.chatId        = chatId;
        this.nombre        = nombre;
        this.montoObjetivo = montoObjetivo;
    }

    public Long getId()               { return id; }
    public Long getChatId()           { return chatId; }
    public String getNombre()         { return nombre; }
    public Double getMontoObjetivo()  { return montoObjetivo; }
    public Double getMontoActual()    { return montoActual; }
    public LocalDate getFechaCreacion(){ return fechaCreacion; }
    public boolean isActiva()         { return activa; }

    public void setNombre(String n)          { this.nombre = n; }
    public void setMontoObjetivo(Double m)   { this.montoObjetivo = m; }
    public void setMontoActual(Double m)     { this.montoActual = m; }
    public void setActiva(boolean a)         { this.activa = a; }
}