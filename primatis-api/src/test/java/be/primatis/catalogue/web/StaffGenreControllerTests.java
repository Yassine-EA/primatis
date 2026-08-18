package be.primatis.catalogue.web;

import be.primatis.catalogue.Genre;
import be.primatis.catalogue.GenreRepository;
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
 * Contrat REST staff des {@code Genre} (DEV-06.5.1) : {@code GET}/{@code
 * POST}/{@code PATCH /api/v1/staff/genres(/{genreId})}. Même stratégie de
 * JWT signés manuellement que {@code StaffTitleControllerTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffGenreControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdGenreIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtureGenres() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long genreId : createdGenreIds) {
                genreRepository.deleteById(genreId);
            }
        });
        createdGenreIds.clear();
    }

    // ---------------------------------------------------------------
    // Sécurité — GET /api/v1/staff/genres
    // ---------------------------------------------------------------

    @Test
    void listGenresWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/genres")).andExpect(status().isUnauthorized());
    }

    @Test
    void listGenresWithoutCatalogueManageIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/staff/genres")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listGenresWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void listGenresWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Sécurité — POST /api/v1/staff/genres
    // ---------------------------------------------------------------

    @Test
    void createGenreWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/genres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CREATE-SEC-ANON-CRT","label":"Create Security Anonymous CRT"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createGenreWithoutCatalogueManageIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/staff/genres")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CREATE-SEC-MEMBER-CRT","label":"Create Security Member CRT"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createGenreWithLibrarianAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_LIBRARIAN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CREATE-SEC-LIBRARIAN-CRT","label":"Create Security Librarian CRT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        createdGenreIds.addAll(genreIdByCode("CREATE-SEC-LIBRARIAN-CRT"));
    }

    @Test
    void createGenreWithAdminAndCatalogueManageIsAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CREATE-SEC-ADMIN-CRT","label":"Create Security Admin CRT"}
                                """))
                .andExpect(status().isCreated());

        createdGenreIds.addAll(genreIdByCode("CREATE-SEC-ADMIN-CRT"));
    }

    @Test
    void createGenreWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createGenreWithDuplicateCodeReturnsConflict() throws Exception {
        persistGenre("CREATE-DUP-CODE-CRT", "Create Dup Code Original CRT");

        mockMvc.perform(post("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CREATE-DUP-CODE-CRT","label":"Create Dup Code New CRT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENRE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createGenreWithDuplicateLabelReturnsConflict() throws Exception {
        persistGenre("CREATE-DUP-LABEL-ORIGINAL-CRT", "Create Dup Label CRT");

        mockMvc.perform(post("/api/v1/staff/genres")
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CREATE-DUP-LABEL-NEW-CRT","label":"Create Dup Label CRT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENRE_LABEL_ALREADY_EXISTS"));
    }

    // ---------------------------------------------------------------
    // Sécurité — PATCH /api/v1/staff/genres/{genreId}
    // ---------------------------------------------------------------

    @Test
    void updateGenreWithoutJwtIsUnauthorized() throws Exception {
        Genre genre = persistGenre("UPDATE-SEC-ANON-CRT", "Update Security Anonymous CRT");

        mockMvc.perform(patch("/api/v1/staff/genres/{genreId}", genre.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateGenreWithoutCatalogueManageIsForbidden() throws Exception {
        Genre genre = persistGenre("UPDATE-SEC-MEMBER-CRT", "Update Security Member CRT");

        mockMvc.perform(patch("/api/v1/staff/genres/{genreId}", genre.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGenreWithCatalogueManageIsAuthorized() throws Exception {
        Genre genre = persistGenre("UPDATE-SEC-AUTH-CRT", "Update Security Authorized CRT");

        mockMvc.perform(patch("/api/v1/staff/genres/{genreId}", genre.getId())
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Updated description CRT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description CRT"));
    }

    @Test
    void updateGenreForNonExistentGenreReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/staff/genres/{genreId}", 999999999L)
                        .header("Authorization", "Bearer "
                                + signToken(List.of("ROLE_ADMIN"), List.of("CATALOGUE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENRE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private List<Long> genreIdByCode(String code) {
        List<Long> result = new ArrayList<>();
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createQuery("SELECT g.id FROM Genre g WHERE g.code = :code", Long.class)
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

    private Genre persistGenre(String code, String label) {
        Genre[] holder = new Genre[1];
        transactionTemplate().executeWithoutResult(status -> {
            Genre genre = new Genre();
            genre.setCode(code);
            genre.setLabel(label);
            genreRepository.save(genre);
            holder[0] = genre;
        });
        createdGenreIds.add(holder[0].getId());
        return holder[0];
    }
}
