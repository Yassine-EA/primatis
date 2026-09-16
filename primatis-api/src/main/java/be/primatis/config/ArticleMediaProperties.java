package be.primatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration technique du stockage des images intégrées au contenu
 * riche d'un Article (DEV-ARTICLES-MEDIA, DEV-DEC-0079). {@code
 * storagePath} : répertoire filesystem où les fichiers sont réellement
 * écrits — ne pas confondre avec {@link #PUBLIC_PATH_PREFIX}, l'URL
 * publique exposée au navigateur (séparation explicite filesystem/URL,
 * mission DEV-ARTICLES-MEDIA §12). N'appartient pas à {@code
 * application_setting} (configuration technique, pas un paramètre
 * métier) — même principe exact que {@link CorsProperties}.
 */
@ConfigurationProperties(prefix = "primatis.article-media")
public record ArticleMediaProperties(String storagePath) {

    /**
     * Préfixe d'URL publique servant ces fichiers ({@code
     * ArticleMediaWebConfig}, {@code SecurityConfig}) — structurel,
     * jamais configurable (contrairement à {@link #storagePath}, qui
     * varie par environnement).
     */
    public static final String PUBLIC_PATH_PREFIX = "/media/articles/";
}
