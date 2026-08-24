package be.primatis.article.web;

import be.primatis.article.Article;
import be.primatis.article.ArticleRepository;
import be.primatis.article.ArticleStatus;
import be.primatis.article.ArticleTag;
import be.primatis.article.ArticleTagId;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat REST public d'Article (DEV-11.5) : {@code GET /api/v1/articles}
 * et {@code GET /api/v1/articles/{slug}}. Même précédent structurel exact
 * que {@code catalogue.web.TitleControllerTests} (DEV-06.4) : JWT signés
 * manuellement pour les scénarios anonymous/rôles, aucune permission
 * n'étant requise ici.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TagRepository tagRepository;

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
                entityManager.createQuery("DELETE FROM ArticleTag at WHERE at.id.articleId = :articleId")
                        .setParameter("articleId", articleId).executeUpdate();
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
        createdTagIds.clear();
        createdUserIds.clear();
    }

    // ---------------------------------------------------------------
    // GET /api/v1/articles — sécurité (permitAll)
    // ---------------------------------------------------------------

    @Test
    void listArticlesWithoutJwtReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/articles")).andExpect(status().isOk());
    }

    @Test
    void listArticlesWithRoleMemberReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/articles").header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/articles — structure/pagination
    // ---------------------------------------------------------------

    @Test
    void listArticlesReturnsDefaultPagedResponseStructure() throws Exception {
        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listArticlesWithNegativePageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void listArticlesWithZeroSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("size", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void listArticlesWithSizeAbove100Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void listArticlesNeverIncludesDraftOrArchivedArticles() throws Exception {
        AppUser author = persistUser("controller-list-exclusion@primatis.test");
        persistArticleWithSlug(author, "Controller Draft Excluded", "controller-draft-excluded", ArticleStatus.DRAFT, null);
        persistArticleWithSlug(author, "Controller Archived Excluded", "controller-archived-excluded",
                ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        Article published = persistArticleWithSlug(author, "Controller Published Included",
                "controller-published-included", ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        mockMvc.perform(get("/api/v1/articles").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + published.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == 'controller-draft-excluded')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.slug == 'controller-archived-excluded')]").doesNotExist());
    }

    @Test
    void listArticlesContentItemsExposeOnlyCompactSummaryFields() throws Exception {
        AppUser author = persistUser("controller-list-anti-leak@primatis.test");
        persistArticleWithSlug(author, "Controller Anti-leak Summary", "controller-anti-leak-summary",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        mockMvc.perform(get("/api/v1/articles").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'controller-anti-leak-summary')].content").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.slug == 'controller-anti-leak-summary')].tags").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.slug == 'controller-anti-leak-summary')].lastModifiedBy")
                        .doesNotExist())
                .andExpect(jsonPath("$.content[?(@.slug == 'controller-anti-leak-summary')].articleStatus")
                        .doesNotExist());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/articles/{slug} — sécurité (permitAll)
    // ---------------------------------------------------------------

    @Test
    void getArticleBySlugWithoutJwtReturnsOkForPublishedArticle() throws Exception {
        AppUser author = persistUser("controller-detail-anonymous@primatis.test");
        Article article = persistArticleWithSlug(author, "Controller Detail Anonymous", "controller-detail-anonymous",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        mockMvc.perform(get("/api/v1/articles/{slug}", article.getSlug())).andExpect(status().isOk());
    }

    @Test
    void getArticleBySlugWithRoleAdminReturnsOk() throws Exception {
        AppUser author = persistUser("controller-detail-admin@primatis.test");
        Article article = persistArticleWithSlug(author, "Controller Detail Admin", "controller-detail-admin",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        mockMvc.perform(get("/api/v1/articles/{slug}", article.getSlug())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/articles/{slug} — comportement
    // ---------------------------------------------------------------

    @Test
    void getArticleBySlugPublishedReturnsDetailWithTags() throws Exception {
        AppUser author = persistUser("controller-detail-published@primatis.test");
        Article article = persistArticleWithSlug(author, "Controller Detail Published", "controller-detail-published",
                ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        Tag tag = persistTag("CTRL-DETAIL-TAG", "Controller Detail Tag");
        linkArticleTag(article, tag);

        mockMvc.perform(get("/api/v1/articles/{slug}", article.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(article.getId()))
                .andExpect(jsonPath("$.title").value("Controller Detail Published"))
                .andExpect(jsonPath("$.articleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.tags[0].code").value("CTRL-DETAIL-TAG"))
                .andExpect(jsonPath("$.author.id").value(author.getId()));
    }

    @Test
    void getArticleBySlugNonExistentReturns404WithArticleNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/v1/articles/{slug}", "does-not-exist-controller-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    @Test
    void getArticleBySlugDraftReturns404ForAnonymous() throws Exception {
        AppUser author = persistUser("controller-detail-draft-anonymous@primatis.test");
        Article draft = persistArticleWithSlug(author, "Controller Detail Draft Anonymous",
                "controller-detail-draft-anonymous", ArticleStatus.DRAFT, null);

        mockMvc.perform(get("/api/v1/articles/{slug}", draft.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    @Test
    void getArticleBySlugDraftReturns404RegardlessOfAuthenticatedIdentity() throws Exception {
        AppUser author = persistUser("controller-detail-draft-admin@primatis.test");
        Article draft = persistArticleWithSlug(author, "Controller Detail Draft Admin",
                "controller-detail-draft-admin", ArticleStatus.DRAFT, null);

        mockMvc.perform(get("/api/v1/articles/{slug}", draft.getSlug())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    @Test
    void getArticleBySlugArchivedReturns404() throws Exception {
        AppUser author = persistUser("controller-detail-archived@primatis.test");
        Article archived = persistArticleWithSlug(author, "Controller Detail Archived",
                "controller-detail-archived", ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        mockMvc.perform(get("/api/v1/articles/{slug}", archived.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private String signToken(List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", List.of())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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

    private Article persistArticleWithSlug(
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

    private void linkArticleTag(Article article, Tag tag) {
        transactionTemplate().executeWithoutResult(status -> {
            ArticleTag articleTag = new ArticleTag();
            articleTag.setId(new ArticleTagId(article.getId(), tag.getId()));
            articleTag.setArticle(entityManager.getReference(Article.class, article.getId()));
            articleTag.setTag(entityManager.getReference(Tag.class, tag.getId()));
            entityManager.persist(articleTag);
        });
    }
}
