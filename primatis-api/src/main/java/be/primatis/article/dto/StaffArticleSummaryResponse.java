package be.primatis.article.dto;

import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrat REST de lecture allégé pour la liste staff d'Articles (DEV-11.12A,
 * {@code GET /api/v1/staff/articles}, {@code ARTICLE_MANAGE}). Distinct de
 * {@link ArticleSummaryResponse} (public, DEV-11.5) : ce dernier expose
 * délibérément un Article toujours {@code PUBLISHED} et omet donc
 * {@code articleStatus}/{@code updatedAt} comme non informatifs dans ce
 * contexte (voir sa Javadoc) — exactement l'inverse du besoin staff, où
 * distinguer {@code DRAFT}/{@code PUBLISHED}/{@code ARCHIVED} et savoir ce
 * qui a été modifié récemment est le but même de cette liste. Réutiliser
 * {@link ArticleSummaryResponse} ici aurait donc contredit sa propre
 * justification de conception plutôt que de l'étendre.
 *
 * <p>Même principe que {@link ArticleSummaryResponse} pour le reste :
 * volontairement allégé par rapport à {@link ArticleResponse} — {@code
 * content} (jamais nécessaire à une ligne de liste), {@code lastModifiedBy}
 * et {@code tags} (aucun besoin staff démontré pour la liste ; disponibles
 * via le détail, {@code GET .../{articleId}}) restent exclus, évitant tout
 * coût réseau/N+1 inutile sur une page potentiellement volumineuse. {@code
 * publishedAt} reste nullable ({@code null} pour un {@code DRAFT},
 * contrairement à {@link ArticleSummaryResponse} qui ne l'expose jamais
 * pour un Article non {@code PUBLISHED}).
 */
public record StaffArticleSummaryResponse(
        Long id,
        String title,
        String summary,
        String slug,
        ArticleStatus articleStatus,
        ArticleUserResponse author,
        Instant publishedAt,
        Instant updatedAt) {

    public static StaffArticleSummaryResponse from(Article article) {
        Objects.requireNonNull(article, "article");
        return new StaffArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSlug(),
                article.getArticleStatus(),
                ArticleUserResponse.from(article.getAuthorUser()),
                article.getPublishedAt(),
                article.getUpdatedAt());
    }
}
