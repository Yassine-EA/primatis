package be.primatis.catalogue.web;

import be.primatis.catalogue.Author;
import be.primatis.catalogue.AuthorRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.catalogue.TitleStatus;
import be.primatis.config.JwtProperties;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat REST staff du catalogue (DEV-06.5) : {@code GET}/{@code POST}/
 * {@code PATCH /api/v1/staff/titles(/{titleId}(/status))}. JWT signés
 * manuellement (même principe que {@code TitleControllerTests}/
 * {@code UserControllerTests}) : {@code CATALOGUE_MANAGE} étant une simple
 * authorité vérifiée par {@code @PreAuthorize}, un token portant cette
 * permission suffit à prouver le comportement de l'endpoint — le bootstrap
 * réel {@code CATALOGUE_MANAGE → ROLE_LIBRARIAN/ROLE_ADMIN} (V002) est déjà
 * couvert par {@code RbacBootstrapTests}, non re-testé ici.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffTitleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdTitleIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtureTitles() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long titleId : createdTitleIds) {
                entityManager.createQuery("DELETE FROM TitleAuthor ta WHERE ta.id.titleId = :titleId")
                        .setParameter("titleId", titleId).executeUpdate();
                entityManager.createQuery("DELETE FROM TitleGenre tg WHERE tg.id.titleId = :titleId")
                        .setParameter("titleId", titleId).executeUpdate();
                titleRepository.deleteById(titleId);
            }
        });
        createdTitleIds.clear();
    }

    // ---------------------------------------------------------------
    // Sécurité — GET /api/v1/staff/titles
    // ---------------------------------------------------------------

    @Test
    void listTitlesWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/titles")).andExpect(status().isUnauthorized());
    }

    @Test
    void listTitlesWithoutCatalogueManageIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/titles")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listTitlesWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/titles")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void listTitlesWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/titles")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Sécurité — GET /api/v1/staff/titles/{titleId}
    // ---------------------------------------------------------------

    @Test
    void getTitleByIdWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistActiveTitle("Staff Detail Security Anonymous CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}", title.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void getTitleByIdWithoutCatalogueManageIsForbidden() throws Exception {
        Title title = persistActiveTitle("Staff Detail Security Member CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTitleByIdWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        Title title = persistActiveTitle("Staff Detail Security Librarian CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void getTitleByIdWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        Title title = persistActiveTitle("Staff Detail Security Admin CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void getTitleByIdReturnsWithdrawnTitleForStaff() throws Exception {
        Title withdrawn = persistWithdrawnTitle("Staff Detail Shows Withdrawn CRT");

        mockMvc.perform(get("/api/v1/staff/titles/{titleId}", withdrawn.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleStatus").value("WITHDRAWN"));
    }

    @Test
    void listTitlesIncludesWithdrawnByDefaultForStaff() throws Exception {
        Title withdrawn = persistWithdrawnTitle("Staff List Includes Withdrawn CRT");

        mockMvc.perform(get("/api/v1/staff/titles")
                        .param("q", "list includes withdrawn crt")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(withdrawn.getId()));
    }

    // ---------------------------------------------------------------
    // Sécurité — POST /api/v1/staff/titles
    // ---------------------------------------------------------------

    @Test
    void createTitleWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/titles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTitleJson("Create Security Anonymous CRT", List.of())))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Le body doit être structurellement VALIDE (Bean Validation) : {@code
     * authorIds} vide échouerait {@code @NotEmpty} et produirait 400 avant
     * même d'atteindre {@code @PreAuthorize} sur {@code CatalogueService}
     * (validation Spring MVC en amont de l'appel Service) — masquant le
     * vrai contrat 403 visé par ce test. L'identifiant fourni n'a pas besoin
     * de référencer un Author réel : {@code @PreAuthorize} intercepte avant
     * toute exécution du corps de la méthode, {@code resolveAuthors} n'est
     * jamais atteint. Gate PostgreSQL réel #2 : fixture corrigée ici, aucune
     * modification de {@code @PreAuthorize}/{@code GlobalExceptionHandler}.
     */
    @Test
    void createTitleWithoutCatalogueManageIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/staff/titles")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTitleJson("Create Security Member CRT", List.of(1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTitleWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        Author author = persistAuthor("Create Security Librarian Author CRT");

        mockMvc.perform(post("/api/v1/staff/titles")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTitleJson("Create Security Librarian CRT", List.of(author.getId()))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        trackCreatedTitleByTitle("Create Security Librarian CRT");
    }

    @Test
    void createTitleWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        Author author = persistAuthor("Create Security Admin Author CRT");

        mockMvc.perform(post("/api/v1/staff/titles")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTitleJson("Create Security Admin CRT", List.of(author.getId()))))
                .andExpect(status().isCreated());

        trackCreatedTitleByTitle("Create Security Admin CRT");
    }

    @Test
    void createTitleWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/staff/titles")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createTitleWithDuplicateIsbnReturnsConflict() throws Exception {
        Author author = persistAuthor("Create Isbn Conflict Author CRT");
        Title existing = persistActiveTitle("Create Isbn Conflict Existing CRT");
        transactionTemplate().executeWithoutResult(status ->
                titleRepository.findById(existing.getId()).ifPresent(t -> t.setIsbn("9780000009CRT")));

        String json = """
                {"title":"Create Isbn Conflict New CRT","language":"EN","authorIds":[%d],"isbn":"9780000009CRT"}
                """.formatted(author.getId());

        mockMvc.perform(post("/api/v1/staff/titles")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ISBN_ALREADY_EXISTS"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH /api/v1/staff/titles/{titleId}
    // ---------------------------------------------------------------

    @Test
    void updateTitleWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistActiveTitle("Update Security Anonymous CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}", title.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateTitleWithoutCatalogueManageIsForbidden() throws Exception {
        Title title = persistActiveTitle("Update Security Member CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTitleWithCatalogueManageIsAuthorized() throws Exception {
        Title title = persistActiveTitle("Update Security Authorized CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publisher":"Updated Publisher CRT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher").value("Updated Publisher CRT"));
    }

    @Test
    void updateTitleForNonExistentTitleReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TITLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH /api/v1/staff/titles/{titleId}/status
    // ---------------------------------------------------------------

    @Test
    void updateStatusWithoutJwtIsUnauthorized() throws Exception {
        Title title = persistActiveTitle("Status Security Anonymous CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/status", title.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"WITHDRAWN"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStatusWithoutCatalogueManageIsForbidden() throws Exception {
        Title title = persistActiveTitle("Status Security Member CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/status", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"WITHDRAWN"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusWithCatalogueManageIsAuthorized() throws Exception {
        Title title = persistActiveTitle("Status Security Authorized CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/status", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"WITHDRAWN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleStatus").value("WITHDRAWN"));
    }

    @Test
    void updateStatusWithMissingStatusReturns400() throws Exception {
        Title title = persistActiveTitle("Status Missing Field CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/status", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateStatusWithUnknownEnumValueReturns400() throws Exception {
        Title title = persistActiveTitle("Status Bogus Enum CRT");

        mockMvc.perform(patch("/api/v1/staff/titles/{titleId}/status", title.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BOGUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private String createTitleJson(String title, List<Long> authorIds) {
        String authorIdsJson = authorIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return """
                {"title":"%s","language":"EN","authorIds":[%s]}
                """.formatted(title, authorIdsJson);
    }

    private void trackCreatedTitleByTitle(String titleText) {
        transactionTemplate().executeWithoutResult(status -> {
            entityManager.createQuery("SELECT t.id FROM Title t WHERE t.title = :title", Long.class)
                    .setParameter("title", titleText)
                    .getResultStream()
                    .forEach(createdTitleIds::add);
        });
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

    private Title persistActiveTitle(String title) {
        return persistTitleWithStatus(title, TitleStatus.ACTIVE);
    }

    private Title persistWithdrawnTitle(String title) {
        return persistTitleWithStatus(title, TitleStatus.WITHDRAWN);
    }

    private Title persistTitleWithStatus(String title, TitleStatus titleStatus) {
        Title[] holder = new Title[1];
        transactionTemplate().executeWithoutResult(status -> {
            Title entity = new Title();
            entity.setTitle(title);
            entity.setLanguage(Language.EN);
            entity.setTitleStatus(titleStatus);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            titleRepository.save(entity);
            holder[0] = entity;
        });
        createdTitleIds.add(holder[0].getId());
        return holder[0];
    }

    private Author persistAuthor(String fullName) {
        Author[] holder = new Author[1];
        transactionTemplate().executeWithoutResult(status -> {
            Author author = new Author();
            author.setFullName(fullName);
            authorRepository.save(author);
            holder[0] = author;
        });
        return holder[0];
    }
}
