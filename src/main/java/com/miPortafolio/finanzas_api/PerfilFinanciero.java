package com.miPortafolio.finanzas_api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PerfilFinanciero {

    @Id
    private Long chatId;

    // Paso 1: ingresos
    private String frecuenciaIngreso; // DIARIO, SEMANAL, QUINCENAL, MENSUAL
    private Double montoIngreso;

    // Paso 2: transporte
    private String tipoTransporte;   // BUS, CARRO, MOTO, PIE
    private Double costoTransporte;  // gasto diario (o semanal si carro)

    // Paso 3: alimentación
    private Double gastoAlimentacion; // gasto diario

    // Paso 4: gastos fijos
    private Double gastosFijos;        // total mensual

    // Calculados al finalizar onboarding
    private Double ingresoMensual;
    private Double gastosMensuales;
    private Double disponibleMensual;
    private String metodoAhorro;
    private Double porcentajeAhorro;
    private Double ahorroMensualSugerido;

    public PerfilFinanciero() {}
    public PerfilFinanciero(Long chatId) { this.chatId = chatId; }

    public Long getChatId()                               { return chatId; }
    public void setChatId(Long chatId)                    { this.chatId = chatId; }
    public String getFrecuenciaIngreso()                  { return frecuenciaIngreso; }
    public void setFrecuenciaIngreso(String f)            { this.frecuenciaIngreso = f; }
    public Double getMontoIngreso()                       { return montoIngreso; }
    public void setMontoIngreso(Double m)                 { this.montoIngreso = m; }
    public String getTipoTransporte()                     { return tipoTransporte; }
    public void setTipoTransporte(String t)               { this.tipoTransporte = t; }
    public Double getCostoTransporte()                    { return costoTransporte; }
    public void setCostoTransporte(Double c)              { this.costoTransporte = c; }
    public Double getGastoAlimentacion()                  { return gastoAlimentacion; }
    public void setGastoAlimentacion(Double g)            { this.gastoAlimentacion = g; }
    public Double getGastosFijos()                        { return gastosFijos; }
    public void setGastosFijos(Double g)                  { this.gastosFijos = g; }
    public Double getIngresoMensual()                     { return ingresoMensual; }
    public void setIngresoMensual(Double i)               { this.ingresoMensual = i; }
    public Double getGastosMensuales()                    { return gastosMensuales; }
    public void setGastosMensuales(Double g)              { this.gastosMensuales = g; }
    public Double getDisponibleMensual()                  { return disponibleMensual; }
    public void setDisponibleMensual(Double d)            { this.disponibleMensual = d; }
    public String getMetodoAhorro()                       { return metodoAhorro; }
    public void setMetodoAhorro(String m)                 { this.metodoAhorro = m; }
    public Double getPorcentajeAhorro()                   { return porcentajeAhorro; }
    public void setPorcentajeAhorro(Double p)             { this.porcentajeAhorro = p; }
    public Double getAhorroMensualSugerido()              { return ahorroMensualSugerido; }
    public void setAhorroMensualSugerido(Double a)        { this.ahorroMensualSugerido = a; }
}