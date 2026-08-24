package be.primatis.article;

import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.ArticleSummaryResponse;
import be.primatis.article.dto.CreateArticleRequest;
import be.primatis.article.dto.StaffArticleSummaryResponse;
import be.primatis.article.dto.TagResponse;
import be.primatis.article.dto.UpdateArticleRequest;
import be.primatis.article.dto.UpdateArticleTagsRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.notification.Notification;
import be.primatis.notification.NotificationType;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie {@link ArticleService} contre PostgreSQL réel : consultation
 * publique (DEV-11.5) et gestion staff DRAFT (DEV-11.6, {@code
 * ARTICLE_MANAGE}). {@code @Transactional} (rollback automatique par
 * test), même précédent que {@code CatalogueServiceTests}. Les méthodes
 * publiques n'ont aucun {@code @PreAuthorize} ; les méthodes staff en ont
 * un, simulé ici via {@code SecurityContextHolder} — même précédent exact
 * que {@code CatalogueServiceTests}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArticleServiceTests {

    @Autowired
    private ArticleService articleService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Sort PUBLIC_SORT = Sort.by(Sort.Direction.DESC, "publishedAt", "id");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithArticleManage(Long userId) {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ARTICLE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken(String.valueOf(userId), null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithoutArticleManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithArticlePublish(Long userId) {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ARTICLE_PUBLISH"));
        Authentication authentication = new TestingAuthenticationToken(String.valueOf(userId), null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ---------------------------------------------------------------
    // listPublishedArticles
    // ---------------------------------------------------------------

    @Test
    void listPublishedArticlesOnlyIncludesPublishedArticles() {
        AppUser author = persistUser("service-list-published@primatis.test");
        persistArticle(author, "Service Draft Excluded", ArticleStatus.DRAFT, null);
        persistArticle(author, "Service Archived Excluded", ArticleStatus.ARCHIVED,
                Instant.parse("2026-08-01T10:00:00Z"));
        Article published = persistArticle(
                author, "Service Published Included", ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        Page<ArticleSummaryResponse> page = articleService.listPublishedArticles(PageRequest.of(0, 20, PUBLIC_SORT));

        assertThat(page.getContent()).extracting(ArticleSummaryResponse::id).containsExactly(published.getId());
    }

    @Test
    void listPublishedArticlesMapsExpectedSummaryFields() {
        AppUser author = persistUser("service-list-mapping@primatis.test");
        Instant publishedAt = Instant.parse("2026-08-10T10:00:00Z");
        Article article = persistArticle(author, "Service Mapping Title", ArticleStatus.PUBLISHED, publishedAt);
        article.setSummary("Résumé de test");
        entityManager.flush();

        Page<ArticleSummaryResponse> page = articleService.listPublishedArticles(PageRequest.of(0, 20, PUBLIC_SORT));
        ArticleSummaryResponse summary =
                page.getContent().stream().filter(item -> item.id().equals(article.getId())).findFirst().orElseThrow();

        assertThat(summary.title()).isEqualTo("Service Mapping Title");
        assertThat(summary.summary()).isEqualTo("Résumé de test");
        assertThat(summary.slug()).isEqualTo(article.getSlug());
        assertThat(summary.publishedAt()).isEqualTo(publishedAt);
        assertThat(summary.author().id()).isEqualTo(author.getId());
    }

    @Test
    void listPublishedArticlesTransmitsPaginationCorrectly() {
        AppUser author = persistUser("service-list-pagination@primatis.test");
        for (int i = 0; i < 3; i++) {
            persistArticle(author, "Service Pagination " + i, ArticleStatus.PUBLISHED,
                    Instant.parse("2026-08-0" + (i + 1) + "T10:00:00Z"));
        }

        Page<ArticleSummaryResponse> page = articleService.listPublishedArticles(PageRequest.of(0, 2, PUBLIC_SORT));

        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(2);
    }

    // ---------------------------------------------------------------
    // getPublishedArticleBySlug
    // ---------------------------------------------------------------

    @Test
    void getPublishedArticleBySlugReturnsFullDetailWithTags() {
        AppUser author = persistUser("service-detail@primatis.test");
        Article article = persistArticleWithSlug(
                author, "Service Detail Title", "service-detail-slug", ArticleStatus.PUBLISHED,
                Instant.parse("2026-08-10T10:00:00Z"));
        Tag tag = persistTag("SERVICE-DETAIL-TAG", "Detail Tag");
        linkArticleTag(article, tag);

        ArticleResponse response = articleService.getPublishedArticleBySlug("service-detail-slug");

        assertThat(response.id()).isEqualTo(article.getId());
        assertThat(response.content()).isEqualTo("Contenu de test");
        assertThat(response.author().id()).isEqualTo(author.getId());
        assertThat(response.tags()).extracting(t -> t.code()).containsExactly("SERVICE-DETAIL-TAG");
    }

    @Test
    void getPublishedArticleBySlugThrowsNotFoundForADraftArticle() {
        AppUser author = persistUser("service-detail-draft@primatis.test");
        persistArticleWithSlug(author, "Service Draft Detail", "service-draft-detail-slug", ArticleStatus.DRAFT, null);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.getPublishedArticleBySlug("service-draft-detail-slug"))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void getPublishedArticleBySlugThrowsNotFoundForAnArchivedArticle() {
        AppUser author = persistUser("service-detail-archived@primatis.test");
        persistArticleWithSlug(author, "Service Archived Detail", "service-archived-detail-slug",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.getPublishedArticleBySlug("service-archived-detail-slug"))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void getPublishedArticleBySlugThrowsNotFoundForANonExistentSlug() {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.getPublishedArticleBySlug("service-does-not-exist-slug"))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // listStaffArticles — sécurité (DEV-11.12A)
    // ---------------------------------------------------------------

    @Test
    void listStaffArticlesWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> articleService.listStaffArticles(PageRequest.of(0, 20)));
    }

    @Test
    void listStaffArticlesWithArticlePublishOnlyIsDenied() {
        AppUser user = persistUser("staff-list-security-publish@primatis.test");
        authenticateWithArticlePublish(user.getId());

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.listStaffArticles(PageRequest.of(0, 20)));
    }

    @Test
    void listStaffArticlesWithoutArticleManageIsDenied() {
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.listStaffArticles(PageRequest.of(0, 20)));
    }

    // ---------------------------------------------------------------
    // listStaffArticles — comportement
    // ---------------------------------------------------------------

    @Test
    void listStaffArticlesReturnsAllStatuses() {
        AppUser author = persistUser("staff-list-all@primatis.test");
        AppUser editor = persistUser("staff-list-all-editor@primatis.test");
        Article draft = persistArticleWithSlug(author, "Staff List Draft", "staff-list-draft", ArticleStatus.DRAFT, null);
        Article published = persistArticleWithSlug(author, "Staff List Published", "staff-list-published",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        Article archived = persistArticleWithSlug(author, "Staff List Archived", "staff-list-archived",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        Page<StaffArticleSummaryResponse> page = articleService.listStaffArticles(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(StaffArticleSummaryResponse::id)
                .contains(draft.getId(), published.getId(), archived.getId());
    }

    @Test
    void listStaffArticlesMapsExpectedSummaryFields() {
        AppUser author = persistUser("staff-list-mapping@primatis.test");
        AppUser editor = persistUser("staff-list-mapping-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff List Mapping", "staff-list-mapping",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        article.setSummary("Résumé staff");
        entityManager.flush();
        authenticateWithArticleManage(editor.getId());

        Page<StaffArticleSummaryResponse> page = articleService.listStaffArticles(PageRequest.of(0, 100));
        StaffArticleSummaryResponse row = page.getContent().stream()
                .filter(candidate -> candidate.id().equals(article.getId())).findFirst().orElseThrow();

        assertThat(row.title()).isEqualTo("Staff List Mapping");
        assertThat(row.slug()).isEqualTo("staff-list-mapping");
        assertThat(row.summary()).isEqualTo("Résumé staff");
        assertThat(row.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(row.author().id()).isEqualTo(author.getId());
        assertThat(row.publishedAt()).isEqualTo(Instant.parse("2026-08-10T10:00:00Z"));
        assertThat(row.updatedAt()).isNotNull();
    }

    @Test
    void listStaffArticlesRespectsPagination() {
        AppUser author = persistUser("staff-list-pagination@primatis.test");
        AppUser editor = persistUser("staff-list-pagination-editor@primatis.test");
        for (int i = 0; i < 3; i++) {
            persistArticleWithSlug(author, "Staff List Pagination " + i, "staff-list-pagination-" + i,
                    ArticleStatus.DRAFT, null);
        }
        authenticateWithArticleManage(editor.getId());

        Page<StaffArticleSummaryResponse> page = articleService.listStaffArticles(PageRequest.of(0, 2));

        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(0);
    }

    // ---------------------------------------------------------------
    // getStaffArticleById — sécurité (DEV-11.12A)
    // ---------------------------------------------------------------

    @Test
    void getStaffArticleByIdWithoutAuthenticationIsDenied() {
        AppUser author = persistUser("staff-detail-security-anon@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Anon", "staff-detail-anon", ArticleStatus.DRAFT, null);

        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> articleService.getStaffArticleById(article.getId()));
    }

    @Test
    void getStaffArticleByIdWithArticlePublishOnlyIsDenied() {
        AppUser author = persistUser("staff-detail-security-publish@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Publish", "staff-detail-publish", ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(author.getId());

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.getStaffArticleById(article.getId()));
    }

    @Test
    void getStaffArticleByIdWithoutArticleManageIsDenied() {
        AppUser author = persistUser("staff-detail-security-member@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Member", "staff-detail-member", ArticleStatus.DRAFT, null);
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.getStaffArticleById(article.getId()));
    }

    // ---------------------------------------------------------------
    // getStaffArticleById — comportement
    // ---------------------------------------------------------------

    @Test
    void getStaffArticleByIdReturnsADraftArticle() {
        AppUser author = persistUser("staff-detail-draft@primatis.test");
        AppUser editor = persistUser("staff-detail-draft-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Draft", "staff-detail-draft-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.getStaffArticleById(article.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void getStaffArticleByIdReturnsAPublishedArticle() {
        AppUser author = persistUser("staff-detail-published@primatis.test");
        AppUser editor = persistUser("staff-detail-published-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Published", "staff-detail-published-slug",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.getStaffArticleById(article.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(Instant.parse("2026-08-10T10:00:00Z"));
    }

    @Test
    void getStaffArticleByIdReturnsAnArchivedArticle() {
        AppUser author = persistUser("staff-detail-archived@primatis.test");
        AppUser editor = persistUser("staff-detail-archived-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Archived", "staff-detail-archived-slug",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.getStaffArticleById(article.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.ARCHIVED);
    }

    @Test
    void getStaffArticleByIdIncludesTags() {
        AppUser author = persistUser("staff-detail-tags@primatis.test");
        AppUser editor = persistUser("staff-detail-tags-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Tags", "staff-detail-tags-slug",
                ArticleStatus.DRAFT, null);
        Tag tag = persistTag("staff-detail-tag", "Staff Detail Tag");
        linkArticleTag(article, tag);
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.getStaffArticleById(article.getId());

        assertThat(response.tags()).extracting(TagResponse::id).containsExactly(tag.getId());
    }

    @Test
    void getStaffArticleByIdIncludesAuthorAndLastModifiedBy() {
        AppUser author = persistUser("staff-detail-users@primatis.test");
        AppUser editor = persistUser("staff-detail-users-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Staff Detail Users", "staff-detail-users-slug",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        article.setLastModifiedByUser(editor);
        entityManager.flush();
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.getStaffArticleById(article.getId());

        assertThat(response.author().id()).isEqualTo(author.getId());
        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().id()).isEqualTo(editor.getId());
    }

    @Test
    void getStaffArticleByIdNonExistentThrowsNotFound() {
        AppUser editor = persistUser("staff-detail-not-found-editor@primatis.test");
        authenticateWithArticleManage(editor.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.getStaffArticleById(999999999L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Séparation public/staff — non-régression (mission DEV-11.12A §19)
    // ---------------------------------------------------------------

    @Test
    void draftArticleIsVisibleViaStaffDetailButNotViaPublicSlug() {
        AppUser author = persistUser("separation-draft@primatis.test");
        AppUser editor = persistUser("separation-draft-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Separation Draft", "separation-draft-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        assertThat(articleService.getStaffArticleById(article.getId()).articleStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.getPublishedArticleBySlug("separation-draft-slug"));
    }

    @Test
    void archivedArticleIsVisibleViaStaffDetailButNotViaPublicSlug() {
        AppUser author = persistUser("separation-archived@primatis.test");
        AppUser editor = persistUser("separation-archived-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Separation Archived", "separation-archived-slug",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        assertThat(articleService.getStaffArticleById(article.getId()).articleStatus()).isEqualTo(ArticleStatus.ARCHIVED);
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.getPublishedArticleBySlug("separation-archived-slug"));
    }

    // ---------------------------------------------------------------
    // createDraftArticle — sécurité
    // ---------------------------------------------------------------

    @Test
    void createDraftArticleWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class).isThrownBy(() ->
                articleService.createDraftArticle(new CreateArticleRequest("Denied", "Contenu", null), 1L));
    }

    @Test
    void createDraftArticleWithoutArticleManageIsDenied() {
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() ->
                articleService.createDraftArticle(new CreateArticleRequest("Denied", "Contenu", null), 1L));
    }

    // ---------------------------------------------------------------
    // createDraftArticle — comportement
    // ---------------------------------------------------------------

    @Test
    void createDraftArticleProducesDraftWithNullPublishedAtAndNullLastModifiedBy() {
        AppUser author = persistUser("create-draft-basic@primatis.test");
        authenticateWithArticleManage(author.getId());

        ArticleResponse response = articleService.createDraftArticle(
                new CreateArticleRequest("Create Draft Basic", "<p>Contenu éditorial</p>", null), author.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
        assertThat(response.lastModifiedBy()).isNull();
        assertThat(response.author().id()).isEqualTo(author.getId());
        assertThat(response.tags()).isEmpty();
    }

    @Test
    void createDraftArticleSanitizesContentRemovingScript() {
        AppUser author = persistUser("create-draft-sanitize@primatis.test");
        authenticateWithArticleManage(author.getId());

        ArticleResponse response = articleService.createDraftArticle(new CreateArticleRequest(
                "Create Draft Sanitize", "<p>Texte légitime</p><script>alert(1)</script>", null), author.getId());

        assertThat(response.content()).doesNotContain("<script").doesNotContain("alert(").contains("Texte légitime");
    }

    @Test
    void createDraftArticleAcceptsNullSummary() {
        AppUser author = persistUser("create-draft-summary@primatis.test");
        authenticateWithArticleManage(author.getId());

        ArticleResponse response = articleService.createDraftArticle(
                new CreateArticleRequest("Create Draft Summary", "<p>Contenu</p>", null), author.getId());

        assertThat(response.summary()).isNull();
    }

    @Test
    void createDraftArticleGeneratesSlugFromTitle() {
        AppUser author = persistUser("create-draft-slug@primatis.test");
        authenticateWithArticleManage(author.getId());

        ArticleResponse response = articleService.createDraftArticle(
                new CreateArticleRequest("Été à la Bibliothèque", "<p>Contenu</p>", null), author.getId());

        assertThat(response.slug()).isEqualTo("ete-a-la-bibliotheque");
    }

    @Test
    void createDraftArticleGeneratesUniqueSlugOnCollision() {
        AppUser author = persistUser("create-draft-collision@primatis.test");
        authenticateWithArticleManage(author.getId());

        ArticleResponse first = articleService.createDraftArticle(
                new CreateArticleRequest("Collision Slug Test", "<p>Premier</p>", null), author.getId());
        ArticleResponse second = articleService.createDraftArticle(
                new CreateArticleRequest("Collision Slug Test", "<p>Second</p>", null), author.getId());

        assertThat(first.slug()).isEqualTo("collision-slug-test");
        assertThat(second.slug()).isEqualTo("collision-slug-test-2");
    }

    @Test
    void createDraftArticleRejectsTitleProducingEmptySlug() {
        AppUser author = persistUser("create-draft-empty-slug@primatis.test");
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.createDraftArticle(
                        new CreateArticleRequest("!!!", "<p>Contenu</p>", null), author.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_TITLE_PRODUCES_EMPTY_SLUG"));
    }

    @Test
    void createDraftArticleRejectsBlankContentAfterSanitization() {
        AppUser author = persistUser("create-draft-blank@primatis.test");
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.createDraftArticle(
                        new CreateArticleRequest("Create Draft Blank", "   ", null), author.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_CONTENT_EMPTY"));
    }

    @Test
    void createDraftArticleRejectsContentWithOnlyEmptyParagraph() {
        AppUser author = persistUser("create-draft-empty-p@primatis.test");
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.createDraftArticle(
                        new CreateArticleRequest("Create Draft Empty P", "<p></p>", null), author.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_CONTENT_EMPTY"));
    }

    @Test
    void createDraftArticleRejectsContentWithOnlyLineBreak() {
        AppUser author = persistUser("create-draft-br@primatis.test");
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.createDraftArticle(
                        new CreateArticleRequest("Create Draft Br", "<p><br></p>", null), author.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_CONTENT_EMPTY"));
    }

    @Test
    void createDraftArticleRejectsContentThatIsOnlyDangerousMarkup() {
        AppUser author = persistUser("create-draft-dangerous@primatis.test");
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.createDraftArticle(
                        new CreateArticleRequest("Create Draft Dangerous", "<script>alert(1)</script>", null),
                        author.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_CONTENT_EMPTY"));
    }

    // ---------------------------------------------------------------
    // updateArticle — sécurité
    // ---------------------------------------------------------------

    @Test
    void updateArticleWithoutAuthenticationIsDenied() {
        AppUser author = persistUser("update-security-anon@primatis.test");
        Article article = persistArticleWithSlug(
                author, "Update Security Anonymous", "update-security-anonymous", ArticleStatus.DRAFT, null);

        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class).isThrownBy(() ->
                articleService.updateArticle(article.getId(), new UpdateArticleRequest(), 1L));
    }

    @Test
    void updateArticleWithoutArticleManageIsDenied() {
        AppUser author = persistUser("update-security-member@primatis.test");
        Article article = persistArticleWithSlug(
                author, "Update Security Member", "update-security-member", ArticleStatus.DRAFT, null);
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() ->
                articleService.updateArticle(article.getId(), new UpdateArticleRequest(), 1L));
    }

    // ---------------------------------------------------------------
    // updateArticle — comportement
    // ---------------------------------------------------------------

    @Test
    void updateArticleChangesTitleAndKeepsSlugUnchanged() {
        AppUser author = persistUser("update-title@primatis.test");
        AppUser editor = persistUser("update-title-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Original Title", "original-title-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("Nouveau Titre");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.title()).isEqualTo("Nouveau Titre");
        assertThat(response.slug()).isEqualTo("original-title-slug");
    }

    @Test
    void updateArticleChangesContentAfterSanitization() {
        AppUser author = persistUser("update-content@primatis.test");
        AppUser editor = persistUser("update-content-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Content", "update-content-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setContent("<p>Nouveau</p><script>alert(1)</script>");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.content()).doesNotContain("<script").contains("Nouveau");
        assertThat(response.slug()).isEqualTo("update-content-slug");
    }

    @Test
    void updateArticleChangesSummary() {
        AppUser author = persistUser("update-summary@primatis.test");
        AppUser editor = persistUser("update-summary-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Summary", "update-summary-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setSummary("Résumé mis à jour");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.summary()).isEqualTo("Résumé mis à jour");
    }

    @Test
    void updateArticleClearsSummaryWhenPresentNull() {
        AppUser author = persistUser("update-summary-clear@primatis.test");
        AppUser editor = persistUser("update-summary-clear-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Summary Clear", "update-summary-clear-slug",
                ArticleStatus.DRAFT, null);
        article.setSummary("Ancien résumé");
        entityManager.flush();
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setSummary(null);

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.summary()).isNull();
    }

    @Test
    void updateArticleKeepsFieldsUnchangedWhenAbsentFromRequest() {
        AppUser author = persistUser("update-absent@primatis.test");
        AppUser editor = persistUser("update-absent-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Absent", "update-absent-slug",
                ArticleStatus.DRAFT, null);
        article.setSummary("Résumé conservé");
        entityManager.flush();
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response =
                articleService.updateArticle(article.getId(), new UpdateArticleRequest(), editor.getId());

        assertThat(response.title()).isEqualTo("Update Absent");
        assertThat(response.summary()).isEqualTo("Résumé conservé");
        assertThat(response.content()).isEqualTo("Contenu de test");
    }

    @Test
    void updateArticleWithNoFieldsPresentDoesNotChangeLastModifiedByOrUpdatedAt() {
        AppUser author = persistUser("update-noop@primatis.test");
        AppUser editor = persistUser("update-noop-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Noop", "update-noop-slug", ArticleStatus.DRAFT, null);
        Instant originalUpdatedAt = article.getUpdatedAt();
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response =
                articleService.updateArticle(article.getId(), new UpdateArticleRequest(), editor.getId());

        assertThat(response.lastModifiedBy()).isNull();
        assertThat(response.updatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateArticleRejectsContentBecomingEmptyAfterSanitization() {
        AppUser author = persistUser("update-empty-content@primatis.test");
        AppUser editor = persistUser("update-empty-content-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Empty Content", "update-empty-content-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setContent("<script>alert(1)</script>");

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.updateArticle(article.getId(), request, editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_CONTENT_EMPTY"));
    }

    @Test
    void updateArticleRejectsBlankTitle() {
        AppUser author = persistUser("update-blank-title@primatis.test");
        AppUser editor = persistUser("update-blank-title-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Blank Title", "update-blank-title-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("   ");

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.updateArticle(article.getId(), request, editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_TITLE_MUST_NOT_BE_BLANK"));
    }

    @Test
    void updateArticleRejectsNullContent() {
        AppUser author = persistUser("update-null-content@primatis.test");
        AppUser editor = persistUser("update-null-content-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Null Content", "update-null-content-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setContent(null);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.updateArticle(article.getId(), request, editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_CONTENT_MUST_NOT_BE_NULL"));
    }

    @Test
    void updateArticleRejectsAnArchivedArticle() {
        AppUser author = persistUser("update-archived@primatis.test");
        AppUser editor = persistUser("update-archived-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Archived", "update-archived-slug",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("Tentative de modification");

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.updateArticle(article.getId(), request, editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_EDITABLE"));
    }

    @Test
    void updateArticleNonExistentThrowsNotFound() {
        AppUser editor = persistUser("update-not-found-editor@primatis.test");
        authenticateWithArticleManage(editor.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.updateArticle(999999999L, new UpdateArticleRequest(), editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void updateArticleKeepsAuthorUserUnchanged() {
        AppUser author = persistUser("update-author-unchanged@primatis.test");
        AppUser editor = persistUser("update-author-unchanged-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Author Unchanged", "update-author-unchanged-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("Nouveau titre");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.author().id()).isEqualTo(author.getId());
    }

    @Test
    void updateArticleSetsLastModifiedByUserToEditorOnRealMutation() {
        AppUser author = persistUser("update-lastmodified@primatis.test");
        AppUser editor = persistUser("update-lastmodified-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Last Modified", "update-lastmodified-slug",
                ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setSummary("Seul le résumé change");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().id()).isEqualTo(editor.getId());
    }

    // ---------------------------------------------------------------
    // updateArticle — comportement (PUBLISHED, DEV-11.8)
    // ---------------------------------------------------------------

    @Test
    void updateArticleOnPublishedArticleChangesTitleContentAndSummaryWhileKeepingStatusPublished() {
        AppUser author = persistUser("update-published-fields@primatis.test");
        AppUser editor = persistUser("update-published-fields-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Published Fields", "update-published-fields-slug",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("Titre édité après publication");
        request.setContent("<p>Contenu édité</p><script>alert(1)</script>");
        request.setSummary("Résumé édité");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.title()).isEqualTo("Titre édité après publication");
        assertThat(response.content()).isEqualTo("<p>Contenu édité</p>");
        assertThat(response.summary()).isEqualTo("Résumé édité");
    }

    @Test
    void updateArticleOnPublishedArticleKeepsPublishedAtSlugAndAuthorUnchanged() {
        AppUser author = persistUser("update-published-invariants@primatis.test");
        AppUser editor = persistUser("update-published-invariants-editor@primatis.test");
        Instant originalPublishedAt = Instant.parse("2026-08-01T10:00:00Z");
        Article article = persistArticleWithSlug(author, "Update Published Invariants",
                "update-published-invariants-slug", ArticleStatus.PUBLISHED, originalPublishedAt);
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("Nouveau titre");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.publishedAt()).isEqualTo(originalPublishedAt);
        assertThat(response.slug()).isEqualTo("update-published-invariants-slug");
        assertThat(response.author().id()).isEqualTo(author.getId());
    }

    @Test
    void updateArticleOnPublishedArticleSetsLastModifiedByUserToEditor() {
        AppUser author = persistUser("update-published-lastmodified@primatis.test");
        AppUser editor = persistUser("update-published-lastmodified-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Published Last Modified",
                "update-published-lastmodified-slug", ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setSummary("Seul le résumé change");

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().id()).isEqualTo(editor.getId());
    }

    @Test
    void updateArticleOnPublishedArticleClearsSummaryWhenPresentNull() {
        AppUser author = persistUser("update-published-clear-summary@primatis.test");
        AppUser editor = persistUser("update-published-clear-summary-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Published Clear Summary",
                "update-published-clear-summary-slug", ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        article.setSummary("Résumé initial");
        entityManager.flush();
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setSummary(null);

        ArticleResponse response = articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(response.summary()).isNull();
    }

    @Test
    void updateArticleOnPublishedArticleWithNoFieldsPresentDoesNotChangeLastModifiedByOrUpdatedAt() {
        AppUser author = persistUser("update-published-noop@primatis.test");
        Article article = persistArticleWithSlug(author, "Update Published Noop", "update-published-noop-slug",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        Instant originalUpdatedAt = article.getUpdatedAt();
        authenticateWithArticleManage(author.getId());

        ArticleResponse response = articleService.updateArticle(article.getId(), new UpdateArticleRequest(), author.getId());

        assertThat(response.lastModifiedBy()).isNull();
        assertThat(response.updatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateArticleOnPublishedArticleDoesNotCreateNewArticlePublishedNotification() {
        AppUser author = persistUser("update-published-no-notification@primatis.test");
        AppUser editor = persistUser("update-published-no-notification-editor@primatis.test");
        persistMemberWithStatus("update-published-no-notification-member@primatis.test", MemberStatus.ACTIVE);
        Article article = persistArticleWithSlug(author, "Update Published No Notification",
                "update-published-no-notification-slug", ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(editor.getId());

        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setTitle("Titre édité, aucun nouveau fanout");

        articleService.updateArticle(article.getId(), request, editor.getId());

        assertThat(findArticlePublishedNotifications(article.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // publishArticle — sécurité
    // ---------------------------------------------------------------

    @Test
    void publishArticleWithoutAuthenticationIsDenied() {
        AppUser author = persistUser("publish-security-anon@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Anon", "publish-anon", ArticleStatus.DRAFT, null);

        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> articleService.publishArticle(article.getId(), 1L));
    }

    @Test
    void publishArticleWithArticleManageOnlyIsDenied() {
        AppUser author = persistUser("publish-security-manage@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Manage", "publish-manage", ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.publishArticle(article.getId(), author.getId()));
    }

    @Test
    void publishArticleWithoutArticlePublishIsDenied() {
        AppUser author = persistUser("publish-security-member@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Member", "publish-member", ArticleStatus.DRAFT, null);
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.publishArticle(article.getId(), author.getId()));
    }

    // ---------------------------------------------------------------
    // publishArticle — transition et invariants
    // ---------------------------------------------------------------

    @Test
    void publishArticleTransitionsDraftToPublished() {
        AppUser author = persistUser("publish-transition@primatis.test");
        AppUser publisher = persistUser("publish-transition-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Transition", "publish-transition",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        ArticleResponse response = articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    void publishArticleSetsPublishedAtAfterCreatedAt() {
        AppUser author = persistUser("publish-publishedat@primatis.test");
        AppUser publisher = persistUser("publish-publishedat-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish PublishedAt", "publish-publishedat",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        ArticleResponse response = articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.publishedAt()).isAfterOrEqualTo(article.getCreatedAt());
    }

    @Test
    void publishArticleSetsLastModifiedByUserToPublisherAndKeepsAuthorUnchanged() {
        AppUser author = persistUser("publish-lastmodified@primatis.test");
        AppUser publisher = persistUser("publish-lastmodified-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Last Modified", "publish-lastmodified",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        ArticleResponse response = articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(response.author().id()).isEqualTo(author.getId());
        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().id()).isEqualTo(publisher.getId());
    }

    @Test
    void publishArticleKeepsSlugContentAndSummaryUnchanged() {
        AppUser author = persistUser("publish-unchanged@primatis.test");
        AppUser publisher = persistUser("publish-unchanged-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Unchanged", "publish-unchanged-slug",
                ArticleStatus.DRAFT, null);
        article.setSummary("Résumé original");
        entityManager.flush();
        authenticateWithArticlePublish(publisher.getId());

        ArticleResponse response = articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(response.slug()).isEqualTo("publish-unchanged-slug");
        assertThat(response.content()).isEqualTo("Contenu de test");
        assertThat(response.summary()).isEqualTo("Résumé original");
        assertThat(response.title()).isEqualTo("Publish Unchanged");
    }

    @Test
    void publishArticleNonExistentThrowsNotFound() {
        AppUser publisher = persistUser("publish-not-found-publisher@primatis.test");
        authenticateWithArticlePublish(publisher.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.publishArticle(999999999L, publisher.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void publishArticleRejectsAnAlreadyPublishedArticle() {
        AppUser author = persistUser("publish-already-published@primatis.test");
        AppUser publisher = persistUser("publish-already-published-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Already Published", "publish-already-published",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticlePublish(publisher.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.publishArticle(article.getId(), publisher.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_PUBLISHABLE"));
    }

    @Test
    void publishArticleRejectsAnArchivedArticle() {
        AppUser author = persistUser("publish-archived@primatis.test");
        AppUser publisher = persistUser("publish-archived-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Archived", "publish-archived-source",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticlePublish(publisher.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.publishArticle(article.getId(), publisher.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_PUBLISHABLE"));
    }

    // ---------------------------------------------------------------
    // publishArticle — fanout ARTICLE_PUBLISHED
    // ---------------------------------------------------------------

    @Test
    void publishArticleWithZeroActiveMembersSucceedsWithZeroNotifications() {
        AppUser author = persistUser("publish-zero-active@primatis.test");
        AppUser publisher = persistUser("publish-zero-active-publisher@primatis.test");
        Article article = persistArticleWithSlug(author, "Publish Zero Active", "publish-zero-active",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        ArticleResponse response = articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(findArticlePublishedNotifications(article.getId())).isEmpty();
    }

    @Test
    void publishArticleCreatesOneNotificationForOneActiveMember() {
        AppUser author = persistUser("publish-one-active@primatis.test");
        AppUser publisher = persistUser("publish-one-active-publisher@primatis.test");
        AppUser activeMember = persistMemberWithStatus("publish-one-active-member@primatis.test", MemberStatus.ACTIVE);
        Article article = persistArticleWithSlug(author, "Publish One Active", "publish-one-active",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        articleService.publishArticle(article.getId(), publisher.getId());

        List<Notification> notifications = findArticlePublishedNotifications(article.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getRecipientUser().getId()).isEqualTo(activeMember.getId());
        assertThat(notifications.get(0).getNotificationType()).isEqualTo(NotificationType.ARTICLE_PUBLISHED);
    }

    @Test
    void publishArticleCreatesOneNotificationPerActiveMemberForMultipleMembers() {
        AppUser author = persistUser("publish-n-active@primatis.test");
        AppUser publisher = persistUser("publish-n-active-publisher@primatis.test");
        persistMemberWithStatus("publish-n-active-member-1@primatis.test", MemberStatus.ACTIVE);
        persistMemberWithStatus("publish-n-active-member-2@primatis.test", MemberStatus.ACTIVE);
        persistMemberWithStatus("publish-n-active-member-3@primatis.test", MemberStatus.ACTIVE);
        Article article = persistArticleWithSlug(author, "Publish N Active", "publish-n-active",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(findArticlePublishedNotifications(article.getId())).hasSize(3);
    }

    @Test
    void publishArticleNeverNotifiesBlockedExpiredOrNullMemberStatusMembers() {
        AppUser author = persistUser("publish-non-active@primatis.test");
        AppUser publisher = persistUser("publish-non-active-publisher@primatis.test");
        persistMemberWithStatus("publish-non-active-blocked@primatis.test", MemberStatus.BLOCKED);
        persistMemberWithStatus("publish-non-active-expired@primatis.test", MemberStatus.EXPIRED);
        persistUser("publish-non-active-null-status@primatis.test"); // memberStatus jamais renseigné (non adhérent)
        Article article = persistArticleWithSlug(author, "Publish Non Active", "publish-non-active",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        articleService.publishArticle(article.getId(), publisher.getId());

        assertThat(findArticlePublishedNotifications(article.getId())).isEmpty();
    }

    @Test
    void publishArticleNotificationReferencesOnlyArticleOrigin() {
        AppUser author = persistUser("publish-origin@primatis.test");
        AppUser publisher = persistUser("publish-origin-publisher@primatis.test");
        persistMemberWithStatus("publish-origin-member@primatis.test", MemberStatus.ACTIVE);
        Article article = persistArticleWithSlug(author, "Publish Origin", "publish-origin",
                ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(publisher.getId());

        articleService.publishArticle(article.getId(), publisher.getId());

        Notification notification = findArticlePublishedNotifications(article.getId()).get(0);
        assertThat(notification.getArticle().getId()).isEqualTo(article.getId());
        assertThat(notification.getLoan()).isNull();
        assertThat(notification.getReservation()).isNull();
        assertThat(notification.getFine()).isNull();
    }

    // ---------------------------------------------------------------
    // archiveArticle — sécurité
    // ---------------------------------------------------------------

    @Test
    void archiveArticleWithoutAuthenticationIsDenied() {
        AppUser author = persistUser("archive-security-anon@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Anon", "archive-anon",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> articleService.archiveArticle(article.getId(), 1L));
    }

    @Test
    void archiveArticleWithArticlePublishOnlyIsDenied() {
        AppUser author = persistUser("archive-security-publish@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Publish", "archive-publish",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticlePublish(author.getId());

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.archiveArticle(article.getId(), author.getId()));
    }

    @Test
    void archiveArticleWithoutArticleManageIsDenied() {
        AppUser author = persistUser("archive-security-member@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Member", "archive-member",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.archiveArticle(article.getId(), author.getId()));
    }

    // ---------------------------------------------------------------
    // archiveArticle — transition et invariants
    // ---------------------------------------------------------------

    @Test
    void archiveArticleTransitionsPublishedToArchived() {
        AppUser author = persistUser("archive-transition@primatis.test");
        AppUser archiver = persistUser("archive-transition-archiver@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Transition", "archive-transition",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(archiver.getId());

        ArticleResponse response = articleService.archiveArticle(article.getId(), archiver.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.ARCHIVED);
    }

    @Test
    void archiveArticleSetsLastModifiedByUserToArchiverAndKeepsAuthorUnchanged() {
        AppUser author = persistUser("archive-lastmodified@primatis.test");
        AppUser archiver = persistUser("archive-lastmodified-archiver@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Last Modified", "archive-lastmodified",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(archiver.getId());

        ArticleResponse response = articleService.archiveArticle(article.getId(), archiver.getId());

        assertThat(response.author().id()).isEqualTo(author.getId());
        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().id()).isEqualTo(archiver.getId());
    }

    @Test
    void archiveArticleKeepsPublishedAtSlugContentSummaryUnchanged() {
        AppUser author = persistUser("archive-unchanged@primatis.test");
        AppUser archiver = persistUser("archive-unchanged-archiver@primatis.test");
        Instant originalPublishedAt = Instant.parse("2026-08-01T10:00:00Z");
        Article article = persistArticleWithSlug(author, "Archive Unchanged", "archive-unchanged-slug",
                ArticleStatus.PUBLISHED, originalPublishedAt);
        article.setSummary("Résumé original");
        entityManager.flush();
        authenticateWithArticleManage(archiver.getId());

        ArticleResponse response = articleService.archiveArticle(article.getId(), archiver.getId());

        assertThat(response.publishedAt()).isEqualTo(originalPublishedAt);
        assertThat(response.slug()).isEqualTo("archive-unchanged-slug");
        assertThat(response.content()).isEqualTo("Contenu de test");
        assertThat(response.summary()).isEqualTo("Résumé original");
        assertThat(response.title()).isEqualTo("Archive Unchanged");
    }

    @Test
    void archiveArticleNonExistentThrowsNotFound() {
        AppUser archiver = persistUser("archive-not-found-archiver@primatis.test");
        authenticateWithArticleManage(archiver.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.archiveArticle(999999999L, archiver.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void archiveArticleRejectsADraftArticle() {
        AppUser author = persistUser("archive-draft@primatis.test");
        AppUser archiver = persistUser("archive-draft-archiver@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Draft", "archive-draft", ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(archiver.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.archiveArticle(article.getId(), archiver.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_ARCHIVABLE"));
    }

    @Test
    void archiveArticleRejectsAnAlreadyArchivedArticle() {
        AppUser author = persistUser("archive-already@primatis.test");
        AppUser archiver = persistUser("archive-already-archiver@primatis.test");
        Article article = persistArticleWithSlug(author, "Archive Already", "archive-already",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(archiver.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.archiveArticle(article.getId(), archiver.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_ARCHIVABLE"));
    }

    @Test
    void archiveArticleCreatesNoNotification() {
        AppUser author = persistUser("archive-no-notification@primatis.test");
        AppUser archiver = persistUser("archive-no-notification-archiver@primatis.test");
        persistMemberWithStatus("archive-no-notification-member@primatis.test", MemberStatus.ACTIVE);
        Article article = persistArticleWithSlug(author, "Archive No Notification", "archive-no-notification",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        authenticateWithArticleManage(archiver.getId());

        articleService.archiveArticle(article.getId(), archiver.getId());

        assertThat(findArticlePublishedNotifications(article.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // deleteDraftArticle — sécurité
    // ---------------------------------------------------------------

    @Test
    void deleteDraftArticleWithoutAuthenticationIsDenied() {
        AppUser author = persistUser("delete-security-anon@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Anon", "delete-anon", ArticleStatus.DRAFT, null);

        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> articleService.deleteDraftArticle(article.getId()));
    }

    @Test
    void deleteDraftArticleWithArticlePublishOnlyIsDenied() {
        AppUser author = persistUser("delete-security-publish@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Publish", "delete-publish", ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(author.getId());

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.deleteDraftArticle(article.getId()));
    }

    @Test
    void deleteDraftArticleWithoutArticleManageIsDenied() {
        AppUser author = persistUser("delete-security-member@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Member", "delete-member", ArticleStatus.DRAFT, null);
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.deleteDraftArticle(article.getId()));
    }

    // ---------------------------------------------------------------
    // deleteDraftArticle — comportement
    // ---------------------------------------------------------------

    @Test
    void deleteDraftArticleRemovesDraftArticlePhysically() {
        AppUser author = persistUser("delete-physical@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Physical", "delete-physical", ArticleStatus.DRAFT, null);
        Long articleId = article.getId();
        authenticateWithArticleManage(author.getId());

        articleService.deleteDraftArticle(articleId);
        entityManager.flush();

        assertThat(entityManager.find(Article.class, articleId)).isNull();
    }

    @Test
    void deleteDraftArticleNonExistentThrowsNotFound() {
        AppUser actor = persistUser("delete-not-found@primatis.test");
        authenticateWithArticleManage(actor.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.deleteDraftArticle(999999999L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void deleteDraftArticleRejectsAPublishedArticle() {
        AppUser author = persistUser("delete-published@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Published", "delete-published",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        Long articleId = article.getId();
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.deleteDraftArticle(articleId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_DELETABLE"));
        assertThat(entityManager.find(Article.class, articleId)).isNotNull();
    }

    @Test
    void deleteDraftArticleRejectsAnArchivedArticle() {
        AppUser author = persistUser("delete-archived@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Archived", "delete-archived",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        Long articleId = article.getId();
        authenticateWithArticleManage(author.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.deleteDraftArticle(articleId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_DELETABLE"));
        assertThat(entityManager.find(Article.class, articleId)).isNotNull();
    }

    /**
     * Test obligatoire DEV-11.9 (mission §26/§44) : ferme explicitement le
     * risque documenté en DEV-11.8 §20 — un {@code DRAFT} peut désormais
     * réellement posséder des {@code ArticleTag} ({@link
     * ArticleService#associateTags}), le hard-delete doit rester
     * fonctionnel malgré {@code fk_article_tag_article_id ON DELETE
     * RESTRICT}. {@code flush()} explicite après la fixture (même précaution
     * que partout ailleurs dans ce fichier pour un scénario de suppression
     * réelle — {@code findByIdForUpdate} déclenche de toute façon un
     * auto-flush avant sa requête, cette ligne documente l'intention plutôt
     * qu'elle ne change le résultat).
     */
    @Test
    void deleteDraftArticleWithAssociatedTagsSucceedsAndKeepsTagsThemselves() {
        AppUser author = persistUser("delete-tagged-draft@primatis.test");
        Article article = persistArticleWithSlug(author, "Delete Tagged Draft", "delete-tagged-draft",
                ArticleStatus.DRAFT, null);
        Tag tagA = persistTag("delete-tagged-draft-a", "Tag A");
        Tag tagB = persistTag("delete-tagged-draft-b", "Tag B");
        linkArticleTag(article, tagA);
        linkArticleTag(article, tagB);
        Long articleId = article.getId();
        Long tagAId = tagA.getId();
        Long tagBId = tagB.getId();
        entityManager.flush();
        authenticateWithArticleManage(author.getId());

        articleService.deleteDraftArticle(articleId);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Article.class, articleId)).isNull();
        Long remainingAssociations = entityManager
                .createQuery("SELECT count(at) FROM ArticleTag at WHERE at.article.id = :articleId", Long.class)
                .setParameter("articleId", articleId)
                .getSingleResult();
        assertThat(remainingAssociations).isZero();
        assertThat(entityManager.find(Tag.class, tagAId)).isNotNull();
        assertThat(entityManager.find(Tag.class, tagBId)).isNotNull();
    }

    // ---------------------------------------------------------------
    // associateTags — sécurité
    // ---------------------------------------------------------------

    @Test
    void associateTagsWithoutAuthenticationIsDenied() {
        AppUser author = persistUser("associate-security-anon@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Anon", "associate-anon", ArticleStatus.DRAFT, null);

        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> articleService.associateTags(article.getId(),
                        new UpdateArticleTagsRequest(List.of()), 1L));
    }

    @Test
    void associateTagsWithArticlePublishOnlyIsDenied() {
        AppUser author = persistUser("associate-security-publish@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Publish", "associate-publish", ArticleStatus.DRAFT, null);
        authenticateWithArticlePublish(author.getId());

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.associateTags(article.getId(),
                        new UpdateArticleTagsRequest(List.of()), author.getId()));
    }

    @Test
    void associateTagsWithoutArticleManageIsDenied() {
        AppUser author = persistUser("associate-security-member@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Member", "associate-member", ArticleStatus.DRAFT, null);
        authenticateWithoutArticleManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> articleService.associateTags(article.getId(),
                        new UpdateArticleTagsRequest(List.of()), author.getId()));
    }

    // ---------------------------------------------------------------
    // associateTags — comportement
    // ---------------------------------------------------------------

    @Test
    void associateTagsWithEmptyListSucceedsWithNoTags() {
        AppUser author = persistUser("associate-zero@primatis.test");
        AppUser editor = persistUser("associate-zero-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Zero", "associate-zero", ArticleStatus.DRAFT, null);
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.associateTags(article.getId(), new UpdateArticleTagsRequest(List.of()), editor.getId());

        assertThat(response.tags()).isEmpty();
    }

    @Test
    void associateTagsWithOneTagSucceeds() {
        AppUser author = persistUser("associate-one@primatis.test");
        AppUser editor = persistUser("associate-one-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate One", "associate-one", ArticleStatus.DRAFT, null);
        Tag tag = persistTag("associate-one-tag", "Tag One");

        authenticateWithArticleManage(editor.getId());
        ArticleResponse response = articleService.associateTags(
                article.getId(), new UpdateArticleTagsRequest(List.of(tag.getId())), editor.getId());

        assertThat(response.tags()).extracting(TagResponse::id).containsExactly(tag.getId());
    }

    @Test
    void associateTagsWithMultipleTagsSucceeds() {
        AppUser author = persistUser("associate-multi@primatis.test");
        AppUser editor = persistUser("associate-multi-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Multi", "associate-multi", ArticleStatus.DRAFT, null);
        Tag tagA = persistTag("associate-multi-a", "Tag A");
        Tag tagB = persistTag("associate-multi-b", "Tag B");
        Tag tagC = persistTag("associate-multi-c", "Tag C");

        authenticateWithArticleManage(editor.getId());
        ArticleResponse response = articleService.associateTags(article.getId(),
                new UpdateArticleTagsRequest(List.of(tagA.getId(), tagB.getId(), tagC.getId())), editor.getId());

        assertThat(response.tags()).extracting(TagResponse::id)
                .containsExactlyInAnyOrder(tagA.getId(), tagB.getId(), tagC.getId());
    }

    @Test
    void associateTagsReplacesPreviousSelectionCompletely() {
        AppUser author = persistUser("associate-replace@primatis.test");
        AppUser editor = persistUser("associate-replace-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Replace", "associate-replace", ArticleStatus.DRAFT, null);
        Tag tagA = persistTag("associate-replace-a", "Tag A");
        Tag tagB = persistTag("associate-replace-b", "Tag B");
        authenticateWithArticleManage(editor.getId());
        articleService.associateTags(article.getId(), new UpdateArticleTagsRequest(List.of(tagA.getId())), editor.getId());

        ArticleResponse response = articleService.associateTags(
                article.getId(), new UpdateArticleTagsRequest(List.of(tagB.getId())), editor.getId());

        assertThat(response.tags()).extracting(TagResponse::id).containsExactly(tagB.getId());
    }

    @Test
    void associateTagsWithDuplicateIdsProducesNoDuplicateAssociation() {
        AppUser author = persistUser("associate-duplicate@primatis.test");
        AppUser editor = persistUser("associate-duplicate-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Duplicate", "associate-duplicate", ArticleStatus.DRAFT, null);
        Tag tag = persistTag("associate-duplicate-tag", "Tag");
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.associateTags(
                article.getId(), new UpdateArticleTagsRequest(List.of(tag.getId(), tag.getId())), editor.getId());

        assertThat(response.tags()).extracting(TagResponse::id).containsExactly(tag.getId());
    }

    @Test
    void associateTagsRejectsUnknownTagIdAtomically() {
        AppUser author = persistUser("associate-unknown@primatis.test");
        AppUser editor = persistUser("associate-unknown-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Unknown", "associate-unknown", ArticleStatus.DRAFT, null);
        Tag tag = persistTag("associate-unknown-tag", "Tag");
        authenticateWithArticleManage(editor.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.associateTags(article.getId(),
                        new UpdateArticleTagsRequest(List.of(tag.getId(), 999999999L)), editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("TAG_NOT_FOUND"));

        List<Tag> tags = articleRepositoryFindTags(article.getId());
        assertThat(tags).isEmpty();
    }

    @Test
    void associateTagsNonExistentArticleThrowsNotFound() {
        AppUser editor = persistUser("associate-not-found-editor@primatis.test");
        authenticateWithArticleManage(editor.getId());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> articleService.associateTags(999999999L,
                        new UpdateArticleTagsRequest(List.of()), editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_FOUND"));
    }

    @Test
    void associateTagsOnDraftArticleSucceeds() {
        AppUser author = persistUser("associate-draft@primatis.test");
        AppUser editor = persistUser("associate-draft-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Draft", "associate-draft", ArticleStatus.DRAFT, null);
        Tag tag = persistTag("associate-draft-tag", "Tag");
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.associateTags(
                article.getId(), new UpdateArticleTagsRequest(List.of(tag.getId())), editor.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    void associateTagsOnPublishedArticleSucceeds() {
        AppUser author = persistUser("associate-published@primatis.test");
        AppUser editor = persistUser("associate-published-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Published", "associate-published",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        Tag tag = persistTag("associate-published-tag", "Tag");
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.associateTags(
                article.getId(), new UpdateArticleTagsRequest(List.of(tag.getId())), editor.getId());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.tags()).extracting(TagResponse::id).containsExactly(tag.getId());
    }

    @Test
    void associateTagsOnArchivedArticleIsRejected() {
        AppUser author = persistUser("associate-archived@primatis.test");
        AppUser editor = persistUser("associate-archived-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Archived", "associate-archived",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        Tag tag = persistTag("associate-archived-tag", "Tag");
        authenticateWithArticleManage(editor.getId());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> articleService.associateTags(article.getId(),
                        new UpdateArticleTagsRequest(List.of(tag.getId())), editor.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ARTICLE_NOT_EDITABLE"));
    }

    @Test
    void associateTagsSetsLastModifiedByUserToActor() {
        AppUser author = persistUser("associate-lastmodified@primatis.test");
        AppUser editor = persistUser("associate-lastmodified-editor@primatis.test");
        Article article = persistArticleWithSlug(author, "Associate Last Modified", "associate-lastmodified",
                ArticleStatus.DRAFT, null);
        Tag tag = persistTag("associate-lastmodified-tag", "Tag");
        authenticateWithArticleManage(editor.getId());

        ArticleResponse response = articleService.associateTags(
                article.getId(), new UpdateArticleTagsRequest(List.of(tag.getId())), editor.getId());

        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().id()).isEqualTo(editor.getId());
    }

    private List<Tag> articleRepositoryFindTags(Long articleId) {
        return entityManager
                .createQuery("SELECT at.tag FROM ArticleTag at WHERE at.article.id = :articleId ORDER BY at.tag.id ASC", Tag.class)
                .setParameter("articleId", articleId)
                .getResultList();
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private List<Notification> findArticlePublishedNotifications(Long articleId) {
        return entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.article.id = :articleId", Notification.class)
                .setParameter("articleId", articleId)
                .getResultList();
    }

    private AppUser persistMemberWithStatus(String email, MemberStatus memberStatus) {
        AppUser user = persistUser(email);
        user.setMemberStatus(memberStatus);
        entityManager.flush();
        return user;
    }

    private AppUser persistUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private Article persistArticle(AppUser author, String title, ArticleStatus status, Instant publishedAt) {
        return persistArticleWithSlug(author, title, "slug-" + System.nanoTime(), status, publishedAt);
    }

    private Article persistArticleWithSlug(
            AppUser author, String title, String slug, ArticleStatus status, Instant publishedAt) {
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle(title);
        article.setContent("Contenu de test");
        article.setSlug(slug);
        article.setArticleStatus(status);
        article.setPublishedAt(publishedAt);
        article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        entityManager.persist(article);
        return article;
    }

    private Tag persistTag(String code, String label) {
        Tag tag = new Tag();
        tag.setCode(code);
        tag.setLabel(label);
        entityManager.persist(tag);
        return tag;
    }

    private void linkArticleTag(Article article, Tag tag) {
        ArticleTag articleTag = new ArticleTag();
        articleTag.setId(new ArticleTagId(article.getId(), tag.getId()));
        articleTag.setArticle(entityManager.getReference(Article.class, article.getId()));
        articleTag.setTag(entityManager.getReference(Tag.class, tag.getId()));
        entityManager.persist(articleTag);
    }
}
