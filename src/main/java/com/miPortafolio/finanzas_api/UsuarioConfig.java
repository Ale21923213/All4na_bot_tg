package com.miPortafolio.finanzas_api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class UsuarioConfig {

    @Id
    private Long chatId;

    private String nombre = "Usuario";

    // Etapas del onboarding:
    // NUEVO → FRECUENCIA_INGRESO → MONTO_INGRESO → TIPO_TRANSPORTE →
    // COSTO_TRANSPORTE → GASTO_ALIMENTACION → GASTOS_FIJOS →
    // META_PREGUNTA → META_NOMBRE → META_MONTO → COMPLETADO
    private String etapaOnboarding = "NUEVO";

    public UsuarioConfig() {}

    public UsuarioConfig(Long chatId, String nombre) {
        this.chatId = chatId;
        this.nombre = nombre;
    }

    public Long getChatId()                          { return chatId; }
    public void setChatId(Long chatId)               { this.chatId = chatId; }
    public String getNombre()                        { return nombre; }
    public void setNombre(String nombre)             { this.nombre = nombre; }
    public String getEtapaOnboarding()               { return etapaOnboarding; }
    public void setEtapaOnboarding(String etapa)     { this.etapaOnboarding = etapa; }
}