package be.primatis.article.web;

import be.primatis.article.Article;
import be.primatis.article.ArticleRepository;
import be.primatis.article.ArticleStatus;
import be.primatis.article.ArticleTag;
import be.primatis.article.ArticleTagId;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat REST staff des {@code Tag} (DEV-11.9, {@code ARTICLE_MANAGE}) :
 * {@code GET}/{@code POST}/{@code PATCH}/{@code DELETE
 * /api/v1/staff/tags(/{tagId})}. Même stratégie de JWT signés manuellement
 * que {@code StaffGenreControllerTests}. Contrairement au précédent Genre,
 * couvre également {@code DELETE} — voir {@code TagService} pour la
 * justification de cette divergence délibérée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffTagControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleTagRepository articleTagRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdTagIds = new ArrayList<>();
    private final List<Long> createdArticleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

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
        createdTagIds.clear();
        createdArticleIds.clear();
        createdUserIds.clear();
    }

    // ---------------------------------------------------------------
    // Sécurité — GET /api/v1/staff/tags
    // ---------------------------------------------------------------

    @Test
    void listTagsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    void listTagsWithArticlePublishOnlyIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/tags")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_PUBLISH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTagsWithoutArticleManageIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/tags")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTagsWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void listTagsWithAdminAndArticleManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Sécurité — POST /api/v1/staff/tags
    // ---------------------------------------------------------------

    @Test
    void createTagWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"create-sec-anon-crt","label":"Create Security Anonymous CRT"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTagWithoutArticleManageIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/staff/tags")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"create-sec-member-crt","label":"Create Security Member CRT"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTagWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"create-sec-librarian-crt","label":"Create Security Librarian CRT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        createdTagIds.addAll(tagIdByCode("create-sec-librarian-crt"));
    }

    @Test
    void createTagWithAdminAndArticleManageIsAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"create-sec-admin-crt","label":"Create Security Admin CRT"}
                                """))
                .andExpect(status().isCreated());

        createdTagIds.addAll(tagIdByCode("create-sec-admin-crt"));
    }

    @Test
    void createTagWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createTagWithDuplicateCodeReturnsConflict() throws Exception {
        persistTag("create-dup-code-crt", "Create Dup Code Original CRT");

        mockMvc.perform(post("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"create-dup-code-crt","label":"Create Dup Code New CRT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createTagAllowsDuplicateLabel() throws Exception {
        persistTag("create-dup-label-original-crt", "Create Dup Label CRT");

        mockMvc.perform(post("/api/v1/staff/tags")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"create-dup-label-new-crt","label":"Create Dup Label CRT"}
                                """))
                .andExpect(status().isCreated());

        createdTagIds.addAll(tagIdByCode("create-dup-label-new-crt"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH /api/v1/staff/tags/{tagId}
    // ---------------------------------------------------------------

    @Test
    void updateTagWithoutJwtIsUnauthorized() throws Exception {
        Tag tag = persistTag("update-sec-anon-crt", "Update Security Anonymous CRT");

        mockMvc.perform(patch("/api/v1/staff/tags/{tagId}", tag.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateTagWithoutArticleManageIsForbidden() throws Exception {
        Tag tag = persistTag("update-sec-member-crt", "Update Security Member CRT");

        mockMvc.perform(patch("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTagWithArticleManageIsAuthorized() throws Exception {
        Tag tag = persistTag("update-sec-auth-crt", "Update Security Authorized CRT");

        mockMvc.perform(patch("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Updated description CRT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description CRT"))
                .andExpect(jsonPath("$.code").value("update-sec-auth-crt"));
    }

    @Test
    void updateTagForNonExistentTagReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/staff/tags/{tagId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }

    @Test
    void updateTagRejectsBlankLabel() throws Exception {
        Tag tag = persistTag("update-blank-label-crt", "Update Blank Label CRT");

        mockMvc.perform(patch("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"   "}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_LABEL_MUST_NOT_BE_BLANK"));
    }

    // ---------------------------------------------------------------
    // Sécurité — DELETE /api/v1/staff/tags/{tagId}
    // ---------------------------------------------------------------

    @Test
    void deleteTagWithoutJwtIsUnauthorized() throws Exception {
        Tag tag = persistTag("delete-sec-anon-crt", "Delete Security Anonymous CRT");

        mockMvc.perform(delete("/api/v1/staff/tags/{tagId}", tag.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteTagWithoutArticleManageIsForbidden() throws Exception {
        Tag tag = persistTag("delete-sec-member-crt", "Delete Security Member CRT");

        mockMvc.perform(delete("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTagWithLibrarianAndArticleManageIsAuthorized() throws Exception {
        Tag tag = persistTag("delete-sec-librarian-crt", "Delete Security Librarian CRT");

        mockMvc.perform(delete("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNoContent());
        createdTagIds.remove(tag.getId());
    }

    @Test
    void deleteTagWithAdminAndArticleManageIsAuthorized() throws Exception {
        Tag tag = persistTag("delete-sec-admin-crt", "Delete Security Admin CRT");

        mockMvc.perform(delete("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isNoContent());
        createdTagIds.remove(tag.getId());
    }

    // ---------------------------------------------------------------
    // DELETE /api/v1/staff/tags/{tagId} — comportement
    // ---------------------------------------------------------------

    @Test
    void deleteTagForNonExistentTagReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/staff/tags/{tagId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }

    /**
     * Preuve de persistance réelle inter-requêtes (mission DEV-11.9 §36) :
     * chaque requête {@code MockMvc} s'exécute dans sa propre transaction
     * (commit réel), contrairement à un test {@code @Transactional} de
     * classe qui partagerait une transaction unique — c'est ici l'endroit
     * approprié pour vérifier qu'un Tag rejeté à la suppression reste
     * réellement présent en base après le rejet (voir {@code
     * TagServiceTests.deleteTagInUseIsRejected}, dont la vérification
     * équivalente serait invalide dans une transaction déjà marquée en
     * échec).
     */
    @Test
    void deleteTagInUseReturns409AndTagStillExistsAfterwards() throws Exception {
        AppUser author = persistUser("staff-delete-tag-in-use-author@primatis.test");
        Tag tag = persistTag("delete-in-use-crt", "Delete In Use CRT");
        Article article = persistDraftArticleWithTag(author, "Delete Tag In Use CTRL", "delete-tag-in-use-ctrl", tag);

        mockMvc.perform(delete("/api/v1/staff/tags/{tagId}", tag.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_IN_USE"));

        mockMvc.perform(get("/api/v1/staff/tags").param("size", "100")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("ARTICLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code == 'delete-in-use-crt')]").exists());
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private List<Long> tagIdByCode(String code) {
        List<Long> result = new ArrayList<>();
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createQuery("SELECT t.id FROM Tag t WHERE t.code = :code", Long.class)
                        .setParameter("code", code)
                        .getResultStream()
                        .forEach(result::add));
        return result;
    }

    private String signToken(List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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

    private Article persistDraftArticleWithTag(AppUser author, String title, String slug, Tag tag) {
        Article[] holder = new Article[1];
        transactionTemplate().executeWithoutResult(status -> {
            Article article = new Article();
            article.setAuthorUser(entityManager.getReference(AppUser.class, author.getId()));
            article.setTitle(title);
            article.setContent("Contenu de test");
            article.setSlug(slug);
            article.setArticleStatus(ArticleStatus.DRAFT);
            article.setPublishedAt(null);
            article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
            article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
            articleRepository.save(article);

            ArticleTag articleTag = new ArticleTag();
            articleTag.setId(new ArticleTagId(article.getId(), tag.getId()));
            articleTag.setArticle(entityManager.getReference(Article.class, article.getId()));
            articleTag.setTag(entityManager.getReference(Tag.class, tag.getId()));
            entityManager.persist(articleTag);

            holder[0] = article;
        });
        createdArticleIds.add(holder[0].getId());
        return holder[0];
    }
}
