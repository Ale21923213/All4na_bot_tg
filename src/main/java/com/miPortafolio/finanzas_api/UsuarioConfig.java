package com.miPortafolio.finanzas_api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class UsuarioConfig {

    @Id
    private Long chatId;

    private String modoActual = "GENERAL";

    private boolean vozActiva = true;

    // Nombre del usuario para personalizar notificaciones
    private String nombre = "Usuario";

    public UsuarioConfig() {}

    public UsuarioConfig(Long chatId, String modoActual) {
        this.chatId     = chatId;
        this.modoActual = modoActual;
    }

    public Long getChatId()                { return chatId; }
    public void setChatId(Long id)         { this.chatId = id; }
    public String getModoActual()          { return modoActual; }
    public void setModoActual(String modo) { this.modoActual = modo; }
    public boolean isVozActiva()           { return vozActiva; }
    public void setVozActiva(boolean voz)  { this.vozActiva = voz; }
    public String getNombre()              { return nombre; }
    public void setNombre(String nombre)   { this.nombre = nombre; }
}