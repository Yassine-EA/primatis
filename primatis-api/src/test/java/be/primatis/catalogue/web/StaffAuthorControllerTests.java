package be.primatis.catalogue.web;

import be.primatis.catalogue.Author;
import be.primatis.catalogue.AuthorRepository;
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
 * Contrat REST staff des {@code Author} (DEV-06.5.1) : {@code GET}/{@code
 * POST}/{@code PATCH /api/v1/staff/authors(/{authorId})}. Même stratégie de
 * JWT signés manuellement que {@code StaffTitleControllerTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAuthorControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdAuthorIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtureAuthors() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long authorId : createdAuthorIds) {
                authorRepository.deleteById(authorId);
            }
        });
        createdAuthorIds.clear();
    }

    // ---------------------------------------------------------------
    // Sécurité — GET /api/v1/staff/authors
    // ---------------------------------------------------------------

    @Test
    void listAuthorsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/authors")).andExpect(status().isUnauthorized());
    }

    @Test
    void listAuthorsWithoutCatalogueManageIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/authors")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listAuthorsWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void listAuthorsWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void listAuthorsWithQueryFiltersResults() throws Exception {
        Author matching = persistAuthor("Controller Author Query Match CRT");
        persistAuthor("Controller Author Query Other CRT Entirely");

        mockMvc.perform(get("/api/v1/staff/authors")
                        .param("q", "author query match crt")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------------------------------------------------------------
    // Sécurité — POST /api/v1/staff/authors
    // ---------------------------------------------------------------

    @Test
    void createAuthorWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Create Security Anonymous CRT"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAuthorWithoutCatalogueManageIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/staff/authors")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Create Security Member CRT"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAuthorWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Create Security Librarian CRT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        trackCreatedAuthorByFullName("Create Security Librarian CRT");
    }

    @Test
    void createAuthorWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Create Security Admin CRT"}
                                """))
                .andExpect(status().isCreated());

        trackCreatedAuthorByFullName("Create Security Admin CRT");
    }

    @Test
    void createAuthorWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createAuthorAllowsHomonym() throws Exception {
        persistAuthor("Create Homonym Controller CRT");

        mockMvc.perform(post("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Create Homonym Controller CRT"}
                                """))
                .andExpect(status().isCreated());

        trackCreatedAuthorByFullName("Create Homonym Controller CRT");
    }

    @Test
    void createAuthorWithIncoherentDatesReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/staff/authors")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Create Incoherent Dates CRT","birthDate":"2000-01-01","deathDate":"1999-01-01"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTHOR_BIRTH_DATE_AFTER_DEATH_DATE"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH /api/v1/staff/authors/{authorId}
    // ---------------------------------------------------------------

    @Test
    void updateAuthorWithoutJwtIsUnauthorized() throws Exception {
        Author author = persistAuthor("Update Security Anonymous CRT");

        mockMvc.perform(patch("/api/v1/staff/authors/{authorId}", author.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAuthorWithoutCatalogueManageIsForbidden() throws Exception {
        Author author = persistAuthor("Update Security Member CRT");

        mockMvc.perform(patch("/api/v1/staff/authors/{authorId}", author.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAuthorWithCatalogueManageIsAuthorized() throws Exception {
        Author author = persistAuthor("Update Security Authorized CRT");

        mockMvc.perform(patch("/api/v1/staff/authors/{authorId}", author.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nationality":"Espagnole"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationality").value("Espagnole"));
    }

    @Test
    void updateAuthorForNonExistentAuthorReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/staff/authors/{authorId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTHOR_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private void trackCreatedAuthorByFullName(String fullName) {
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createQuery("SELECT a.id FROM Author a WHERE a.fullName = :fullName", Long.class)
                        .setParameter("fullName", fullName)
                        .getResultStream()
                        .forEach(createdAuthorIds::add));
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

    private Author persistAuthor(String fullName) {
        Author[] holder = new Author[1];
        transactionTemplate().executeWithoutResult(status -> {
            Author author = new Author();
            author.setFullName(fullName);
            authorRepository.save(author);
            holder[0] = author;
        });
        createdAuthorIds.add(holder[0].getId());
        return holder[0];
    }
}
