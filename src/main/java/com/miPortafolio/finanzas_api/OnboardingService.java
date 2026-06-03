package com.miPortafolio.finanzas_api;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Service
public class OnboardingService {

    private final UsuarioConfigRepository    usuarioRepo;
    private final PerfilFinancieroRepository perfilRepo;
    private final MetaAhorroRepository       metaRepo;
    private final AnalizadorFinanciero       analizador;
    private final AsistenteBot               bot;

    // Nombre temporal de meta durante onboarding (en memoria)
    private final java.util.Map<Long, String> tempMetaNombre = new java.util.concurrent.ConcurrentHashMap<>();

    public OnboardingService(UsuarioConfigRepository usuarioRepo,
                             PerfilFinancieroRepository perfilRepo,
                             MetaAhorroRepository metaRepo,
                             AnalizadorFinanciero analizador,
                             @Lazy AsistenteBot bot) {
        this.usuarioRepo = usuarioRepo;
        this.perfilRepo  = perfilRepo;
        this.metaRepo    = metaRepo;
        this.analizador  = analizador;
        this.bot         = bot;
    }

    public boolean estaEnOnboarding(UsuarioConfig config) {
        String etapa = config.getEtapaOnboarding();
        return etapa == null || !"COMPLETADO".equals(etapa);
    }

    /**
     * Inicia o reinicia el onboarding para un usuario.
     */
    public void iniciar(long chatId, String nombre) {
        UsuarioConfig config = usuarioRepo.findById(chatId)
                .orElse(new UsuarioConfig(chatId, nombre));
        config.setNombre(nombre);
        config.setEtapaOnboarding("NUEVO");
        usuarioRepo.save(config);

        // Limpiar perfil anterior si existe
        perfilRepo.findById(chatId).ifPresent(p -> {
            p.setFrecuenciaIngreso(null); p.setMontoIngreso(null);
            p.setTipoTransporte(null);    p.setCostoTransporte(null);
            p.setGastoAlimentacion(null); p.setGastosFijos(null);
            perfilRepo.save(p);
        });

        // Enviar bienvenida
        String saludo = nombre != null && !nombre.isBlank() ? nombre : "!";
        bot.enviarMensaje(chatId,
                "Hola " + saludo + "! 👋\n\n" +
                        "Soy Alana, tu asistente financiera personal. Estoy aqui para ayudarte " +
                        "a entender mejor tu dinero y ahorrar sin sacrificar tu estilo de vida. 💚\n\n" +
                        "Para empezar, dime: con que frecuencia recibes tus ingresos?");

        bot.enviarConBotones(chatId, "Elige tu frecuencia:", List.of(
                List.of(boton("📅 Diario", "FREQ_DIARIO"), boton("📅 Semanal", "FREQ_SEMANAL")),
                List.of(boton("📅 Quincenal", "FREQ_QUINCENAL"), boton("📅 Mensual", "FREQ_MENSUAL"))
        ));

        config.setEtapaOnboarding("FRECUENCIA_INGRESO");
        usuarioRepo.save(config);
    }

