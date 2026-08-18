package be.primatis.catalogue.web;

import be.primatis.catalogue.Author;
import be.primatis.catalogue.AuthorRepository;
import be.primatis.catalogue.Genre;
import be.primatis.catalogue.GenreRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleAuthor;
import be.primatis.catalogue.TitleAuthorId;
import be.primatis.catalogue.TitleGenre;
import be.primatis.catalogue.TitleGenreId;
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
 * Contrat REST public du catalogue (DEV-06.4) : {@code GET /api/v1/titles}
 * et {@code GET /api/v1/titles/{titleId}}. JWT signés manuellement (même
 * principe que {@code SecurityFilterChainTests}/{@code UserControllerTests})
 * pour les scénarios anonymous/rôles — aucune permission n'étant requise ici
 * (K.2, résolu DEV-06.4), un login réel bootstrapé n'est pas nécessaire :
 * seule la présence d'un {@code roles} claim quelconque est vérifiée comme
 * n'ayant aucun effet sur la surface exposée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TitleControllerTests {

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
    private GenreRepository genreRepository;

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
    // GET /api/v1/titles — sécurité (permitAll)
    // ---------------------------------------------------------------

    @Test
    void listTitlesWithoutJwtReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/titles")).andExpect(status().isOk());
    }

    @Test
    void listTitlesWithRoleMemberReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/titles").header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"))))
                .andExpect(status().isOk());
    }

    @Test
    void listTitlesWithRoleLibrarianReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/titles")
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_LIBRARIAN"))))
                .andExpect(status().isOk());
    }

    @Test
    void listTitlesWithRoleAdminReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/titles").header("Authorization", "Bearer " + signToken(List.of("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/titles — structure/pagination
    // ---------------------------------------------------------------

    @Test
    void listTitlesReturnsDefaultPagedResponseStructure() throws Exception {
        mockMvc.perform(get("/api/v1/titles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listTitlesWithNegativePageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/titles").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTitlesWithZeroSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/titles").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTitlesWithSizeAbove100Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/titles").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTitlesWithInvalidLanguageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/titles").param("language", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void listTitlesWithQTransmitsFilterToOnlyMatchingTitle() throws Exception {
        Title matching = persistActiveTitle("Controller Q Filter Match CRT");
        persistActiveTitle("Controller Q Filter Non-match CRT Other");

        mockMvc.perform(get("/api/v1/titles").param("q", "q filter match crt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listTitlesWithAuthorIdTransmitsFilterToOnlyMatchingTitle() throws Exception {
        Author author = persistAuthor("Controller Filter Author CRT");
        Title matching = persistActiveTitle("Controller Author Filter Work CRT");
        linkAuthor(matching, author);

        mockMvc.perform(get("/api/v1/titles").param("authorId", String.valueOf(author.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listTitlesWithGenreCodeTransmitsFilterToOnlyMatchingTitle() throws Exception {
        Genre genre = persistGenre("CONTROLLER-FILTER-GENRE-CRT", "Controller Filter Genre CRT");
        Title matching = persistActiveTitle("Controller Genre Filter Work CRT");
        linkGenre(matching, genre);

        mockMvc.perform(get("/api/v1/titles").param("genreCode", "CONTROLLER-FILTER-GENRE-CRT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listTitlesNeverIncludesWithdrawnTitles() throws Exception {
        Title withdrawn = persistWithdrawnTitle("Controller List Withdrawn Exclusion CRT");

        mockMvc.perform(get("/api/v1/titles").param("q", "list withdrawn exclusion crt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        assertTitleStillExistsButExcludedFromPublicListing(withdrawn.getId());
    }

    @Test
    void listTitlesContentItemsExposeOnlyCompactListFields() throws Exception {
        persistActiveTitle("Controller List Anti-leak CRT");

        mockMvc.perform(get("/api/v1/titles").param("q", "list anti-leak crt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].summary").doesNotExist())
                .andExpect(jsonPath("$.content[0].pageCount").doesNotExist())
                .andExpect(jsonPath("$.content[0].authors").doesNotExist())
                .andExpect(jsonPath("$.content[0].genres").doesNotExist())
                .andExpect(jsonPath("$.content[0].copies").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/titles/{titleId} — sécurité (permitAll)
    // ---------------------------------------------------------------

    @Test
    void getTitleByIdWithoutJwtReturnsOkForActiveTitle() throws Exception {
        Title title = persistActiveTitle("Controller Detail Security Anonymous CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", title.getId())).andExpect(status().isOk());
    }

    @Test
    void getTitleByIdWithRoleMemberReturnsOk() throws Exception {
        Title title = persistActiveTitle("Controller Detail Security Member CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_MEMBER"))))
                .andExpect(status().isOk());
    }

    @Test
    void getTitleByIdWithRoleLibrarianReturnsOk() throws Exception {
        Title title = persistActiveTitle("Controller Detail Security Librarian CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_LIBRARIAN"))))
                .andExpect(status().isOk());
    }

    @Test
    void getTitleByIdWithRoleAdminReturnsOk() throws Exception {
        Title title = persistActiveTitle("Controller Detail Security Admin CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", title.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/titles/{titleId} — comportement
    // ---------------------------------------------------------------

    @Test
    void getTitleByIdActiveReturnsDetail() throws Exception {
        Title title = persistActiveTitle("Controller Detail Active CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", title.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(title.getId()))
                .andExpect(jsonPath("$.title").value("Controller Detail Active CRT"))
                .andExpect(jsonPath("$.titleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.authors").isArray())
                .andExpect(jsonPath("$.genres").isArray());
    }

    @Test
    void getTitleByIdNonExistentReturns404WithTitleNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/v1/titles/{titleId}", 999999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TITLE_NOT_FOUND"));
    }

    @Test
    void getTitleByIdWithdrawnReturns404ForAnonymous() throws Exception {
        Title withdrawn = persistWithdrawnTitle("Controller Detail Withdrawn Anonymous CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", withdrawn.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TITLE_NOT_FOUND"));
    }

    @Test
    void getTitleByIdWithdrawnReturns404RegardlessOfAuthenticatedIdentity() throws Exception {
        Title withdrawn = persistWithdrawnTitle("Controller Detail Withdrawn Admin CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", withdrawn.getId())
                        .header("Authorization", "Bearer " + signToken(List.of("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TITLE_NOT_FOUND"));
    }

    @Test
    void getTitleByIdDoesNotExposeCopiesField() throws Exception {
        Title title = persistActiveTitle("Controller Detail Anti-leak CRT");

        mockMvc.perform(get("/api/v1/titles/{titleId}", title.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copies").doesNotExist());
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private void assertTitleStillExistsButExcludedFromPublicListing(Long titleId) {
        transactionTemplate().executeWithoutResult(status ->
                org.assertj.core.api.Assertions.assertThat(titleRepository.findById(titleId)).isPresent());
    }

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

    private Genre persistGenre(String code, String label) {
        Genre[] holder = new Genre[1];
        transactionTemplate().executeWithoutResult(status -> {
            Genre genre = new Genre();
            genre.setCode(code);
            genre.setLabel(label);
            genreRepository.save(genre);
            holder[0] = genre;
        });
        return holder[0];
    }

    private void linkAuthor(Title title, Author author) {
        transactionTemplate().executeWithoutResult(status -> {
            TitleAuthor titleAuthor = new TitleAuthor();
            titleAuthor.setId(new TitleAuthorId(title.getId(), author.getId()));
            titleAuthor.setTitle(entityManager.getReference(Title.class, title.getId()));
            titleAuthor.setAuthor(entityManager.getReference(Author.class, author.getId()));
            entityManager.persist(titleAuthor);
        });
    }

    private void linkGenre(Title title, Genre genre) {
        transactionTemplate().executeWithoutResult(status -> {
            TitleGenre titleGenre = new TitleGenre();
            titleGenre.setId(new TitleGenreId(genre.getId(), title.getId()));
            titleGenre.setTitle(entityManager.getReference(Title.class, title.getId()));
            titleGenre.setGenre(entityManager.getReference(Genre.class, genre.getId()));
            entityManager.persist(titleGenre);
        });
    }
}
