package be.primatis.article.dto;

/**
 * Réponse de l'upload d'une image destinée à {@code Article.content}
 * (DEV-ARTICLES-MEDIA). {@code url} est une URL publique relative
 * ({@code /media/articles/<uuid>.<ext>}) — jamais un chemin filesystem
 * (mission §9 : ne jamais exposer {@code /home/...}/{@code C:\...}).
 */
public record ArticleMediaUploadResponse(String url) {
}
