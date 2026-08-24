package be.primatis.article;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * slug est l'identifiant public de navigation d'un Article
     * (GET /api/v1/articles/:slug). Historique (scaffold DEV-01/02) : ne
     * filtre pas par statut, conservée telle quelle pour les futurs
     * workflows staff/génération de slug (DEV-11.6+ : unicité candidate,
     * édition). <b>Jamais utilisée pour la consultation publique</b> (DEV-11.5,
     * mission §20) — {@link #findBySlugAndArticleStatus} lui est préférée
     * pour ce cas, afin d'encoder {@code PUBLISHED} directement dans la
     * requête plutôt que de filtrer après coup.
     */
    Optional<Article> findBySlug(String slug);

    /**
     * Consultation publique paginée (DEV-11.5, business-rules.md §7.14,
     * DEV-DEC-0061) : encode {@code articleStatus = :articleStatus}
     * directement dans la requête, jamais un filtrage a posteriori dans le
     * Service/Controller (mission DEV-11.5 §3). {@code @EntityGraph} sur
     * {@code authorUser} uniquement : {@link
     * be.primatis.article.dto.ArticleSummaryResponse#from} n'accède qu'à
     * cette relation {@code LAZY} (jamais {@code lastModifiedByUser}, exclu
     * du résumé public) — même précédent exact que {@code
     * ReservationRepository.findByUserId} (DEV-08.4) : une seule relation
     * {@code @ManyToOne}, jamais une collection {@code *ToMany}, donc
     * aucun risque de doublon de ligne ni de pagination en mémoire en
     * combinaison avec {@link Pageable}.
     */
    @EntityGraph(attributePaths = {"authorUser"})
    Page<Article> findByArticleStatus(ArticleStatus articleStatus, Pageable pageable);

    /**
     * Détail public par slug (DEV-11.5, business-rules.md §7.14) : encode
     * {@code slug + articleStatus = PUBLISHED} directement dans la requête
     * — un {@code DRAFT}/{@code ARCHIVED} existant sous ce slug ne doit
     * jamais être atteignable par ce chemin (mission §3/§15 : indistinguable
     * publiquement d'un slug inexistant, jamais un 403/leak de statut).
     * {@code @EntityGraph} sur {@code authorUser}/{@code lastModifiedByUser} :
     * {@link be.primatis.article.dto.ArticleResponse#from} accède
     * inconditionnellement à {@code authorUser} et, si non {@code null}, à
     * {@code lastModifiedByUser} — deux relations {@code @ManyToOne}, même
     * raisonnement de sûreté que ci-dessus. Un seul résultat possible par
     * {@code slug} ({@code uq_article_slug}, V001).
     */
    @EntityGraph(attributePaths = {"authorUser", "lastModifiedByUser"})
    Optional<Article> findBySlugAndArticleStatus(String slug, ArticleStatus articleStatus);

    /**
     * Tags associés à un Article, résolus via {@code ArticleTag} (DEV-11.5)
     * — {@code Article} n'expose aucune collection bidirectionnelle vers
     * {@code ArticleTag}/{@code Tag} (relation unidirectionnelle,
     * database-model.md §20.3, non modifiée). Une seule requête pour
     * l'unique Article du détail public (jamais appelée en boucle sur une
     * page de résultats — la liste publique, {@link
     * be.primatis.article.dto.ArticleSummaryResponse}, n'expose délibérément
     * pas les tags, évitant ainsi tout N+1 sur ce point pour la liste).
     * Tri par {@code tag.id} croissant : déterministe, IMPLEMENTATION
     * FREEDOM (aucune source ne fixe d'ordre d'affichage des tags d'un
     * Article) — {@code ArticleResponse.from} ne réordonne jamais ce qui
     * lui est fourni (DEV-11.3).
     */
    @Query("SELECT at.tag FROM ArticleTag at WHERE at.article.id = :articleId ORDER BY at.tag.id ASC")
    List<Tag> findTagsByArticleId(@Param("articleId") Long articleId);

    /**
     * Chargement verrouillé (SELECT ... FOR UPDATE), réservé à la
     * publication (DEV-11.7) — même précédent exact que {@code
     * LoanRepository.findByIdForUpdate}/{@code CopyRepository.findByIdForUpdate} :
     * charge et verrouille en une seule opération, aucune fenêtre non
     * verrouillée entre le chargement et le premier contrôle d'{@code
     * articleStatus}, revalidation post-lock donc structurellement garantie
     * (le verrou est détenu en continu jusqu'au commit). Deux publications
     * concurrentes sur le même Article se sérialisent réellement au niveau
     * PostgreSQL : le second thread se bloque jusqu'au commit du premier,
     * puis découvre {@code articleStatus == PUBLISHED} et rejette proprement
     * ({@code ARTICLE_NOT_PUBLISHABLE}), jamais une double diffusion
     * {@code ARTICLE_PUBLISHED}. Réservé à ce workflow — {@link
     * #findById(Object)}/les autres méthodes restent non verrouillées pour
     * tous les autres usages (consultation, DEV-11.5/11.6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Article a WHERE a.id = :id")
    Optional<Article> findByIdForUpdate(@Param("id") Long id);

    /**
     * Liste staff paginée (DEV-11.12A, {@code ARTICLE_MANAGE}) : tous
     * statuts confondus (DRAFT/PUBLISHED/ARCHIVED), contrairement à {@link
     * #findByArticleStatus} (public, PUBLISHED uniquement) — même principe
     * exact que {@code CatalogueService.searchStaffTitles} pour {@code
     * TitleStatus.WITHDRAWN}. Tri déterministe imposé ({@code updatedAt
     * DESC, id DESC}) — IMPLEMENTATION FREEDOM, aucune source ne fixe
     * d'ordre pour cette liste ; retenu comme le plus utile à une gestion
     * staff (Articles récemment modifiés en premier), le second critère
     * (id) restant un tie-break déterministe défensif. {@code @EntityGraph}
     * sur {@code authorUser} uniquement : {@link
     * be.primatis.article.dto.StaffArticleSummaryResponse#from} n'accède
     * qu'à cette relation {@code LAZY} (jamais {@code lastModifiedByUser},
     * exclu du résumé staff) — même raisonnement exact que {@link
     * #findByArticleStatus}.
     */
    @EntityGraph(attributePaths = {"authorUser"})
    Page<Article> findAllByOrderByUpdatedAtDescIdDesc(Pageable pageable);

    /**
     * Détail staff par id (DEV-11.12A, {@code ARTICLE_MANAGE}) : visible
     * quel que soit {@code articleStatus} (contrairement à {@link
     * #findBySlugAndArticleStatus}, public, PUBLISHED uniquement) — même
     * précédent exact que {@code CatalogueService.getStaffTitleById} pour
     * {@code TitleStatus.WITHDRAWN}. {@code @EntityGraph} identique à
     * {@link #findBySlugAndArticleStatus} ({@code authorUser} +
     * {@code lastModifiedByUser}) : {@link
     * be.primatis.article.dto.ArticleResponse} est réutilisé tel quel pour
     * le détail staff (même précédent que {@code TitleDetailResponse},
     * partagé public/staff côté Catalogue), donc les mêmes relations sont
     * nécessaires.
     */
    @EntityGraph(attributePaths = {"authorUser", "lastModifiedByUser"})
    @Query("SELECT a FROM Article a WHERE a.id = :id")
    Optional<Article> findByIdWithUsers(@Param("id") Long id);
}
