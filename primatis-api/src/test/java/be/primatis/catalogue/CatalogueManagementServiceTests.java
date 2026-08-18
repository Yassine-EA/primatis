package be.primatis.catalogue;

import be.primatis.catalogue.dto.AuthorResponse;
import be.primatis.catalogue.dto.CreateAuthorRequest;
import be.primatis.catalogue.dto.CreateGenreRequest;
import be.primatis.catalogue.dto.GenreResponse;
import be.primatis.catalogue.dto.UpdateAuthorRequest;
import be.primatis.catalogue.dto.UpdateGenreRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie {@link CatalogueManagementService} (DEV-06.5.1) contre PostgreSQL
 * réel : gestion staff minimale d'{@code Author}/{@code Genre} sous
 * {@code CATALOGUE_MANAGE}. Homonymie Author autorisée (contrairement à
 * Genre code/label, {@code UNIQUE}). Aucun test de suppression — aucun
 * {@code DELETE} n'existe (K.1, contrat fermé DEV-06.5.1).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogueManagementServiceTests {

    @Autowired
    private CatalogueManagementService catalogueManagementService;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithCatalogueManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("CATALOGUE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithoutCatalogueManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ---------------------------------------------------------------
    // Author
    // ---------------------------------------------------------------

    /**
     * Aucune {@code Authentication} dans le {@code SecurityContext} : Spring
     * Security lève {@code AuthenticationCredentialsNotFoundException}, pas
     * {@code AccessDeniedException} — cas distinct de {@link
     * #searchAuthorsWithoutCatalogueManageIsDenied} (authentifié mais
     * permission manquante). Gate PostgreSQL réel #2 : mauvaise attente
     * d'exception corrigée ici (aucune modification de
     * {@code @PreAuthorize}/{@code CatalogueManagementService}).
     */
    @Test
    void searchAuthorsWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> catalogueManagementService.searchAuthors(null, PageRequest.of(0, 20)));
    }

    @Test
    void searchAuthorsWithoutCatalogueManageIsDenied() {
        authenticateWithoutCatalogueManage();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> catalogueManagementService.searchAuthors(null, PageRequest.of(0, 20)));
    }

    @Test
    void searchAuthorsWithNullQueryReturnsAllAuthors() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Management List Author CRT");
        entityManager.flush();

        Page<AuthorResponse> page = catalogueManagementService.searchAuthors(null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(AuthorResponse::id).contains(author.getId());
    }

    @Test
    void searchAuthorsWithQueryFiltersByPartialFullName() {
        authenticateWithCatalogueManage();
        Author matching = persistAuthor("Management Query Match CRT");
        persistAuthor("Management Query Other CRT Entirely");
        entityManager.flush();

        Page<AuthorResponse> page = catalogueManagementService.searchAuthors(
                "query match crt", PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(AuthorResponse::id).containsExactly(matching.getId());
    }

    @Test
    void createAuthorNominalSucceeds() {
        authenticateWithCatalogueManage();

        AuthorResponse response = catalogueManagementService.createAuthor(new CreateAuthorRequest(
                "Management Create Author CRT", LocalDate.of(1950, 1, 1), LocalDate.of(2020, 1, 1),
                "Belge", "Biographie CRT"));

        assertThat(response.fullName()).isEqualTo("Management Create Author CRT");
        assertThat(response.nationality()).isEqualTo("Belge");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void createAuthorAllowsHomonyms() {
        authenticateWithCatalogueManage();
        catalogueManagementService.createAuthor(
                new CreateAuthorRequest("Management Homonym Author CRT", null, null, null, null));

        AuthorResponse second = catalogueManagementService.createAuthor(
                new CreateAuthorRequest("Management Homonym Author CRT", null, null, null, null));

        assertThat(second.fullName()).isEqualTo("Management Homonym Author CRT");
    }

    @Test
    void createAuthorWithCoherentDatesSucceeds() {
        authenticateWithCatalogueManage();

        AuthorResponse response = catalogueManagementService.createAuthor(new CreateAuthorRequest(
                "Management Coherent Dates Author CRT", LocalDate.of(1900, 1, 1), LocalDate.of(1980, 1, 1),
                null, null));

        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(response.deathDate()).isEqualTo(LocalDate.of(1980, 1, 1));
    }

    @Test
    void createAuthorWithIncoherentDatesIsRejected() {
        authenticateWithCatalogueManage();

        CreateAuthorRequest request = new CreateAuthorRequest(
                "Management Incoherent Dates Author CRT", LocalDate.of(2000, 1, 1), LocalDate.of(1999, 1, 1),
                null, null);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> catalogueManagementService.createAuthor(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_BIRTH_DATE_AFTER_DEATH_DATE"));
    }

    @Test
    void updateAuthorNominalPatchSucceeds() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Management Update Author CRT");
        entityManager.flush();

        UpdateAuthorRequest request = new UpdateAuthorRequest();
        request.setNationality("Française");

        AuthorResponse response = catalogueManagementService.updateAuthor(author.getId(), request);

        assertThat(response.nationality()).isEqualTo("Française");
        assertThat(response.fullName()).isEqualTo("Management Update Author CRT");
    }

    @Test
    void updateAuthorForNonExistentAuthorThrowsAuthorNotFound() {
        authenticateWithCatalogueManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueManagementService.updateAuthor(-1L, new UpdateAuthorRequest()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_NOT_FOUND"));
    }

    @Test
    void updateAuthorWithBlankFullNameIsRejected() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Management Update Blank Name Author CRT");
        entityManager.flush();

        UpdateAuthorRequest request = new UpdateAuthorRequest();
        request.setFullName("   ");

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> catalogueManagementService.updateAuthor(author.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_FULL_NAME_MUST_NOT_BE_BLANK"));
    }

    @Test
    void updateAuthorWithIncoherentFinalDatesIsRejected() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Management Update Incoherent Author CRT");
        author.setBirthDate(LocalDate.of(1950, 1, 1));
        entityManager.flush();

        UpdateAuthorRequest request = new UpdateAuthorRequest();
        request.setDeathDate(LocalDate.of(1940, 1, 1));

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> catalogueManagementService.updateAuthor(author.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_BIRTH_DATE_AFTER_DEATH_DATE"));
    }

    @Test
    void updateAuthorClearsNullableFieldsWithExplicitNull() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Management Update Clear Author CRT");
        author.setNationality("Belge");
        entityManager.flush();

        UpdateAuthorRequest request = new UpdateAuthorRequest();
        request.setNationality(null);

        AuthorResponse response = catalogueManagementService.updateAuthor(author.getId(), request);

        assertThat(response.nationality()).isNull();
    }

    // ---------------------------------------------------------------
    // Genre
    // ---------------------------------------------------------------

    @Test
    void listGenresReturnsAllGenres() {
        authenticateWithCatalogueManage();
        Genre genre = persistGenre("MANAGEMENT-LIST-GENRE-CRT", "Management List Genre CRT");
        entityManager.flush();

        Page<GenreResponse> page = catalogueManagementService.listGenres(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(GenreResponse::id).contains(genre.getId());
    }

    @Test
    void createGenreNominalSucceeds() {
        authenticateWithCatalogueManage();

        GenreResponse response = catalogueManagementService.createGenre(
                new CreateGenreRequest("MANAGEMENT-CREATE-GENRE-CRT", "Management Create Genre CRT", "Desc CRT"));

        assertThat(response.code()).isEqualTo("MANAGEMENT-CREATE-GENRE-CRT");
        assertThat(response.label()).isEqualTo("Management Create Genre CRT");
    }

    @Test
    void createGenreWithDuplicateCodeIsRejected() {
        authenticateWithCatalogueManage();
        persistGenre("MANAGEMENT-DUP-CODE-CRT", "Management Dup Code Original CRT");
        entityManager.flush();

        CreateGenreRequest request = new CreateGenreRequest(
                "MANAGEMENT-DUP-CODE-CRT", "Management Dup Code New CRT", null);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> catalogueManagementService.createGenre(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("GENRE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createGenreWithDuplicateLabelIsRejected() {
        authenticateWithCatalogueManage();
        persistGenre("MANAGEMENT-DUP-LABEL-ORIGINAL-CRT", "Management Dup Label CRT");
        entityManager.flush();

        CreateGenreRequest request = new CreateGenreRequest(
                "MANAGEMENT-DUP-LABEL-NEW-CRT", "Management Dup Label CRT", null);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> catalogueManagementService.createGenre(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("GENRE_LABEL_ALREADY_EXISTS"));
    }

    @Test
    void updateGenreNominalPatchSucceeds() {
        authenticateWithCatalogueManage();
        Genre genre = persistGenre("MANAGEMENT-UPDATE-GENRE-CRT", "Management Update Genre CRT");
        entityManager.flush();

        UpdateGenreRequest request = new UpdateGenreRequest();
        request.setDescription("Nouvelle description CRT");

        GenreResponse response = catalogueManagementService.updateGenre(genre.getId(), request);

        assertThat(response.description()).isEqualTo("Nouvelle description CRT");
        assertThat(response.code()).isEqualTo("MANAGEMENT-UPDATE-GENRE-CRT");
    }

    @Test
    void updateGenreForNonExistentGenreThrowsGenreNotFound() {
        authenticateWithCatalogueManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueManagementService.updateGenre(-1L, new UpdateGenreRequest()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("GENRE_NOT_FOUND"));
    }

    @Test
    void updateGenreResubmittingSameCodeIsNotTreatedAsConflict() {
        authenticateWithCatalogueManage();
        Genre genre = persistGenre("MANAGEMENT-SAME-CODE-CRT", "Management Same Code CRT");
        entityManager.flush();

        UpdateGenreRequest request = new UpdateGenreRequest();
        request.setCode("MANAGEMENT-SAME-CODE-CRT");

        GenreResponse response = catalogueManagementService.updateGenre(genre.getId(), request);

        assertThat(response.code()).isEqualTo("MANAGEMENT-SAME-CODE-CRT");
    }

    @Test
    void updateGenreToCodeUsedByAnotherGenreIsRejected() {
        authenticateWithCatalogueManage();
        persistGenre("MANAGEMENT-TAKEN-CODE-CRT", "Management Taken Code CRT");
        Genre target = persistGenre("MANAGEMENT-OWN-CODE-CRT", "Management Own Code CRT");
        entityManager.flush();

        UpdateGenreRequest request = new UpdateGenreRequest();
        request.setCode("MANAGEMENT-TAKEN-CODE-CRT");

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> catalogueManagementService.updateGenre(target.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("GENRE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void updateGenreWithBlankLabelIsRejected() {
        authenticateWithCatalogueManage();
        Genre genre = persistGenre("MANAGEMENT-BLANK-LABEL-CRT", "Management Blank Label CRT");
        entityManager.flush();

        UpdateGenreRequest request = new UpdateGenreRequest();
        request.setLabel("   ");

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> catalogueManagementService.updateGenre(genre.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("GENRE_LABEL_MUST_NOT_BE_BLANK"));
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private Author persistAuthor(String fullName) {
        Author author = new Author();
        author.setFullName(fullName);
        entityManager.persist(author);
        return author;
    }

    private Genre persistGenre(String code, String label) {
        Genre genre = new Genre();
        genre.setCode(code);
        genre.setLabel(label);
        entityManager.persist(genre);
        return genre;
    }
}
