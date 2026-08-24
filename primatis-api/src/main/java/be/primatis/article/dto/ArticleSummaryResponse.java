package be.primatis.article.dto;

import be.primatis.article.Article;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrat REST de lecture allégé pour la liste publique d'Articles
 * (DEV-11.5, {@code GET /api/v1/articles}). Introduit après réévaluation
 * du cas d'usage réel de la liste (mission DEV-11.5 §7) : {@link
 * ArticleResponse} (DEV-11.3) reste le contrat de détail (un seul Article,
 * {@code content} central, jamais différé), mais envoyer le HTML complet
 * de chaque Article dans une page de résultats n'apporte aucune valeur à
 * un visiteur parcourant une liste et alourdirait inutilement la réponse.
 *
 * <p>Champs volontairement exclus par rapport à {@link ArticleResponse},
 * chacun justifié :
 * <ul>
 *   <li>{@code content} — jamais nécessaire à l'affichage d'une liste,
 *   {@code summary} suffit ; coût réseau évité sur potentiellement 100
 *   lignes (taille de page maximale, business-rules.md §7.14) ;</li>
 *   <li>{@code lastModifiedBy} — aucune utilité publique identifiée (mission
 *   §8) : un visiteur consultant une liste d'actualités n'a pas besoin de
 *   savoir qui a corrigé une coquille après publication, à la différence
 *   de {@code author} qui reste éditorialement pertinent ;</li>
 *   <li>{@code tags} — exclu de la liste pour éviter tout N+1 (résolution
 *   par Article, {@code ArticleRepository.findTagsByArticleId}, réservée
 *   au détail où un seul Article est concerné) ; aucun filtrage public par
 *   Tag n'existe de toute façon en DEV-11 (DEV-DEC-0061) ;</li>
 *   <li>{@code articleStatus}/{@code createdAt}/{@code updatedAt} — cette
 *   liste n'expose jamais que des Articles {@code PUBLISHED}
 *   (business-rules.md §7.14) : le statut serait toujours identique,
 *   aucune valeur informative pour le public.</li>
 * </ul>
 *
 * <p>{@code author} reste un résumé compact ({@link ArticleUserResponse}),
 * jamais l'Entity {@code AppUser} exposée directement.
 */
public record ArticleSummaryResponse(
        Long id, String title, String summary, String slug, ArticleUserResponse author, Instant publishedAt) {

    public static ArticleSummaryResponse from(Article article) {
        Objects.requireNonNull(article, "article");
        return new ArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getSlug(),
                ArticleUserResponse.from(article.getAuthorUser()),
                article.getPublishedAt());
    }
}
