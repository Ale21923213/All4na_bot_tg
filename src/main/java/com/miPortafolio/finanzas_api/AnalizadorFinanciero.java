package com.miPortafolio.finanzas_api;

import org.springframework.stereotype.Service;

@Service
public class AnalizadorFinanciero {

    /**
     * Calcula el ingreso mensual equivalente según la frecuencia declarada.
     */
    public double calcularIngresoMensual(String frecuencia, double monto) {
        return switch (frecuencia) {
            case "DIARIO"    -> monto * 26; // días laborables promedio
            case "SEMANAL"   -> monto * 4.33;
            case "QUINCENAL" -> monto * 2;
            default          -> monto; // MENSUAL
        };
    }

    /**
     * Calcula gastos mensuales totales según el perfil.
     */
    public double calcularGastosMensuales(PerfilFinanciero p) {
        double transporte = 0;
        if (p.getCostoTransporte() != null && !"PIE".equals(p.getTipoTransporte())) {
            transporte = switch (p.getTipoTransporte() != null ? p.getTipoTransporte() : "") {
                case "CARRO" -> p.getCostoTransporte() * 4.33; // semanal a mensual
                default      -> p.getCostoTransporte() * 26;   // diario a mensual
            };
        }
        double alimentacion = p.getGastoAlimentacion() != null ? p.getGastoAlimentacion() * 30 : 0;
        double fijos        = p.getGastosFijos() != null ? p.getGastosFijos() : 0;
        return transporte + alimentacion + fijos;
    }

    /**
     * Calcula y recomienda el método de ahorro más adecuado.
     * Retorna el perfil con todos los campos calculados.
     */
    public PerfilFinanciero calcularYRecomendar(PerfilFinanciero perfil) {
        double ingresoMensual  = calcularIngresoMensual(perfil.getFrecuenciaIngreso(), perfil.getMontoIngreso());
        double gastosMensuales = calcularGastosMensuales(perfil);
        double disponible      = ingresoMensual - gastosMensuales;

        perfil.setIngresoMensual(redondear(ingresoMensual));
        perfil.setGastosMensuales(redondear(gastosMensuales));
        perfil.setDisponibleMensual(redondear(disponible));

        // Selección del método según ingreso y capacidad de ahorro
        String metodo;
        double porcentaje;

        if (disponible <= 0) {
            // Gastos superan ingresos — método de rescate
            metodo     = "RESCATE";
            porcentaje = 0;
        } else {
            double ratioDisponible = disponible / ingresoMensual;

            if (ingresoMensual < 400) {
                // Ingreso bajo: método del sobre, objetivo conservador
                metodo     = "SOBRE";
                porcentaje = Math.min(10, ratioDisponible * 100 * 0.6);
            } else if (ingresoMensual < 900) {
                // Ingreso medio: págate primero a ti mismo
                metodo     = "PAGATE_PRIMERO";
                porcentaje = Math.min(20, ratioDisponible * 100 * 0.65);
            } else {
                // Ingreso alto: regla 50/30/20
                metodo     = "50_30_20";
                porcentaje = 20;
            }
        }

        double ahorroMensual = ingresoMensual * porcentaje / 100;
        perfil.setMetodoAhorro(metodo);
        perfil.setPorcentajeAhorro(redondear(porcentaje));
        perfil.setAhorroMensualSugerido(redondear(ahorroMensual));

        return perfil;
    }

    /**
     * Genera el mensaje completo de recomendación para mostrar al usuario.
     */
    public String generarMensajeRecomendacion(PerfilFinanciero p, MetaAhorro meta) {
        StringBuilder sb = new StringBuilder();

        sb.append("Tu perfil financiero esta listo! 🎉\n\n");
        sb.append("📊 TU SITUACION ACTUAL\n");
        sb.append("Ingreso mensual estimado: $").append(p.getIngresoMensual()).append("\n");
        sb.append("Gastos mensuales estimados: $").append(p.getGastosMensuales()).append("\n");

        if (p.getDisponibleMensual() <= 0) {
            sb.append("\n⚠️ Atencion: tus gastos estimados superan tus ingresos.\n");
            sb.append("Lo primero es revisar donde puedes reducir gastos. ");
            sb.append("Cuéntame más sobre tus gastos y te ayudo a encontrar donde recortar.\n\n");
        } else {
            sb.append("Disponible para ahorrar: $").append(p.getDisponibleMensual()).append("\n\n");

            switch (p.getMetodoAhorro()) {
                case "SOBRE" -> {
                    sb.append("💡 METODO RECOMENDADO: El Sobre\n\n");
                    sb.append("Con tu ingreso, lo más efectivo es dividir tu dinero en sobres al recibir tu pago:\n");
                    sb.append("• Sobre 1 - Ahorro: $").append(p.getAhorroMensualSugerido()).append(" (").append(p.getPorcentajeAhorro()).append("%)\n");
                    sb.append("• Sobre 2 - Gastos fijos: lo que necesitas para transporte, comida y servicios\n");
                    sb.append("• Sobre 3 - Gastos variables: lo que queda\n\n");
                    sb.append("Este metodo es simple y funciona porque ves físicamente cuanto te queda.\n");
                }
                case "PAGATE_PRIMERO" -> {
                    sb.append("💡 METODO RECOMENDADO: Pagate primero a ti mismo\n\n");
                    sb.append("Tan pronto recibas tu pago, mueve el ahorro ANTES de gastar:\n");
                    sb.append("• Ahorro inmediato: $").append(p.getAhorroMensualSugerido()).append(" al mes (").append(p.getPorcentajeAhorro()).append("%)\n");
                    sb.append("• Con lo que queda, cubres tus gastos normalmente\n\n");
                    sb.append("La clave es que el ahorro no es \"lo que sobra\" sino lo primero que sale.\n");
                }
                case "50_30_20" -> {
                    sb.append("💡 METODO RECOMENDADO: Regla 50/30/20\n\n");
                    sb.append("Divide tu ingreso mensual de $").append(p.getIngresoMensual()).append(" así:\n");
                    sb.append("• 50% necesidades ($").append(redondear(p.getIngresoMensual() * 0.5)).append("): vivienda, comida, transporte\n");
                    sb.append("• 30% deseos ($").append(redondear(p.getIngresoMensual() * 0.3)).append("): entretenimiento, salidas\n");
                    sb.append("• 20% ahorro ($").append(p.getAhorroMensualSugerido()).append("): inversión en tu futuro\n");
                }
            }

            if (meta != null && meta.getMontoObjetivo() != null && p.getAhorroMensualSugerido() > 0) {
                double meses = meta.getMontoObjetivo() / p.getAhorroMensualSugerido();
                sb.append("\n🎯 TU META: ").append(meta.getNombre()).append("\n");
                sb.append("Objetivo: $").append(meta.getMontoObjetivo()).append("\n");
                sb.append("Ahorrando $").append(p.getAhorroMensualSugerido()).append("/mes, ");
                sb.append("lo alcanzas en aproximadamente ").append((int) Math.ceil(meses)).append(" meses.\n");
            }
        }

        sb.append("\n✅ Listo! Ya puedes contarme tus gastos de forma natural:\n");
        sb.append("• \"Gaste $5 en el bus\"\n");
        sb.append("• \"Almorce por $3.50\"\n");
        sb.append("• \"Pague $25 de internet\"\n\n");
        sb.append("Los voy registrando automaticamente. Escribe /resumen para ver tu balance en cualquier momento.");

        return sb.toString();
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