    public void procesarCallback(long chatId, String data) {
        UsuarioConfig config = usuarioRepo.findById(chatId).orElse(null);
        if (config == null) return;

        String etapa = config.getEtapaOnboarding();
        if (etapa == null) etapa = "NUEVO";

        PerfilFinanciero perfil = perfilRepo.findById(chatId)
                .orElseGet(() -> perfilRepo.save(new PerfilFinanciero(chatId)));

        switch (etapa) {
            case "FRECUENCIA_INGRESO" -> {
                String frecuencia = data.replace("FREQ_", "");
                perfil.setFrecuenciaIngreso(frecuencia);
                perfilRepo.save(perfil);

                String label = switch (frecuencia) {
                    case "DIARIO" -> "al dia"; case "SEMANAL" -> "a la semana";
                    case "QUINCENAL" -> "quincenal"; default -> "al mes";
                };
                bot.enviarMensaje(chatId,
                        "Perfecto! ✅\n\n¿Cuánto ganas " + label + "? " +
                                "Puedes darme un aproximado.");
                config.setEtapaOnboarding("MONTO_INGRESO");
                usuarioRepo.save(config);
            }

            case "TIPO_TRANSPORTE" -> {
                String tipo = data.replace("TRANS_", "");
                perfil.setTipoTransporte(tipo);
                perfilRepo.save(perfil);

                if ("PIE".equals(tipo)) {
                    perfil.setCostoTransporte(0.0);
                    perfilRepo.save(perfil);
                    config.setEtapaOnboarding("GASTO_ALIMENTACION");
                    usuarioRepo.save(config);
                    bot.enviarMensaje(chatId,
                            "Genial, ¡caminar es la opción más económica y saludable! 🚶\n\n" +
                                    "Y en alimentación, ¿cuánto gastas en promedio al día? " +
                                    "(desayuno, almuerzo, merienda... todo incluido)");
                } else {
                    String unidad = "CARRO".equals(tipo) ? "a la semana en combustible" : "al día en transporte";
                    bot.enviarMensaje(chatId,
                            "¿Cuánto gastas aproximadamente " + unidad + "?");
                    config.setEtapaOnboarding("COSTO_TRANSPORTE");
                    usuarioRepo.save(config);
                }
            }

            case "META_PREGUNTA" -> {
                if ("META_SI".equals(data)) {
                    config.setEtapaOnboarding("META_NOMBRE");
                    usuarioRepo.save(config);
                    bot.enviarMensaje(chatId,
                            "¡Me encanta que tengas una meta! 🌟\n\n" +
                                    "¿Cómo se llama? Descríbela brevemente, por ejemplo:\n" +
                                    "\"Moto Pulsar\", \"Viaje a Cuenca\", \"Fondo de emergencia\"");
                } else {
                    completarOnboarding(chatId, perfil, null, config);
                }
            }
        }
    }

