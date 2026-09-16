/**
 * Voir `be.primatis.article.dto.ArticleMediaUploadResponse` côté backend
 * (DEV-ARTICLES-MEDIA). `url` est une URL publique relative
 * (`/media/articles/<uuid>.<ext>`), jamais un chemin filesystem.
 */
export interface ArticleMediaUploadResponse {
  url: string;
}
