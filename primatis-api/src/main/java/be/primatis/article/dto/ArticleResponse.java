package be.primatis.article.dto;

import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;
import be.primatis.article.Tag;
import be.primatis.user.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code Article} (DEV-11.3), destiné à la
 * fois à la future consultation publique ({@code PUBLISHED} uniquement,
 * business-rules.md §7.14/DEV-DEC-0061) et à la future gestion staff
 * ({@code ARTICLE_READ}/{@code ARTICLE_MANAGE}). Un seul contrat pour les
 * deux usages — {@code Article} ne possède aucune collection lourde
 * comparable à {@code Title.authors}/{@code Title.genres} justifiant une
 * séparation {@code Response}/{@code DetailResponse} (contrairement à
 * {@code catalogue.dto.TitleResponse}/{@code TitleDetailResponse}) ;
 * `content` reste la donnée centrale de la ressource, jamais différée à un
 * second appel.
 *
 * <p>{@code author}/{@code lastModifiedBy} sont des résumés compacts
 * ({@link ArticleUserResponse}), jamais l'Entity {@code AppUser} exposée
 * directement. {@code author} n'est jamais {@code null} ({@code
 * authorUser} est {@code NOT NULL} en base). {@code lastModifiedBy} est
 * {@code null} tant qu'aucune action persistée significative n'a encore eu
 * lieu après la création (DEV-DEC-0059, business-rules.md §7.12) —
 * reflète strictement l'état persisté, aucune valeur de repli substituée
 * (jamais {@code author} recopié comme valeur par défaut).
 *
 * <p>{@code tags} provient d'une liste {@code Tag} fournie explicitement
 * par l'appelant (même précédent exact que {@code
 * TitleDetailResponse#from(Title, List, List)}) : {@code Article}
 * n'expose aucune collection bidirectionnelle vers {@code ArticleTag}/
 * {@code Tag} (relation unidirectionnelle, {@code database-model.md}
 * §20.3), la résolution de cette liste appartient au futur Repository/
 * Service (hors scope DEV-11.3). {@code null} → liste vide ; un élément
 * {@code null} au sein d'une liste non nulle échoue explicitement (via
 * {@link TagResponse#from}) ; l'ordre reçu est conservé tel quel, aucun tri
 * inventé ici (un futur besoin de tri déterministe reste un contrat du
 * Repository/Service appelant, cf. log DEV-11.3 §16).
 *
 * <p>{@code articleStatus} exposé tel quel (enum {@link ArticleStatus}, pas
 * de traduction). Aucun champ calculé/artificiel (par exemple {@code
 * isPublished}/{@code canPublish}/{@code canArchive}) : le DTO reflète
 * l'état persistant, jamais une décision UI ou une règle métier
 * précalculée (même principe que {@code reservation.dto.ReservationResponse}).
 */
public record ArticleResponse(
        Long id,
        String title,
        String content,
        String summary,
        String slug,
        ArticleStatus articleStatus,
        ArticleUserResponse author,
        ArticleUserResponse lastModifiedBy,
        List<TagResponse> tags,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static ArticleResponse from(Article article, List<Tag> tags) {
        Objects.requireNonNull(article, "article");

        AppUser lastModifiedByUser = article.getLastModifiedByUser();

        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getSummary(),
                article.getSlug(),
                article.getArticleStatus(),
                ArticleUserResponse.from(article.getAuthorUser()),
                lastModifiedByUser == null ? null : ArticleUserResponse.from(lastModifiedByUser),
                tags == null ? List.of() : tags.stream().map(TagResponse::from).toList(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                article.getUpdatedAt());
    }
}
