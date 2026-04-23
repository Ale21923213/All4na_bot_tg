package com.miPortafolio.finanzas_api;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class ScraperLoL {

    public String obtenerUltimoParche() {
        try {
            // URL oficial de las notas del parche
            String url = "https://www.leagueoflegends.com/es-mx/news/game-updates/";
            Document doc = Jsoup.connect(url).get();

            // Extraemos el primer artículo de noticias (el más reciente)
            String titulo = doc.select("h2").first().text();
            String link = doc.select("a[data-testid='article-card']").first().attr("abs:href");

            // Entramos al link para leer el contenido real
            Document parcheDoc = Jsoup.connect(link).get();
            String contenido = parcheDoc.select("div.article-column-content").text();

            return "NOTAS OFICIALES (" + titulo + "):\n" + (contenido.length() > 2000 ? contenido.substring(0, 2000) : contenido);
        } catch (Exception e) {
            return "No pude conectar con el servidor de Riot: " + e.getMessage();
        }
    }
}
