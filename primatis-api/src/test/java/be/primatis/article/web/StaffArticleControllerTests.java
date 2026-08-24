package be.primatis.article.web;

import be.primatis.article.Article;
import be.primatis.article.ArticleRepository;
import be.primatis.article.ArticleStatus;
import be.primatis.article.ArticleTagRepository;
import be.primatis.article.Tag;
import be.primatis.article.TagRepository;
import be.primatis.config.JwtProperties;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat REST staff de gestion des Articles {@code DRAFT} (DEV-11.6) :
 * {@code POST}/{@code PATCH /api/v1/staff/articles(/{articleId})}. JWT
 * signés manuellement (même principe que {@code StaffTitleControllerTests}) :
 * {@code ARTICLE_MANAGE} étant une simple autorité vérifiée par {@code
 * @PreAuthorize}, un token portant cette permission suffit à prouver le
 * comportement de l'endpoint — le bootstrap réel {@code ARTICLE_MANAGE →
 * ROLE_LIBRARIAN/ROLE_ADMIN} (V002) est déjà couvert par {@code
 * RbacBootstrapTests}, non re-testé ici.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffArticleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleTagRepository articleTagRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdArticleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdTagIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtures() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long articleId : createdArticleIds) {
                articleTagRepository.deleteByIdArticleId(articleId);
            }
            for (Long articleId : createdArticleIds) {
                articleRepository.deleteById(articleId);
            }
            for (Long tagId : createdTagIds) {
                tagRepository.deleteById(tagId);
            }
            for (Long userId : createdUserIds) {
                appUserRepository.deleteById(userId);
            }
        });
        createdArticleIds.clear();
        createdUserIds.clear();
        createdTagIds.clear();
    }

    // ---------------------------------------------------------------
    // GET /api/v1/staff/articles — sécurité (DEV-11.12A)
    // ---------------------------------------------------------------

    @Test
    void listStaffArticlesWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles")).andExpect(status().isUnauthorized());
    }

    @Test
    void listStaffArticlesWithArticlePublishOnlyIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listStaffArticlesWithRoleMemberIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listStaffArticlesWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void listStaffArticlesWithAdminAndArticleManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/staff/articles — comportement
    // ---------------------------------------------------------------

    @Test
    void listStaffArticlesRejectsInvalidPageParam() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles").param("page", "-1")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listStaffArticlesRejectsInvalidSizeParam() throws Exception {
        mockMvc.perform(get("/api/v1/staff/articles").param("size", "101")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listStaffArticlesReturnsAllStatusesWithExpectedJsonContract() throws Exception {
        AppUser author = persistUser("staff-list-ctrl-author@primatis.test");
        Article draft = persistDraftArticle(author, "Staff List Ctrl Draft", "staff-list-ctrl-draft");
        Article archived = persistArticleWithStatus(author, "Staff List Ctrl Archived", "staff-list-ctrl-archived",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(get("/api/v1/staff/articles").param("size", "100")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + draft.getId() + ")].articleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.content[?(@.id == " + archived.getId() + ")].articleStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.content[?(@.id == " + draft.getId() + ")].content").doesNotExist())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/staff/articles/{articleId} — sécurité (DEV-11.12A)
    // ---------------------------------------------------------------

    @Test
    void getStaffArticleByIdWithoutJwtIsUnauthorized() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-security-anon@primatis.test");
        Article article = persistDraftArticle(author, "Staff Detail Security Anon", "staff-detail-ctrl-security-anon");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStaffArticleByIdWithArticlePublishOnlyIsForbidden() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-security-publish@primatis.test");
        Article article = persistDraftArticle(author, "Staff Detail Security Publish", "staff-detail-ctrl-security-publish");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStaffArticleByIdWithRoleMemberIsForbidden() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-security-member@primatis.test");
        Article article = persistDraftArticle(author, "Staff Detail Security Member", "staff-detail-ctrl-security-member");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStaffArticleByIdWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-librarian-author@primatis.test");
        Article article = persistDraftArticle(author, "Staff Detail Librarian", "staff-detail-ctrl-librarian");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void getStaffArticleByIdWithAdminAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-admin-author@primatis.test");
        Article article = persistDraftArticle(author, "Staff Detail Admin", "staff-detail-ctrl-admin");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/staff/articles/{articleId} — comportement
    // ---------------------------------------------------------------

    @Test
    void getStaffArticleByIdReturnsADraftArticleWithExpectedJsonContract() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-draft-author@primatis.test");
        Article article = persistDraftArticle(author, "Staff Detail Draft Ctrl", "staff-detail-ctrl-draft");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.slug").value("staff-detail-ctrl-draft"))
                .andExpect(jsonPath("$.author.id").value(author.getId()))
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    void getStaffArticleByIdReturnsAnArchivedArticle() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-archived-author@primatis.test");
        Article article = persistArticleWithStatus(author, "Staff Detail Archived Ctrl", "staff-detail-ctrl-archived",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleStatus").value("ARCHIVED"));
    }

    @Test
    void getStaffArticleByIdReturnsAPublishedArticle() throws Exception {
        AppUser author = persistUser("staff-detail-ctrl-published-author@primatis.test");
        Article article = persistArticleWithStatus(author, "Staff Detail Published Ctrl", "staff-detail-ctrl-published",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-08-01T10:00:00Z"));
    }

    @Test
    void getStaffArticleByIdNonExistentReturns404() throws Exception {
        AppUser actor = persistUser("staff-detail-ctrl-not-found-actor@primatis.test");

        mockMvc.perform(get("/api/v1/staff/articles/{articleId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(actor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/staff/articles — sécurité
    // ---------------------------------------------------------------

    @Test
    void createDraftArticleWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Security Anonymous CTRL", "<p>Contenu</p>")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDraftArticleWithoutArticleManageIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Security Member CTRL", "<p>Contenu</p>")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDraftArticleWithArticlePublishOnlyIsForbidden() throws Exception {
        // Mission DEV-11.6 §13 : ARTICLE_PUBLISH seul ne doit jamais suffire
        // pour create/update DRAFT — uniquement ARTICLE_MANAGE.
        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(1L, List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Security Publish Only CTRL", "<p>Contenu</p>")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDraftArticleWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-create-librarian@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Security Librarian CTRL", "<p>Contenu</p>")))
                .andExpect(status().isCreated());

        trackCreatedArticleBySlug("create-security-librarian-ctrl");
    }

    @Test
    void createDraftArticleWithAdminAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-create-admin@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Security Admin CTRL", "<p>Contenu</p>")))
                .andExpect(status().isCreated());

        trackCreatedArticleBySlug("create-security-admin-ctrl");
    }

    // ---------------------------------------------------------------
    // POST /api/v1/staff/articles — comportement
    // ---------------------------------------------------------------

    @Test
    void createDraftArticleReturnsCreatedWithoutLocationHeader() throws Exception {
        // Aucun GET /api/v1/staff/articles/{id} n'existe encore (hors scope
        // DEV-11.6) — même précédent exact que Loan/Reservation.
        AppUser author = persistUser("staff-create-no-location@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create No Location CTRL", "<p>Contenu</p>")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"));

        trackCreatedArticleBySlug("create-no-location-ctrl");
    }

    @Test
    void createDraftArticleResponseBodyReflectsDraftInvariants() throws Exception {
        AppUser author = persistUser("staff-create-body@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Body Invariants CTRL", "<p>Contenu</p>")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.articleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.lastModifiedBy").doesNotExist())
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags").isEmpty())
                .andExpect(jsonPath("$.slug").value("create-body-invariants-ctrl"))
                .andExpect(jsonPath("$.author.id").value(author.getId()));

        trackCreatedArticleBySlug("create-body-invariants-ctrl");
    }

    @Test
    void createDraftArticleWithBlankTitleReturns400() throws Exception {
        AppUser author = persistUser("staff-create-blank-title@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson(" ", "<p>Contenu</p>")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDraftArticleWithContentEmptyAfterSanitizationReturns409() throws Exception {
        AppUser author = persistUser("staff-create-empty-content@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles")
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createArticleJson("Create Empty Content CTRL", "<script>alert(1)</script>")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_CONTENT_EMPTY"));
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/staff/articles/{articleId} — sécurité
    // ---------------------------------------------------------------

    @Test
    void updateArticleWithoutJwtIsUnauthorized() throws Exception {
        AppUser author = persistUser("staff-update-security-anon@primatis.test");
        Article article = persistDraftArticle(author, "Update Security Anonymous", "update-security-anonymous-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouveau\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateArticleWithoutArticleManageIsForbidden() throws Exception {
        AppUser author = persistUser("staff-update-security-member@primatis.test");
        Article article = persistDraftArticle(author, "Update Security Member", "update-security-member-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouveau\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateArticleWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-update-librarian-author@primatis.test");
        AppUser editor = persistUser("staff-update-librarian-editor@primatis.test");
        Article article = persistDraftArticle(author, "Update Librarian", "update-librarian-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouveau Titre Librarian\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateArticleWithAdminAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-update-admin-author@primatis.test");
        AppUser editor = persistUser("staff-update-admin-editor@primatis.test");
        Article article = persistDraftArticle(author, "Update Admin", "update-admin-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouveau Titre Admin\"}"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/staff/articles/{articleId} — comportement
    // ---------------------------------------------------------------

    @Test
    void updateArticlePatchesTitleAndKeepsSlugUnchanged() throws Exception {
        AppUser author = persistUser("staff-update-behavior-author@primatis.test");
        AppUser editor = persistUser("staff-update-behavior-editor@primatis.test");
        Article article = persistDraftArticle(author, "Update Behavior Original", "update-behavior-slug-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Update Behavior Changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Update Behavior Changed"))
                .andExpect(jsonPath("$.slug").value("update-behavior-slug-ctrl"))
                .andExpect(jsonPath("$.lastModifiedBy.id").value(editor.getId()));
    }

    @Test
    void updateArticleOnPublishedArticleReturns200AndKeepsStatusPublished() throws Exception {
        AppUser author = persistUser("staff-update-published-author@primatis.test");
        AppUser editor = persistUser("staff-update-published-editor@primatis.test");
        Article article = persistArticleWithStatus(author, "Update Published CTRL", "update-published-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titre édité après publication CTRL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.title").value("Titre édité après publication CTRL"))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.slug").value("update-published-ctrl"))
                .andExpect(jsonPath("$.lastModifiedBy.id").value(editor.getId()));
    }

    @Test
    void updateArticleOnArchivedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-update-archived-author@primatis.test");
        AppUser editor = persistUser("staff-update-archived-editor@primatis.test");
        Article article = persistArticleWithStatus(author, "Update Archived CTRL", "update-archived-ctrl",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tentative\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_EDITABLE"));
    }

    @Test
    void updateArticleNonExistentReturns404() throws Exception {
        AppUser editor = persistUser("staff-update-not-found-editor@primatis.test");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tentative\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/staff/articles/{articleId}/publish — sécurité
    // ---------------------------------------------------------------

    @Test
    void publishArticleWithoutJwtIsUnauthorized() throws Exception {
        AppUser author = persistUser("staff-publish-security-anon@primatis.test");
        Article article = persistDraftArticle(author, "Publish Security Anonymous", "publish-security-anonymous-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishArticleWithArticleManageOnlyIsForbidden() throws Exception {
        AppUser author = persistUser("staff-publish-security-manage@primatis.test");
        Article article = persistDraftArticle(author, "Publish Security Manage", "publish-security-manage-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishArticleWithRoleMemberIsForbidden() throws Exception {
        AppUser author = persistUser("staff-publish-security-member@primatis.test");
        Article article = persistDraftArticle(author, "Publish Security Member", "publish-security-member-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishArticleWithLibrarianAndArticlePublishIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-publish-librarian-author@primatis.test");
        AppUser publisher = persistUser("staff-publish-librarian-publisher@primatis.test");
        Article article = persistDraftArticle(author, "Publish Librarian", "publish-librarian-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(publisher.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isOk());
    }

    @Test
    void publishArticleWithAdminAndArticlePublishIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-publish-admin-author@primatis.test");
        AppUser publisher = persistUser("staff-publish-admin-publisher@primatis.test");
        Article article = persistDraftArticle(author, "Publish Admin", "publish-admin-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(publisher.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/staff/articles/{articleId}/publish — comportement
    // ---------------------------------------------------------------

    @Test
    void publishArticleResponseReflectsPublishedInvariants() throws Exception {
        AppUser author = persistUser("staff-publish-body-author@primatis.test");
        AppUser publisher = persistUser("staff-publish-body-publisher@primatis.test");
        Article article = persistDraftArticle(author, "Publish Body Invariants", "publish-body-invariants-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(publisher.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.slug").value("publish-body-invariants-ctrl"))
                .andExpect(jsonPath("$.author.id").value(author.getId()))
                .andExpect(jsonPath("$.lastModifiedBy.id").value(publisher.getId()));
    }

    @Test
    void publishArticleOnAlreadyPublishedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-publish-already-author@primatis.test");
        AppUser publisher = persistUser("staff-publish-already-publisher@primatis.test");
        Article article = persistArticleWithStatus(author, "Publish Already Published CTRL",
                "publish-already-published-ctrl", ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(publisher.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_PUBLISHABLE"));
    }

    @Test
    void publishArticleOnArchivedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-publish-archived-author@primatis.test");
        AppUser publisher = persistUser("staff-publish-archived-publisher@primatis.test");
        Article article = persistArticleWithStatus(author, "Publish Archived CTRL", "publish-archived-ctrl",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(publisher.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_PUBLISHABLE"));
    }

    @Test
    void publishArticleNonExistentReturns404() throws Exception {
        AppUser publisher = persistUser("staff-publish-not-found-publisher@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/publish", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(publisher.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/staff/articles/{articleId}/archive — sécurité
    // ---------------------------------------------------------------

    @Test
    void archiveArticleWithoutJwtIsUnauthorized() throws Exception {
        AppUser author = persistUser("staff-archive-security-anon@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Security Anonymous", "archive-security-anonymous-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void archiveArticleWithArticlePublishOnlyIsForbidden() throws Exception {
        AppUser author = persistUser("staff-archive-security-publish@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Security Publish", "archive-security-publish-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveArticleWithRoleMemberIsForbidden() throws Exception {
        AppUser author = persistUser("staff-archive-security-member@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Security Member", "archive-security-member-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveArticleWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-archive-librarian-author@primatis.test");
        AppUser archiver = persistUser("staff-archive-librarian-archiver@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Librarian", "archive-librarian-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void archiveArticleWithAdminAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-archive-admin-author@primatis.test");
        AppUser archiver = persistUser("staff-archive-admin-archiver@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Admin", "archive-admin-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/staff/articles/{articleId}/archive — comportement
    // ---------------------------------------------------------------

    @Test
    void archiveArticleResponseReflectsArchivedInvariants() throws Exception {
        AppUser author = persistUser("staff-archive-body-author@primatis.test");
        AppUser archiver = persistUser("staff-archive-body-archiver@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Body Invariants", "archive-body-invariants-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$.slug").value("archive-body-invariants-ctrl"))
                .andExpect(jsonPath("$.author.id").value(author.getId()))
                .andExpect(jsonPath("$.lastModifiedBy.id").value(archiver.getId()));
    }

    @Test
    void archiveArticleOnADraftArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-archive-draft-author@primatis.test");
        AppUser archiver = persistUser("staff-archive-draft-archiver@primatis.test");
        Article article = persistDraftArticle(author, "Archive Draft CTRL", "archive-draft-ctrl");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_ARCHIVABLE"));
    }

    @Test
    void archiveArticleOnAnAlreadyArchivedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-archive-already-author@primatis.test");
        AppUser archiver = persistUser("staff-archive-already-archiver@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Already CTRL", "archive-already-ctrl",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_ARCHIVABLE"));
    }

    @Test
    void archiveArticleNonExistentReturns404() throws Exception {
        AppUser archiver = persistUser("staff-archive-not-found-archiver@primatis.test");

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Archivage — visibilité publique (DEV-11.8 §26/§30)
    // ---------------------------------------------------------------

    @Test
    void archivedArticleIsNoLongerPubliclyVisibleBySlugOrList() throws Exception {
        AppUser author = persistUser("staff-archive-public-author@primatis.test");
        AppUser archiver = persistUser("staff-archive-public-archiver@primatis.test");
        Article article = persistArticleWithStatus(author, "Archive Public Visibility", "archive-public-visibility-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(get("/api/v1/articles/{slug}", article.getSlug()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/staff/articles/{articleId}/archive", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(archiver.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/{slug}", article.getSlug()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/articles").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'archive-public-visibility-ctrl')]").doesNotExist());
    }

    // ---------------------------------------------------------------
    // DELETE /api/v1/staff/articles/{articleId} — sécurité
    // ---------------------------------------------------------------

    @Test
    void deleteArticleWithoutJwtIsUnauthorized() throws Exception {
        AppUser author = persistUser("staff-delete-security-anon@primatis.test");
        Article article = persistDraftArticle(author, "Delete Security Anonymous", "delete-security-anonymous-ctrl");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteArticleWithArticlePublishOnlyIsForbidden() throws Exception {
        AppUser author = persistUser("staff-delete-security-publish@primatis.test");
        Article article = persistDraftArticle(author, "Delete Security Publish", "delete-security-publish-ctrl");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteArticleWithRoleMemberIsForbidden() throws Exception {
        AppUser author = persistUser("staff-delete-security-member@primatis.test");
        Article article = persistDraftArticle(author, "Delete Security Member", "delete-security-member-ctrl");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteArticleWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-delete-librarian-author@primatis.test");
        Article article = persistDraftArticle(author, "Delete Librarian", "delete-librarian-ctrl");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNoContent());
        createdArticleIds.remove(article.getId());
    }

    @Test
    void deleteArticleWithAdminAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-delete-admin-author@primatis.test");
        Article article = persistDraftArticle(author, "Delete Admin", "delete-admin-ctrl");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNoContent());
        createdArticleIds.remove(article.getId());
    }

    // ---------------------------------------------------------------
    // DELETE /api/v1/staff/articles/{articleId} — comportement
    // ---------------------------------------------------------------

    @Test
    void deleteArticleRemovesDraftArticleAndReturns204() throws Exception {
        AppUser author = persistUser("staff-delete-behavior-author@primatis.test");
        Article article = persistDraftArticle(author, "Delete Behavior", "delete-behavior-ctrl");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNoContent());
        createdArticleIds.remove(article.getId());

        transactionTemplate().executeWithoutResult(status ->
                assertThat(articleRepository.findById(article.getId())).isEmpty());
    }

    @Test
    void deleteArticleOnAPublishedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-delete-published-author@primatis.test");
        Article article = persistArticleWithStatus(author, "Delete Published CTRL", "delete-published-ctrl",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_DELETABLE"));
    }

    @Test
    void deleteArticleOnAnArchivedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-delete-archived-author@primatis.test");
        Article article = persistArticleWithStatus(author, "Delete Archived CTRL", "delete-archived-ctrl",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_DELETABLE"));
    }

    @Test
    void deleteArticleNonExistentReturns404() throws Exception {
        AppUser actor = persistUser("staff-delete-not-found-actor@primatis.test");

        mockMvc.perform(delete("/api/v1/staff/articles/{articleId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(actor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/staff/articles/{articleId}/tags — sécurité
    // ---------------------------------------------------------------

    @Test
    void associateTagsWithoutJwtIsUnauthorized() throws Exception {
        AppUser author = persistUser("staff-associate-security-anon@primatis.test");
        Article article = persistDraftArticle(author, "Associate Security Anonymous", "associate-security-anonymous-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void associateTagsWithArticlePublishOnlyIsForbidden() throws Exception {
        AppUser author = persistUser("staff-associate-security-publish@primatis.test");
        Article article = persistDraftArticle(author, "Associate Security Publish", "associate-security-publish-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(author.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void associateTagsWithRoleMemberIsForbidden() throws Exception {
        AppUser author = persistUser("staff-associate-security-member@primatis.test");
        Article article = persistDraftArticle(author, "Associate Security Member", "associate-security-member-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer " + signToken(1L, List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void associateTagsWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-associate-librarian-author@primatis.test");
        AppUser editor = persistUser("staff-associate-librarian-editor@primatis.test");
        Article article = persistDraftArticle(author, "Associate Librarian", "associate-librarian-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void associateTagsWithAdminAndArticleManageIsAuthorized() throws Exception {
        AppUser author = persistUser("staff-associate-admin-author@primatis.test");
        AppUser editor = persistUser("staff-associate-admin-editor@primatis.test");
        Article article = persistDraftArticle(author, "Associate Admin", "associate-admin-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/staff/articles/{articleId}/tags — comportement
    // ---------------------------------------------------------------

    @Test
    void associateTagsWithMissingTagIdsFieldReturns400() throws Exception {
        AppUser author = persistUser("staff-associate-missing-author@primatis.test");
        AppUser editor = persistUser("staff-associate-missing-editor@primatis.test");
        Article article = persistDraftArticle(author, "Associate Missing", "associate-missing-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void associateTagsResponseReflectsAssociatedTags() throws Exception {
        AppUser author = persistUser("staff-associate-body-author@primatis.test");
        AppUser editor = persistUser("staff-associate-body-editor@primatis.test");
        Article article = persistDraftArticle(author, "Associate Body", "associate-body-ctrl");
        Tag tag = persistTag("associate-body-tag-ctrl", "Tag Body CTRL");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[" + tag.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].id").value(tag.getId()))
                .andExpect(jsonPath("$.tags[0].code").value("associate-body-tag-ctrl"))
                .andExpect(jsonPath("$.lastModifiedBy.id").value(editor.getId()));
    }

    @Test
    void associateTagsOnArchivedArticleReturns409() throws Exception {
        AppUser author = persistUser("staff-associate-archived-author@primatis.test");
        AppUser editor = persistUser("staff-associate-archived-editor@primatis.test");
        Article article = persistArticleWithStatus(author, "Associate Archived CTRL", "associate-archived-ctrl",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_EDITABLE"));
    }

    @Test
    void associateTagsWithUnknownTagIdReturns404() throws Exception {
        AppUser author = persistUser("staff-associate-unknown-author@primatis.test");
        AppUser editor = persistUser("staff-associate-unknown-editor@primatis.test");
        Article article = persistDraftArticle(author, "Associate Unknown CTRL", "associate-unknown-ctrl");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", article.getId())
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[999999999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }

    @Test
    void associateTagsNonExistentArticleReturns404() throws Exception {
        AppUser editor = persistUser("staff-associate-not-found-editor@primatis.test");

        mockMvc.perform(patch("/api/v1/staff/articles/{articleId}/tags", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(editor.getId(), List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private String signToken(Long userId, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String createArticleJson(String title, String content) {
        return "{\"title\":\"" + title + "\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
    }

    private AppUser persistUser(String email) {
        AppUser[] holder = new AppUser[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            holder[0] = user;
        });
        createdUserIds.add(holder[0].getId());
        return holder[0];
    }

    private Article persistDraftArticle(AppUser author, String title, String slug) {
        return persistArticleWithStatus(author, title, slug, ArticleStatus.DRAFT, null);
    }

    private Article persistArticleWithStatus(
            AppUser author, String title, String slug, ArticleStatus status, Instant publishedAt) {
        Article[] holder = new Article[1];
        transactionTemplate().executeWithoutResult(status2 -> {
            Article article = new Article();
            article.setAuthorUser(entityManager.getReference(AppUser.class, author.getId()));
            article.setTitle(title);
            article.setContent("Contenu de test");
            article.setSlug(slug);
            article.setArticleStatus(status);
            article.setPublishedAt(publishedAt);
            article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
            article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
            articleRepository.save(article);
            holder[0] = article;
        });
        createdArticleIds.add(holder[0].getId());
        return holder[0];
    }

    private Tag persistTag(String code, String label) {
        Tag[] holder = new Tag[1];
        transactionTemplate().executeWithoutResult(status -> {
            Tag tag = new Tag();
            tag.setCode(code);
            tag.setLabel(label);
            tagRepository.save(tag);
            holder[0] = tag;
        });
        createdTagIds.add(holder[0].getId());
        return holder[0];
    }

    private void trackCreatedArticleBySlug(String slug) {
        articleRepository.findBySlug(slug).ifPresent(article -> createdArticleIds.add(article.getId()));
    }
}
