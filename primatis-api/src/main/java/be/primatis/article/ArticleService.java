package be.primatis.article;

import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.ArticleSummaryResponse;
import be.primatis.article.dto.CreateArticleRequest;
import be.primatis.article.dto.StaffArticleSummaryResponse;
import be.primatis.article.dto.UpdateArticleRequest;
import be.primatis.article.dto.UpdateArticleTagsRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.jsoup.Jsoup;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Consultation publique (DEV-11.5) et gestion staff des Articles
 * {@code DRAFT} (DEV-11.6) d'{@code Article}. Même précédent structurel que
 * {@code CatalogueService} (DEV-06.4) : un seul Service couvrant plusieurs
 * niveaux d'autorisation, {@code @PreAuthorize} appliqué par méthode,
 * jamais au niveau classe. Les deux méthodes de consultation publique
 * restent volontairement sans {@code @PreAuthorize} ({@code permitAll},
 * SecurityConfig, DEV-11.1 §13) ; {@link #createDraftArticle}/{@link
 * #updateArticle}/{@link #archiveArticle}/{@link #deleteDraftArticle}/{@link
 * #listStaffArticles}/{@link #getStaffArticleById} exigent {@code
 * ARTICLE_MANAGE} (jamais {@code ARTICLE_PUBLISH} — DEV-11.1 §32, déduit
 * mécaniquement : {@code ARTICLE_PUBLISH} gate exclusivement la transition
 * {@code DRAFT → PUBLISHED}, {@link #publishArticle}).
 *
 * <p>DEV-11.12A ajoute la lecture staff ({@link #listStaffArticles}/{@link
 * #getStaffArticleById}, tous statuts confondus) — corrective débloquant
 * DEV-11.12 (frontend staff), la surface publique ci-dessus restant
 * structurellement {@code PUBLISHED}-only.
 *
 * <p>DEV-11.8 ajoute l'édition d'un Article {@code PUBLISHED} (généralisation
 * de l'ancienne {@code updateDraftArticle} → {@link #updateArticle}, business-
 * rules.md §7.4), l'archivage ({@link #archiveArticle}, §7.5) et le hard-
 * delete {@code DRAFT} ({@link #deleteDraftArticle}, DEV-DEC-0058/§7.11).
 *
 * <p>DEV-11.9 ajoute l'association/dissociation de {@code Tag} existants
 * ({@link #associateTags}, DEV-DEC-0060) — jamais la création d'un {@code
 * Tag} à la volée, {@link TagService} reste l'unique autorité de gestion du
 * référentiel {@code Tag} (séparation stricte des responsabilités, même
 * précédent que {@code CatalogueService}/{@code CatalogueManagementService}).
 * {@link #deleteDraftArticle} nettoie désormais explicitement les {@code
 * ArticleTag} d'un {@code DRAFT} avant sa suppression physique (réconciliation
 * DEV-DEC-0058 + {@code fk_article_tag_article_id ON DELETE RESTRICT}, V001) —
 * un {@code DRAFT} tagué doit rester hard-deletable. Aucun fanout Notification
 * supplémentaire, aucun frontend.
 */
@Service
public class ArticleService {

    private static final String ARTICLE_NOT_FOUND_CODE = "ARTICLE_NOT_FOUND";
    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String ARTICLE_CONTENT_EMPTY_CODE = "ARTICLE_CONTENT_EMPTY";
    private static final String ARTICLE_TITLE_PRODUCES_EMPTY_SLUG_CODE = "ARTICLE_TITLE_PRODUCES_EMPTY_SLUG";
    private static final String ARTICLE_SLUG_ALREADY_EXISTS_CODE = "ARTICLE_SLUG_ALREADY_EXISTS";
    private static final String ARTICLE_NOT_EDITABLE_CODE = "ARTICLE_NOT_EDITABLE";
    private static final String ARTICLE_TITLE_MUST_NOT_BE_BLANK_CODE = "ARTICLE_TITLE_MUST_NOT_BE_BLANK";
    private static final String ARTICLE_CONTENT_MUST_NOT_BE_NULL_CODE = "ARTICLE_CONTENT_MUST_NOT_BE_NULL";
    private static final String ARTICLE_NOT_PUBLISHABLE_CODE = "ARTICLE_NOT_PUBLISHABLE";
    private static final String ARTICLE_NOT_ARCHIVABLE_CODE = "ARTICLE_NOT_ARCHIVABLE";
    private static final String ARTICLE_NOT_DELETABLE_CODE = "ARTICLE_NOT_DELETABLE";
    private static final String TAG_NOT_FOUND_CODE = "TAG_NOT_FOUND";

    private static final String ARTICLE_PUBLISHED_NOTIFICATION_TITLE = "Nouvel article publié";

    private static final Set<String> ARTICLE_SLUG_DUPLICATE_CONSTRAINTS = Set.of("uq_article_slug");

    private final ArticleRepository articleRepository;
    private final ArticleTagRepository articleTagRepository;
    private final TagRepository tagRepository;
    private final AppUserRepository appUserRepository;
    private final ArticleSanitizer articleSanitizer;
    private final ArticleSlugGenerator articleSlugGenerator;
    private final NotificationService notificationService;
    private final Clock clock;

    public ArticleService(
            ArticleRepository articleRepository,
            ArticleTagRepository articleTagRepository,
            TagRepository tagRepository,
            AppUserRepository appUserRepository,
            ArticleSanitizer articleSanitizer,
            ArticleSlugGenerator articleSlugGenerator,
            NotificationService notificationService,
            Clock clock) {
        this.articleRepository = articleRepository;
        this.articleTagRepository = articleTagRepository;
        this.tagRepository = tagRepository;
        this.appUserRepository = appUserRepository;
        this.articleSanitizer = articleSanitizer;
        this.articleSlugGenerator = articleSlugGenerator;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------
    // Consultation publique (DEV-11.5)
    // ---------------------------------------------------------------

    /**
     * Liste paginée des Articles {@code PUBLISHED} (business-rules.md
     * §7.14, DEV-DEC-0061). {@code articleStatus = PUBLISHED} imposé par ce
     * Service via {@link ArticleRepository#findByArticleStatus}, jamais par
     * l'appelant. Résumé allégé ({@link ArticleSummaryResponse}) — voir sa
     * Javadoc pour la justification de l'exclusion de {@code content}/
     * {@code lastModifiedBy}/{@code tags}.
     */
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> listPublishedArticles(Pageable pageable) {
        return articleRepository.findByArticleStatus(ArticleStatus.PUBLISHED, pageable)
                .map(ArticleSummaryResponse::from);
    }

    /**
     * Détail public d'un Article par slug. Inexistant OU non {@code
     * PUBLISHED} (DRAFT/ARCHIVED) → même {@code 404 ARTICLE_NOT_FOUND}
     * (mission DEV-11.5 §15 : la surface publique ne distingue jamais les
     * deux cas, jamais 403, jamais un indice du statut réel dans le
     * message) — même contrat exact que {@code
     * CatalogueService#getPublicTitleById} (DEV-06.4) pour {@code Title
     * WITHDRAWN}. {@code articleStatus = PUBLISHED} encodé directement dans
     * {@link ArticleRepository#findBySlugAndArticleStatus}, jamais un
     * chargement suivi d'un filtrage a posteriori. Tags résolus en une seule
     * requête groupée ({@link ArticleRepository#findTagsByArticleId}) pour
     * cet unique Article — jamais de N+1 possible sur un détail.
     */
    @Transactional(readOnly = true)
    public ArticleResponse getPublishedArticleBySlug(String slug) {
        Objects.requireNonNull(slug, "slug");
        Article article = articleRepository.findBySlugAndArticleStatus(slug, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article publié pour le slug " + slug + "."));
        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    // ---------------------------------------------------------------
    // Lecture staff (DEV-11.12A)
    // ---------------------------------------------------------------

    /**
     * Liste staff paginée, tous statuts confondus ({@code DRAFT}/{@code
     * PUBLISHED}/{@code ARCHIVED}) — corrective débloquant DEV-11.12
     * (frontend staff) : sans cet endpoint, un {@code DRAFT}/{@code
     * ARCHIVED} devenait irrécupérable après un rechargement de page, la
     * surface publique (§ci-dessus) étant structurellement {@code
     * PUBLISHED}-only. {@code ARTICLE_MANAGE} exigé — jamais {@code
     * ARTICLE_READ} : {@code ARTICLE_READ} est également accordé à {@code
     * ROLE_MEMBER} (V002) ; l'utiliser ici exposerait les {@code DRAFT}/
     * {@code ARCHIVED} (états non publics) à un simple membre, contredisant
     * business-rules.md §7.14/§10.7. DÉDUIT MÉCANIQUEMENT du précédent
     * direct et exactement analogue {@code CatalogueService.searchStaffTitles}
     * (staff, visible quel que soit {@code TitleStatus} y compris {@code
     * WITHDRAWN}) : {@code CATALOGUE_READ} existe et est lui aussi accordé à
     * {@code ROLE_MEMBER}, mais la lecture staff Title utilise
     * {@code CATALOGUE_MANAGE} pour cette même raison — même situation
     * structurelle exacte, même solution.
     *
     * <p>Résumé allégé dédié ({@link StaffArticleSummaryResponse}, distinct
     * de {@link ArticleSummaryResponse}) : voir sa Javadoc — réutiliser le
     * résumé public aurait contredit sa propre justification de conception
     * (`articleStatus`/`updatedAt` volontairement exclus là où ils sont ici
     * le but même de la liste). Tri déterministe imposé par {@link
     * ArticleRepository#findAllByOrderByUpdatedAtDescIdDesc} — aucun
     * paramètre de tri/recherche/filtre client (mission DEV-11.12A §12,
     * cohérent avec DEV-DEC-0061).
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<StaffArticleSummaryResponse> listStaffArticles(Pageable pageable) {
        return articleRepository.findAllByOrderByUpdatedAtDescIdDesc(pageable)
                .map(StaffArticleSummaryResponse::from);
    }

    /**
     * Détail staff par id, visible quel que soit {@code articleStatus} —
     * même précédent exact que {@code CatalogueService.getStaffTitleById}.
     * {@code ARTICLE_MANAGE} exigé, même justification qu'{@link
     * #listStaffArticles}. Id inexistant → {@code 404 ARTICLE_NOT_FOUND},
     * jamais {@code 403}/{@code 409} pour une simple absence (même
     * précédent que les autres méthodes de ce Service). {@link
     * ArticleResponse} réutilisé tel quel (jamais un {@code
     * StaffArticleDetailResponse} dupliqué) — même précédent exact que
     * {@code TitleDetailResponse}, partagé entre détail public et détail
     * staff côté Catalogue ; ce DTO porte déjà tous les champs nécessaires
     * ({@code content}, {@code articleStatus}, {@code lastModifiedBy},
     * {@code tags}, tous les timestamps).
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public ArticleResponse getStaffArticleById(Long articleId) {
        Objects.requireNonNull(articleId, "articleId");
        Article article = articleRepository.findByIdWithUsers(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));
        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    // ---------------------------------------------------------------
    // Gestion staff DRAFT (DEV-11.6)
    // ---------------------------------------------------------------

    /**
     * Création d'un Article, toujours {@code DRAFT} (business-rules.md
     * §7.2). {@code authorUserId} provient exclusivement de l'identité
     * authentifiée (Controller, {@code Authentication.getName()}), jamais
     * du corps de la requête — {@link CreateArticleRequest} ne porte
     * d'ailleurs aucun champ {@code authorUserId}/{@code slug}/{@code
     * articleStatus} (DEV-11.3, garanti structurellement par le contrat
     * DTO). {@code lastModifiedByUser = null} à la création (DEV-DEC-0059).
     *
     * <p>{@code content} sanitisé via {@link ArticleSanitizer} puis vérifié
     * fonctionnellement non vide (extraction du texte via {@code
     * Jsoup.parse(...).text()} — un HTML sanitisé ne contenant plus que des
     * balises structurelles sans texte, ex. {@code "<p><br></p>"} ou un
     * {@code <script>} entièrement neutralisé, est rejeté) avant toute
     * persistance — jamais un second sanitizer, uniquement l'extraction de
     * texte déjà fournie par jsoup (dépendance déjà présente, DEV-11.4).
     *
     * <p>{@code slug} généré via {@link ArticleSlugGenerator#generateUniqueSlug}
     * à partir de {@code title}, jamais fourni par le client. Un titre
     * produisant un slug vide après normalisation ({@code
     * IllegalArgumentException} du générateur, DEV-11.4 §13) est traduit en
     * {@code 409 ARTICLE_TITLE_PRODUCES_EMPTY_SLUG} — jamais une exception
     * technique brute exposée au client. Une collision résiduelle détectée
     * à l'écriture (course concurrente entre la vérification préalable et
     * l'insertion, DEV-11.4 §15) est traduite depuis la violation de {@code
     * uq_article_slug} (V001) en {@code 409 ARTICLE_SLUG_ALREADY_EXISTS} —
     * même précédent exact que {@code ReservationService.createReservationForUser}
     * pour {@code ux_reservation_active_user_title} : traduction ciblée par
     * nom de contrainte, jamais un retry automatique (IMPLEMENTATION
     * FREEDOM, aucune source n'impose l'un ou l'autre ; un retry
     * introduirait une complexité non démontrée nécessaire pour une course
     * extrêmement rare entre deux créations au titre strictement
     * identique).
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse createDraftArticle(CreateArticleRequest request, Long authorUserId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(authorUserId, "authorUserId");

        AppUser author = appUserRepository.findById(authorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + authorUserId + "."));

        String sanitizedContent = sanitizeAndRequireNonEmptyContent(request.content());
        String slug = generateSlugOrFail(request.title());

        Instant now = clock.instant();
        Article article = new Article();
        article.setAuthorUser(author);
        article.setLastModifiedByUser(null);
        article.setTitle(request.title());
        article.setContent(sanitizedContent);
        article.setSummary(request.summary());
        article.setSlug(slug);
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setPublishedAt(null);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);

        try {
            articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException ex) {
            if (isArticleSlugDuplicateConstraint(ex)) {
                throw new BusinessRuleException(
                        ARTICLE_SLUG_ALREADY_EXISTS_CODE, "Un Article existe déjà avec ce slug.");
            }
            throw ex;
        }

        return ArticleResponse.from(article, List.of());
    }

    /**
     * Modification éditoriale d'un Article {@code DRAFT} ou {@code
     * PUBLISHED} (DEV-11.6 pour {@code DRAFT}, généralisé en DEV-11.8 pour
     * couvrir {@code PUBLISHED} sans repasser en {@code DRAFT},
     * business-rules.md §7.4). {@code ARCHIVED} → {@code 409
     * ARTICLE_NOT_EDITABLE} (renommage mécanique de l'ancien {@code
     * ARTICLE_NOT_DRAFT}, devenu inexact depuis que {@code PUBLISHED} est
     * lui aussi éditable — aucune règle métier changée, uniquement
     * l'étiquette technique du rejet), jamais un {@code 404} : un staff
     * autorisé ({@code ARTICLE_MANAGE}) a le droit de savoir que l'Article
     * existe (mission DEV-11.6 §18 — contrairement à la consultation
     * publique, §15, qui masque volontairement cette distinction).
     *
     * <p>La logique de mutation des champs est strictement identique pour
     * {@code DRAFT} et {@code PUBLISHED} (aucune branche par statut au-delà
     * du garde initial) — {@code articleStatus}/{@code publishedAt}/{@code
     * slug} ne sont jamais touchés ici, qu'il s'agisse d'un {@code DRAFT} en
     * préparation ou d'un {@code PUBLISHED} déjà diffusé (business-rules.md
     * §7.4 : {@code publishedAt} inchangé, aucun retour {@code PUBLISHED →
     * DRAFT}). Aucune Notification n'est créée par cette méthode, y compris
     * pour un {@code PUBLISHED} : {@code ARTICLE_PUBLISHED} n'est diffusée
     * qu'à la transition {@link #publishArticle}, jamais à une édition de
     * contenu (business-rules.md §6.10, §7.4).
     *
     * <p>PATCH sparse (même sémantique exacte que {@code
     * CatalogueService#updateTitle}/{@link UpdateArticleRequest}, DEV-11.3) :
     * un champ absent du corps JSON n'est jamais modifié. {@code slug}
     * n'est jamais recalculé ({@code UpdateArticleRequest} ne porte
     * d'ailleurs aucun champ {@code slug} — stabilité SOURCE EXPLICITE,
     * business-rules.md §7.8, garantie structurellement par le contrat
     * DTO). {@code title} présent+vide et {@code content} présent+{@code
     * null} sont rejetés (colonnes {@code NOT NULL}) — même précédent exact
     * que {@code CatalogueService.updateTitle} pour {@code title}/{@code
     * language}. {@code summary} présent+{@code null} efface (nullable en
     * base) — même précédent que {@code subtitle}/{@code summary} de
     * {@code UpdateTitleRequest}.
     *
     * <p>{@code content}, s'il est présent, est sanitisé et vérifié
     * fonctionnellement non vide exactement comme à la création — jamais
     * re-sanitisé s'il est absent du PATCH (mission §9).
     *
     * <p>{@code lastModifiedByUser} mis à jour uniquement si une mutation
     * réelle a eu lieu ({@code mutated}, même mécanisme exact que {@code
     * CatalogueService.updateTitle}) — un PATCH ne portant aucun champ (ou
     * ne modifiant en pratique rien de plus qu'une valeur déjà identique,
     * même précédent : la présence du champ dans le corps JSON suffit à
     * déclencher {@code mutated = true}, sa valeur n'est jamais comparée à
     * l'ancienne) déclenche néanmoins {@code lastModifiedByUser} dès qu'au
     * moins un champ est {@code isXxxPresent()} — aucune modification
     * artificielle n'est fabriquée si le corps JSON est structurellement
     * vide (§11 : aucun champ présent → {@code mutated} reste
     * {@code false} → {@code lastModifiedByUser}/{@code updatedAt}
     * inchangés). {@code authorUser} n'est jamais réassigné ici.
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse updateArticle(Long articleId, UpdateArticleRequest request, Long editorUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(editorUserId, "editorUserId");

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() == ArticleStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_EDITABLE_CODE, "Un Article ARCHIVED ne peut plus être modifié.");
        }

        boolean mutated = false;

        if (request.isTitlePresent()) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BusinessRuleException(ARTICLE_TITLE_MUST_NOT_BE_BLANK_CODE, "title ne peut pas être vide.");
            }
            article.setTitle(request.getTitle());
            mutated = true;
        }
        if (request.isContentPresent()) {
            if (request.getContent() == null) {
                throw new BusinessRuleException(
                        ARTICLE_CONTENT_MUST_NOT_BE_NULL_CODE, "content ne peut pas être effacé.");
            }
            article.setContent(sanitizeAndRequireNonEmptyContent(request.getContent()));
            mutated = true;
        }
        if (request.isSummaryPresent()) {
            article.setSummary(request.getSummary());
            mutated = true;
        }

        if (mutated) {
            AppUser editor = appUserRepository.findById(editorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                            "Utilisateur introuvable pour l'identifiant " + editorUserId + "."));
            article.setLastModifiedByUser(editor);
            article.setUpdatedAt(clock.instant());
        }

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    // ---------------------------------------------------------------
    // Publication (DEV-11.7)
    // ---------------------------------------------------------------

    /**
     * Publication d'un Article : {@code DRAFT → PUBLISHED} uniquement
     * (business-rules.md §7.3) — {@code PUBLISHED}/{@code ARCHIVED} → {@code
     * 409 ARTICLE_NOT_PUBLISHABLE}, jamais un {@code 404} (même
     * raisonnement que {@link #updateArticle} pour {@code
     * ARTICLE_NOT_EDITABLE} : un staff autorisé a le droit de savoir que
     * l'Article existe), jamais un no-op silencieux, jamais une seconde
     * diffusion. {@code ARTICLE_PUBLISH} exigé — jamais {@code
     * ARTICLE_MANAGE} en plus (DEV-11.1 §32, déduit mécaniquement).
     *
     * <p><b>Concurrence</b> : {@link ArticleRepository#findByIdForUpdate}
     * charge et verrouille l'Article en une seule opération ({@code
     * PESSIMISTIC_WRITE}, même précédent exact que {@code
     * LoanRepository.findByIdForUpdate}/{@code CopyRepository.findByIdForUpdate}) —
     * aucune fenêtre non verrouillée n'existe entre le chargement et le
     * contrôle {@code articleStatus == DRAFT} qui suit immédiatement : la
     * revalidation post-lock est donc structurellement garantie. Deux
     * publications concurrentes sur le même Article se sérialisent
     * réellement au niveau PostgreSQL (preuve : {@code
     * ArticleServiceConcurrencyTests}) — jamais {@code @Version} ajouté à
     * {@code Article} (aurait modifié le modèle persistant, hors scope,
     * mission DEV-11.7 §17).
     *
     * <p><b>Invariants préservés</b> : {@code authorUser}/{@code slug}/
     * {@code title}/{@code content}/{@code summary} jamais modifiés ici —
     * seuls {@code articleStatus}, {@code publishedAt}, {@code
     * lastModifiedByUser} et {@code updatedAt} changent (business-rules.md
     * §7.3/§7.12, DEV-DEC-0059 : {@code lastModifiedByUser = publisher}
     * même si aucun contenu n'a changé). Aucun re-sanitization, aucune
     * régénération de slug, aucune Tag touchée (mission §9/§21).
     *
     * <p><b>Atomicité</b> (architecture.md §7.2, SOURCE EXPLICITE : « publish
     * Article + expected Notification creation ») : {@code
     * @Transactional} unique, {@link NotificationService#createForArticle}
     * ne porte pas sa propre frontière transactionnelle (jamais {@code
     * REQUIRES_NEW}, même précédent que {@code LoanService.registerReturn}/
     * {@code ReservationService}) — un échec de création d'une Notification
     * fait échouer/rollback la publication entière (preuve : {@code
     * ArticlePublicationAtomicityTests}). Le fanout n'est déclenché
     * qu'<em>après</em> la mutation de l'Article (jamais avant d'être certain
     * que la publication est autorisée, mission §24).
     *
     * <p><b>Destinataires</b> : {@link AppUserRepository#findByMemberStatus}
     * ({@code MemberStatus.ACTIVE} uniquement, business-rules.md §6.5/§6.10)
     * — 0 destinataire est un cas valide (0 Notification créée, la
     * publication reste un succès). Chargement complet synchrone,
     * IMPLEMENTATION FREEDOM documentée (aucun batching, aucun async, aucun
     * message broker — architecture.md §7.2/§11.4, mission §35).
     */
    @PreAuthorize("hasAuthority('ARTICLE_PUBLISH')")
    @Transactional
    public ArticleResponse publishArticle(Long articleId, Long publisherUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(publisherUserId, "publisherUserId");

        Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() != ArticleStatus.DRAFT) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_PUBLISHABLE_CODE, "Seul un Article DRAFT peut être publié.");
        }

        AppUser publisher = appUserRepository.findById(publisherUserId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + publisherUserId + "."));

        Instant now = clock.instant();
        article.setArticleStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(now);
        article.setLastModifiedByUser(publisher);
        article.setUpdatedAt(now);

        List<AppUser> activeMembers = appUserRepository.findByMemberStatus(MemberStatus.ACTIVE);
        for (AppUser recipient : activeMembers) {
            notificationService.createForArticle(article, recipient, ARTICLE_PUBLISHED_NOTIFICATION_TITLE,
                    "Un nouvel article a été publié : " + article.getTitle() + ".");
        }

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    // ---------------------------------------------------------------
    // Archivage (DEV-11.8)
    // ---------------------------------------------------------------

    /**
     * Archivage d'un Article : {@code PUBLISHED → ARCHIVED} uniquement
     * (business-rules.md §7.5) — {@code DRAFT}/{@code ARCHIVED} → {@code 409
     * ARTICLE_NOT_ARCHIVABLE} (même précédent de nommage exact que {@code
     * ARTICLE_NOT_PUBLISHABLE}, DEV-11.7), jamais un {@code 404}, jamais un
     * no-op silencieux. {@code ARCHIVED} reste terminal : aucun chemin de
     * code ne permet {@code ARCHIVED → PUBLISHED} ni {@code ARCHIVED →
     * DRAFT} (business-rules.md §10.7). {@code ARTICLE_MANAGE} exigé — DEV-11.1
     * §32 ne nomme littéralement qu'un troisième verbe manquant
     * (« archiver »), rattaché mécaniquement à {@code ARTICLE_MANAGE} (« le
     * reste des opérations de gestion »), jamais {@code ARTICLE_PUBLISH} qui
     * gate exclusivement la transition {@code publish}.
     *
     * <p><b>Concurrence</b> : réutilise {@link ArticleRepository#findByIdForUpdate}
     * (même verrou {@code PESSIMISTIC_WRITE} qu'en DEV-11.7, jamais un
     * second mécanisme concurrent différent) — deux archivages concurrents
     * sur le même Article se sérialisent réellement au niveau PostgreSQL, le
     * second thread découvre {@code ARCHIVED} après le commit du premier et
     * rejette proprement, sans double effet secondaire.
     *
     * <p><b>Invariants préservés</b> : {@code authorUser}/{@code slug}/
     * {@code title}/{@code content}/{@code summary}/{@code publishedAt}
     * jamais modifiés ici — seuls {@code articleStatus}, {@code
     * lastModifiedByUser} et {@code updatedAt} changent (DEV-DEC-0059 :
     * {@code lastModifiedByUser = archiver} même si aucun contenu n'a
     * changé, l'archivage étant lui-même l'action persistée significative).
     *
     * <p><b>Notification</b> : aucun type {@code NotificationType} n'est
     * défini pour l'archivage (business-rules.md §6.3/§6.10 : seul
     * {@code ARTICLE_PUBLISHED} existe pour l'origine {@code Article}) —
     * aucune Notification n'est donc créée ici, conformément à la mission
     * (ne pas inventer {@code ARTICLE_ARCHIVED}).
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse archiveArticle(Long articleId, Long editorUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(editorUserId, "editorUserId");

        Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() != ArticleStatus.PUBLISHED) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_ARCHIVABLE_CODE, "Seul un Article PUBLISHED peut être archivé.");
        }

        AppUser editor = appUserRepository.findById(editorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + editorUserId + "."));

        article.setArticleStatus(ArticleStatus.ARCHIVED);
        article.setLastModifiedByUser(editor);
        article.setUpdatedAt(clock.instant());

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    // ---------------------------------------------------------------
    // Hard-delete DRAFT (DEV-11.8, DEV-DEC-0058)
    // ---------------------------------------------------------------

    /**
     * Suppression physique d'un Article {@code DRAFT} uniquement
     * (DEV-DEC-0058, business-rules.md §7.11) — {@code PUBLISHED}/{@code
     * ARCHIVED} → {@code 409 ARTICLE_NOT_DELETABLE}, jamais un hard-delete
     * silencieux d'un Article historique/publié (business-rules.md §1.4,
     * §10.7). {@code ARTICLE_MANAGE} exigé, jamais {@code ARTICLE_PUBLISH}.
     *
     * <p>Aucun paramètre acteur : rien n'est persisté sur l'acteur d'une
     * suppression physique (l'Article n'existe plus après commit) —
     * l'autorisation reste entièrement portée par {@code @PreAuthorize} /
     * {@code SecurityContext}, comme pour toute méthode de ce Service
     * (IMPLEMENTATION FREEDOM, aucune source n'exige de tracer l'acteur
     * d'un hard-delete).
     *
     * <p><b>Concurrence</b> : réutilise {@link ArticleRepository#findByIdForUpdate}
     * (même précédent que {@link #archiveArticle}/DEV-11.7) — deux
     * suppressions concurrentes du même {@code DRAFT} se sérialisent au
     * niveau PostgreSQL : la seconde, une fois le verrou obtenu après le
     * commit de la première, ne retrouve plus l'Article et échoue proprement
     * en {@code 404 ARTICLE_NOT_FOUND} (jamais une exception SQL brute,
     * jamais un {@code 500} non traduit — mission DEV-11.8 §22).
     *
     * <p><b>ArticleTag / FK (DEV-11.9)</b> : {@code article_tag} porte des FK
     * {@code ON DELETE RESTRICT} sur {@code article_id} <em>et</em> {@code
     * tag_id} (V001). Depuis DEV-11.9, un {@code DRAFT} peut réellement
     * posséder des {@code ArticleTag} ({@link #associateTags}) — cette
     * méthode supprime donc explicitement ses associations ({@link
     * ArticleTagRepository#deleteByIdArticleId}) <em>avant</em> de supprimer
     * l'Article lui-même, dans la même transaction, réconciliant
     * DEV-DEC-0058 (hard-delete {@code DRAFT} autorisé) avec la contrainte
     * FK. Les {@code Tag} référencés ne sont jamais touchés (seules les
     * lignes d'association disparaissent) — ceci ne constitue pas un CRUD
     * Tag, {@link TagService} reste l'unique autorité de gestion du
     * référentiel.
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public void deleteDraftArticle(Long articleId) {
        Objects.requireNonNull(articleId, "articleId");

        Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() != ArticleStatus.DRAFT) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_DELETABLE_CODE, "Seul un Article DRAFT peut être supprimé physiquement.");
        }

        articleTagRepository.deleteByIdArticleId(articleId);
        articleRepository.delete(article);
    }

    // ---------------------------------------------------------------
    // Association Tag (DEV-11.9, DEV-DEC-0060)
    // ---------------------------------------------------------------

    /**
     * Association/dissociation de {@code Tag} existants sur un Article
     * {@code DRAFT} ou {@code PUBLISHED} (même garde de statut exact que
     * {@link #updateArticle} — {@code ARCHIVED} → {@code 409
     * ARTICLE_NOT_EDITABLE}, réutilisé tel quel : associer des Tags est une
     * action éditoriale sur l'Article au même titre qu'une modification de
     * contenu, aucune source ne distingue les deux, DÉDUIT MÉCANIQUEMENT).
     * {@code ARTICLE_MANAGE} exigé, jamais {@code ARTICLE_PUBLISH}.
     *
     * <p><b>Tags existants uniquement</b> (DEV-DEC-0060, business-rules.md
     * §7.13) : {@link UpdateArticleTagsRequest} ne porte que des {@code
     * tagIds}, jamais {@code code}/{@code label}/{@code description} —
     * structurellement impossible de créer un Tag depuis ce contrat. Chaque
     * {@code tagId} inconnu → {@code 404 TAG_NOT_FOUND}, résolu <em>avant</em>
     * toute mutation ({@link #resolveTags}, même précédent exact que {@code
     * CatalogueService.resolveAuthors}/{@code resolveGenres}) — une
     * association reste entièrement atomique, jamais partielle.
     *
     * <p><b>Remplacement complet</b> : {@code tagIds} représente la
     * sélection finale exacte, jamais un couple add/remove (mission §15,
     * IMPLEMENTATION FREEDOM). Doublons dans la requête normalisés en
     * ensemble avant résolution (la clé composite {@code article_tag}
     * interdit de toute façon les doublons structurels). Écriture par diff
     * ({@link #replaceArticleTags}) — même précédent exact que {@code
     * CatalogueService.replaceAuthors}/{@code replaceGenres} (DEV-06.5),
     * jamais un {@code delete all + insert all} qui produirait des écritures
     * inutiles pour les Tags inchangés.
     *
     * <p><b>Concurrence</b> : pas de verrou {@code PESSIMISTIC_WRITE}
     * ({@code findById} simple, pas {@code findByIdForUpdate}) — même choix
     * exact que {@code CatalogueService.updateTitle} pour
     * {@code authorIds}/{@code genreIds}, IMPLEMENTATION FREEDOM documentée
     * par cohérence avec ce précédent direct plutôt qu'un besoin démontré
     * (aucun fanout, aucune transition d'état concurrente à protéger ici).
     *
     * <p><b>lastModifiedByUser</b> : toujours mis à jour à un appel réussi
     * (avec {@code updatedAt}), y compris si la sélection finale est
     * identique à l'actuelle — l'appel de cet endpoint dédié constitue par
     * construction une action persistée significative (business-rules.md
     * §7.12), même précédent que {@code updateTitle} traitant la simple
     * présence de {@code authorIds}/{@code genreIds} comme {@code mutated =
     * true} sans comparer au contenu précédent.
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse associateTags(Long articleId, UpdateArticleTagsRequest request, Long actorUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(actorUserId, "actorUserId");

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() == ArticleStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_EDITABLE_CODE, "Un Article ARCHIVED ne peut plus être modifié.");
        }

        AppUser actor = appUserRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + actorUserId + "."));

        List<Tag> targetTags = resolveTags(request.tagIds());
        replaceArticleTags(article, targetTags);

        article.setLastModifiedByUser(actor);
        article.setUpdatedAt(clock.instant());

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    private List<Tag> resolveTags(List<Long> tagIds) {
        Set<Long> uniqueTagIds = new LinkedHashSet<>(tagIds);
        List<Tag> tags = new ArrayList<>();
        for (Long tagId : uniqueTagIds) {
            tags.add(tagRepository.findById(tagId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            TAG_NOT_FOUND_CODE, "Aucun Tag pour l'identifiant " + tagId + ".")));
        }
        return tags;
    }

    private void replaceArticleTags(Article article, List<Tag> targetTags) {
        List<ArticleTag> currentAssociations = articleTagRepository.findByIdArticleId(article.getId());
        Set<Long> currentTagIds = new HashSet<>();
        for (ArticleTag association : currentAssociations) {
            currentTagIds.add(association.getTag().getId());
        }
        Set<Long> targetTagIds = new HashSet<>();
        for (Tag tag : targetTags) {
            targetTagIds.add(tag.getId());
        }

        for (ArticleTag association : currentAssociations) {
            if (!targetTagIds.contains(association.getTag().getId())) {
                articleTagRepository.delete(association);
            }
        }
        for (Tag tag : targetTags) {
            if (!currentTagIds.contains(tag.getId())) {
                ArticleTag newAssociation = new ArticleTag();
                newAssociation.setId(new ArticleTagId(article.getId(), tag.getId()));
                newAssociation.setArticle(article);
                newAssociation.setTag(tag);
                articleTagRepository.save(newAssociation);
            }
        }
    }

    // ---------------------------------------------------------------
    // Utilitaires privés
    // ---------------------------------------------------------------

    private String sanitizeAndRequireNonEmptyContent(String rawContent) {
        String sanitized = articleSanitizer.sanitize(rawContent);
        if (Jsoup.parse(sanitized).text().isBlank()) {
            throw new BusinessRuleException(
                    ARTICLE_CONTENT_EMPTY_CODE, "Le contenu de l'Article est vide après sanitization.");
        }
        return sanitized;
    }

    private String generateSlugOrFail(String title) {
        try {
            return articleSlugGenerator.generateUniqueSlug(
                    title, candidate -> articleRepository.findBySlug(candidate).isPresent());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ARTICLE_TITLE_PRODUCES_EMPTY_SLUG_CODE,
                    "Le titre fourni ne permet pas de générer un slug valide.");
        }
    }

    private boolean isArticleSlugDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return ARTICLE_SLUG_DUPLICATE_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }
}