    public void procesarTexto(long chatId, String texto) {
        UsuarioConfig config = usuarioRepo.findById(chatId).orElse(null);
        if (config == null) return;

        String etapa = config.getEtapaOnboarding();
        if (etapa == null) etapa = "NUEVO";

        PerfilFinanciero perfil = perfilRepo.findById(chatId)
                .orElseGet(() -> perfilRepo.save(new PerfilFinanciero(chatId)));

        switch (etapa) {
            case "NUEVO" -> iniciar(chatId, config.getNombre());

            case "MONTO_INGRESO" -> {
                Double monto = parsearNumero(texto);
                if (monto == null) {
                    bot.enviarMensaje(chatId, "Entiendo. Para poder hacer bien los cálculos de tu perfil, ¿podrías darme un número estimado? Por ejemplo: 400");
                    return;
                }
                perfil.setMontoIngreso(monto);
                perfilRepo.save(perfil);

                bot.enviarMensaje(chatId, "¡Anotado! 📝 Ahora dime, ¿cómo te movilizas normalmente?");
                bot.enviarConBotones(chatId, "Tu transporte principal:", List.of(
                        List.of(boton("🚌 Bus/Metro", "TRANS_BUS"), boton("🚗 Carro propio", "TRANS_CARRO")),
                        List.of(boton("🏍️ Moto", "TRANS_MOTO"), boton("🚶 A pie", "TRANS_PIE"))
                ));
                config.setEtapaOnboarding("TIPO_TRANSPORTE");
                usuarioRepo.save(config);
            }

            case "COSTO_TRANSPORTE" -> {
                Double costo = parsearNumero(texto);
                if (costo == null) {
                    bot.enviarMensaje(chatId, "Comprendo. Es normal que a veces varíe, pero para tener una idea general, ¿cuánto dirías que gastas en promedio? Dame un aproximado (ej: 2.50).");
                    return;
                }
                perfil.setCostoTransporte(costo);
                perfilRepo.save(perfil);

                config.setEtapaOnboarding("GASTO_ALIMENTACION");
                usuarioRepo.save(config);
                bot.enviarMensaje(chatId,
                        "¡Perfecto! 🍽️ Y en alimentación, ¿cuánto gastas aproximadamente al día? " +
                                "(desayuno, almuerzo, merienda... todo incluido)");
            }

            case "GASTO_ALIMENTACION" -> {
                Double gasto = parsearNumero(texto);
                if (gasto == null) {
                    bot.enviarMensaje(chatId, "Te entiendo perfecto, la comida varía mucho dependiendo de si llevas de casa o si te toca comprar por fuera. 🍱\n\nPara poder armar tu plan de ahorro, intenta calcular un promedio diario. ¿Más o menos cuánto dirías que es en un día normal? (ej: 3.50)");
                    return;
                }
                perfil.setGastoAlimentacion(gasto);
                perfilRepo.save(perfil);

                config.setEtapaOnboarding("GASTOS_FIJOS");
                usuarioRepo.save(config);
                bot.enviarMensaje(chatId,
                        "¡Casi terminamos! 🙌\n\n" +
                                "¿Tienes gastos fijos mensuales? Por ejemplo: arriendo, servicios básicos, " +
                                "internet, suscripciones...\n\n" +
                                "Dame el total aproximado al mes. Si no tienes o prefieres no ponerlos aún, escribe 0.");
            }

            case "GASTOS_FIJOS" -> {
                Double fijos = parsearNumero(texto);
                if (fijos == null) {
                    bot.enviarMensaje(chatId, "Tranquilo, si los gastos varían mucho solo dame un promedio aproximado mensual. Si definitivamente no tienes gastos fijos, escribe 0.");
                    return;
                }
                perfil.setGastosFijos(fijos);
                perfilRepo.save(perfil);

                config.setEtapaOnboarding("META_PREGUNTA");
                usuarioRepo.save(config);
                bot.enviarConBotones(chatId,
                        "¿Tienes alguna meta de ahorro en mente? 🎯\n" +
                                "(una moto, un viaje, un fondo de emergencia...)",
                        List.of(List.of(
                                boton("✅ Sí, tengo una meta", "META_SI"),
                                boton("➡️ No por ahora", "META_NO")
                        ))
                );
            }

            case "META_NOMBRE" -> {
                tempMetaNombre.put(chatId, texto.trim());
                config.setEtapaOnboarding("META_MONTO");
                usuarioRepo.save(config);
                bot.enviarMensaje(chatId,
                        "¡Excelente meta, " + texto.trim() + "! 🎯\n\n" +
                                "¿Cuánto dinero estimas que necesitas ahorrar para alcanzarla?");
            }

            case "META_MONTO" -> {
                Double montoMeta = parsearNumero(texto);
                if (montoMeta == null) {
                    bot.enviarMensaje(chatId, "Para poder ayudarte a trazar el plan, necesito que me des un valor aproximado en números, por ejemplo: 1500");
                    return;
                }

                String nombreMeta = tempMetaNombre.getOrDefault(chatId, "Tu meta");
                MetaAhorro meta   = new MetaAhorro(chatId, nombreMeta, montoMeta);
                metaRepo.save(meta);
                tempMetaNombre.remove(chatId);

                completarOnboarding(chatId, perfil, meta, config);
            }
        }
    }

    private void completarOnboarding(long chatId, PerfilFinanciero perfil,
                                     MetaAhorro meta, UsuarioConfig config) {
        analizador.calcularYRecomendar(perfil);
        perfilRepo.save(perfil);

        config.setEtapaOnboarding("COMPLETADO");
        usuarioRepo.save(config);

        String mensaje = analizador.generarMensajeRecomendacion(perfil, meta);
        bot.enviarMensaje(chatId, mensaje);
    }

    /**
     * ✅ MEJORA: Extrae el primer número que encuentre dentro de una oración.
     * Ejemplo: "A veces gasto 3.50 pero otros dias nada" -> Extrae 3.50
     */
    private Double parsearNumero(String texto) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)").matcher(texto);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1).replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null; // Si no encontró ni un solo número en la frase
    }

    private InlineKeyboardButton boton(String texto, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(texto); b.setCallbackData(data); return b;
    }
}