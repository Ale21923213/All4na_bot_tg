package com.miPortafolio.finanzas_api;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private Double monto;

    // TRANSPORTE, ALIMENTACION, ENTRETENIMIENTO, SERVICIOS, SALUD, OTRO
    private String categoria;
    private String descripcion;
    private LocalDate fecha;
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public Gasto() {}

    public Gasto(Long chatId, Double monto, String categoria, String descripcion) {
        this.chatId      = chatId;
        this.monto       = monto;
        this.categoria   = categoria;
        this.descripcion = descripcion;
        this.fecha       = LocalDate.now();
    }

    public Long getId()               { return id; }
    public Long getChatId()           { return chatId; }
    public Double getMonto()          { return monto; }
    public String getCategoria()      { return categoria; }
    public String getDescripcion()    { return descripcion; }
    public LocalDate getFecha()       { return fecha; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }

    public void setMonto(Double m)          { this.monto = m; }
    public void setCategoria(String c)      { this.categoria = c; }
    public void setDescripcion(String d)    { this.descripcion = d; }
}
