package com.miPortafolio.finanzas_api;

import org.springframework.stereotype.Service;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class VozAll4na {

    public File generarAudio(String texto) {
        String nombreUnico      = "audio_" + System.currentTimeMillis();
        File archivoOriginal    = new File(nombreUnico + "_lento.mp3");
        File archivoAcelerado   = new File(nombreUnico + "_rapido.mp3");

        try {
            // 🚀 SOLUCIÓN: Limpiamos los asteriscos, guiones bajos y otros caracteres de Markdown
            // Así Telegram muestra las negritas, pero la voz lee el texto limpio.
            String textoLimpio = texto.replaceAll("[*_~#`]", "").trim();

            // Aseguramos que termine con un signo de puntuación para la entonación final
            if (!textoLimpio.endsWith(".") && !textoLimpio.endsWith("!") && !textoLimpio.endsWith("?")) {
                textoLimpio += ".";
            }

            List<String> fragmentos = dividirTexto(textoLimpio, 180);

            try (FileOutputStream fos = new FileOutputStream(archivoOriginal)) {
                for (String fragmento : fragmentos) {
                    String textoUrl = URLEncoder.encode(fragmento, StandardCharsets.UTF_8);
                    String urlString = "https://translate.google.com/translate_tts?ie=UTF-8&tl=es-MX&client=tw-ob&ttsspeed=1&q=" + textoUrl;

                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    if (conn.getResponseCode() == 200) {
                        try (InputStream in = conn.getInputStream()) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg", "-y", "-i", archivoOriginal.getAbsolutePath(),
                        "-filter:a", "atempo=1.35,apad=pad_dur=0.5",
                        archivoAcelerado.getAbsolutePath()
                );
                Process p = pb.start();
                p.waitFor();
                archivoOriginal.delete();
                return archivoAcelerado;
            } catch (Exception e) {
                System.err.println("Error en FFmpeg. Enviando versión normal...");
                return archivoOriginal;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<String> dividirTexto(String texto, int max) {
        List<String> partes   = new ArrayList<>();
        String[]     palabras = texto.split(" ");
        StringBuilder actual  = new StringBuilder();

        for (String palabra : palabras) {
            if (actual.length() + palabra.length() + 1 > max) {
                partes.add(actual.toString().trim());
                actual = new StringBuilder();
            }
            actual.append(palabra).append(" ");
        }
        if (!actual.isEmpty()) {
            partes.add(actual.toString().trim());
        }
        return partes;
    }
}