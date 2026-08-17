package be.primatis.catalogue;

import be.primatis.catalogue.dto.CreateTitleRequest;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
import be.primatis.catalogue.dto.UpdateTitleRequest;
import be.primatis.catalogue.dto.UpdateTitleStatusRequest;
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

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie {@link CatalogueService} contre PostgreSQL réel : consultation
 * publique (DEV-06.4, imposition serveur de {@code TitleStatus.ACTIVE},
 * combinaison des filtres via {@link TitleSpecifications}, pagination,
 * mapping DTO) et gestion staff (DEV-06.5, {@code CATALOGUE_MANAGE} —
 * création, modification, transitions {@code TitleStatus}, consultation
 * {@code ACTIVE}+{@code WITHDRAWN}). Les méthodes publiques n'ont aucun
 * {@code @PreAuthorize} (K.2) ; les méthodes staff en ont un, simulé ici
 * comme dans {@code UserServiceTests} via {@code SecurityContextHolder}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogueServiceTests {

    @Autowired
    private CatalogueService catalogueService;

    @Autowired
    private TitleRepository titleRepository;

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
    // searchPublicTitles
    // ---------------------------------------------------------------

    @Test
    void searchPublicTitlesMethodHasNoTitleStatusParameter() throws NoSuchMethodException {
        Method method = CatalogueService.class.getMethod(
                "searchPublicTitles", String.class, Long.class, String.class, Language.class,
                org.springframework.data.domain.Pageable.class);

        assertThat(method.getParameterTypes()).doesNotContain(TitleStatus.class);
    }

    @Test
    void searchPublicTitlesNeverReturnsWithdrawnTitlesEvenWithoutAnyFilter() {
        Title active = persistTitle("Service Active Work CRT", Language.EN, TitleStatus.ACTIVE);
        persistTitle("Service Withdrawn Work CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                null, null, null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).contains(active.getId());
        assertThat(page.getContent()).extracting(TitleResponse::titleStatus)
                .allMatch(status -> status == TitleStatus.ACTIVE);
    }

    @Test
    void searchPublicTitlesWithNullQueryAppliesNoTextFilter() {
        Title title = persistTitle("Service Null Query Work CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                null, null, null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).contains(title.getId());
    }

    @Test
    void searchPublicTitlesWithBlankQueryAppliesNoTextFilter() {
        Title title = persistTitle("Service Blank Query Work CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                "   ", null, null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).contains(title.getId());
    }

    @Test
    void searchPublicTitlesFiltersByAuthorId() {
        Author author = persistAuthor("Service Author CRT");
        Title matching = persistTitle("Service Author Match CRT", Language.EN, TitleStatus.ACTIVE);
        persistTitle("Service Author Non-match CRT", Language.EN, TitleStatus.ACTIVE);
        linkAuthor(matching, author);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                null, author.getId(), null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).containsExactly(matching.getId());
    }

    @Test
    void searchPublicTitlesFiltersByGenreCode() {
        Genre genre = persistGenre("SERVICE-GENRE-CRT", "Service Genre CRT");
        Title matching = persistTitle("Service Genre Match CRT", Language.EN, TitleStatus.ACTIVE);
        persistTitle("Service Genre Non-match CRT", Language.EN, TitleStatus.ACTIVE);
        linkGenre(matching, genre);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                null, null, "SERVICE-GENRE-CRT", null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).containsExactly(matching.getId());
    }

    @Test
    void searchPublicTitlesFiltersByLanguage() {
        Title frenchTitle = persistTitle("Service Ouvrage FR CRT", Language.FR, TitleStatus.ACTIVE);
        persistTitle("Service English Work CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                null, null, null, Language.FR, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).contains(frenchTitle.getId());
        assertThat(page.getContent()).extracting(TitleResponse::language).allMatch(language -> language == Language.FR);
    }

    @Test
    void searchPublicTitlesCombinesFilters() {
        Title matching = persistTitle("Service Combined Filter CRT", Language.EN, TitleStatus.ACTIVE);
        persistTitle("Service Combined Filter CRT", Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                "combined filter", null, null, Language.EN, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).containsExactly(matching.getId());
    }

    @Test
    void searchPublicTitlesTransmitsPagination() {
        String sharedKeyword = "Service Pagination Probe CRT";
        persistTitle(sharedKeyword + " 1", Language.EN, TitleStatus.ACTIVE);
        persistTitle(sharedKeyword + " 2", Language.EN, TitleStatus.ACTIVE);
        persistTitle(sharedKeyword + " 3", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                "service pagination probe crt", null, null, null, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
    }

    @Test
    void searchPublicTitlesMapsToTitleResponse() {
        Title title = persistTitle("Service Mapping Check CRT", Language.EN, TitleStatus.ACTIVE);
        title.setIsbn("9780000000CRT");
        title.setPublisher("CRT Publisher");
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchPublicTitles(
                "service mapping check crt", null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        TitleResponse response = page.getContent().get(0);
        assertThat(response.id()).isEqualTo(title.getId());
        assertThat(response.isbn()).isEqualTo("9780000000CRT");
        assertThat(response.publisher()).isEqualTo("CRT Publisher");
        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
    }

    // ---------------------------------------------------------------
    // getPublicTitleById
    // ---------------------------------------------------------------

    @Test
    void getPublicTitleByIdReturnsDetailForActiveTitle() {
        Title title = persistTitle("Service Detail Active CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        TitleDetailResponse response = catalogueService.getPublicTitleById(title.getId());

        assertThat(response.id()).isEqualTo(title.getId());
        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
    }

    @Test
    void getPublicTitleByIdThrowsTitleNotFoundForNonExistentId() {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueService.getPublicTitleById(-1L))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND"));
    }

    @Test
    void getPublicTitleByIdThrowsTitleNotFoundForWithdrawnTitle() {
        Title withdrawn = persistTitle("Service Detail Withdrawn CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueService.getPublicTitleById(withdrawn.getId()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND"));
    }

    @Test
    void getPublicTitleByIdMapsAuthors() {
        Title title = persistTitle("Service Detail Authors CRT", Language.EN, TitleStatus.ACTIVE);
        Author first = persistAuthor("Service Detail Author A CRT");
        Author second = persistAuthor("Service Detail Author B CRT");
        linkAuthor(title, first);
        linkAuthor(title, second);
        entityManager.flush();

        TitleDetailResponse response = catalogueService.getPublicTitleById(title.getId());

        assertThat(response.authors()).extracting(author -> author.fullName())
                .containsExactlyInAnyOrder("Service Detail Author A CRT", "Service Detail Author B CRT");
    }

    @Test
    void getPublicTitleByIdMapsGenres() {
        Title title = persistTitle("Service Detail Genres CRT", Language.EN, TitleStatus.ACTIVE);
        Genre first = persistGenre("SERVICE-DETAIL-GENRE-A-CRT", "Service Detail Genre A CRT");
        Genre second = persistGenre("SERVICE-DETAIL-GENRE-B-CRT", "Service Detail Genre B CRT");
        linkGenre(title, first);
        linkGenre(title, second);
        entityManager.flush();

        TitleDetailResponse response = catalogueService.getPublicTitleById(title.getId());

        assertThat(response.genres()).extracting(genre -> genre.code())
                .containsExactlyInAnyOrder("SERVICE-DETAIL-GENRE-A-CRT", "SERVICE-DETAIL-GENRE-B-CRT");
    }

    /**
     * Un Title possédant des Copies reste consultable normalement (aucun
     * couplage accidentel) : {@link TitleDetailResponse} ne porte
     * structurellement aucun champ Copy (déjà prouvé par
     * {@code CatalogueDtoTests}, DEV-06.3) — cette preuve comportementale
     * confirme que le Service lui-même ne charge/n'échoue pas à cause d'une
     * Copy présente.
     */
    @Test
    void getPublicTitleByIdSucceedsRegardlessOfCopiesAndDoesNotExposeThem() {
        Title title = persistTitle("Service Detail With Copy CRT", Language.EN, TitleStatus.ACTIVE);
        persistCopy(title, "SERVICE-DETAIL-COPY-CRT");
        entityManager.flush();

        TitleDetailResponse response = catalogueService.getPublicTitleById(title.getId());

        assertThat(response.id()).isEqualTo(title.getId());
        assertThat(TitleDetailResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("copies", "copy");
    }

    // ---------------------------------------------------------------
    // createTitle (DEV-06.5)
    // ---------------------------------------------------------------

    /**
     * Aucune {@code Authentication} dans le {@code SecurityContext} : Spring
     * Security lève {@code AuthenticationCredentialsNotFoundException}, pas
     * {@code AccessDeniedException} — cas distinct de {@link
     * #createTitleWithoutCatalogueManageIsDenied} (authentifié mais
     * permission manquante). Gate PostgreSQL réel #2 : mauvaise attente
     * d'exception corrigée ici (aucune modification de
     * {@code @PreAuthorize}/{@code CatalogueService}).
     */
    @Test
    void createTitleWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> catalogueService.createTitle(
                        new CreateTitleRequest(null, "Denied CRT", null, null, null, Language.EN, null, null, null,
                                List.of(), null)));
    }

    @Test
    void createTitleWithoutCatalogueManageIsDenied() {
        authenticateWithoutCatalogueManage();

        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> catalogueService.createTitle(
                new CreateTitleRequest(null, "Denied CRT", null, null, null, Language.EN, null, null, null,
                        List.of(), null)));
    }

    @Test
    void createTitleNominalCreatesActiveTitleWithAuthorAndGenreAssociations() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Create Nominal Author CRT");
        Genre genre = persistGenre("CREATE-NOMINAL-GENRE-CRT", "Create Nominal Genre CRT");
        entityManager.flush();

        CreateTitleRequest request = new CreateTitleRequest(
                "9780000001CRT", "Create Nominal Title CRT", "Subtitle CRT", "Summary CRT", 2024, Language.EN,
                200, "Publisher CRT", "https://example.test/cover.jpg",
                List.of(author.getId()), List.of(genre.getId()));

        TitleDetailResponse response = catalogueService.createTitle(request);

        assertThat(response.title()).isEqualTo("Create Nominal Title CRT");
        assertThat(response.isbn()).isEqualTo("9780000001CRT");
        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
        assertThat(response.authors()).extracting(a -> a.fullName()).containsExactly("Create Nominal Author CRT");
        assertThat(response.genres()).extracting(g -> g.code()).containsExactly("CREATE-NOMINAL-GENRE-CRT");
    }

    @Test
    void createTitleAlwaysImposesActiveStatus() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Create Active Author CRT");
        entityManager.flush();

        TitleDetailResponse response = catalogueService.createTitle(new CreateTitleRequest(
                null, "Create Active Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(author.getId()), null));

        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
    }

    @Test
    void createTitleWithNullIsbnSucceeds() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Create Null Isbn Author CRT");
        entityManager.flush();

        TitleDetailResponse response = catalogueService.createTitle(new CreateTitleRequest(
                null, "Create Null Isbn Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(author.getId()), null));

        assertThat(response.isbn()).isNull();
    }

    @Test
    void createTitleWithDuplicateIsbnIsRejected() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Create Duplicate Isbn Author CRT");
        persistTitle("Existing Isbn Title CRT", Language.EN, TitleStatus.ACTIVE).setIsbn("9780000002CRT");
        entityManager.flush();

        CreateTitleRequest request = new CreateTitleRequest(
                "9780000002CRT", "Create Duplicate Isbn Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(author.getId()), null);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> catalogueService.createTitle(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("ISBN_ALREADY_EXISTS"));
    }

    @Test
    void createTitleWithoutAuthorIsRejected() {
        authenticateWithCatalogueManage();

        CreateTitleRequest request = new CreateTitleRequest(
                null, "Create No Author Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(), null);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> catalogueService.createTitle(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_IDS_MUST_NOT_BE_EMPTY"));
    }

    @Test
    void createTitleWithUnknownAuthorIsRejected() {
        authenticateWithCatalogueManage();

        CreateTitleRequest request = new CreateTitleRequest(
                null, "Create Unknown Author Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(-999L), null);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueService.createTitle(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_NOT_FOUND"));
    }

    @Test
    void createTitleWithUnknownGenreIsRejected() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Create Unknown Genre Author CRT");
        entityManager.flush();

        CreateTitleRequest request = new CreateTitleRequest(
                null, "Create Unknown Genre Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(author.getId()), List.of(-999L));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueService.createTitle(request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("GENRE_NOT_FOUND"));
    }

    @Test
    void createTitleWithUnknownGenreDoesNotPersistAnything() {
        authenticateWithCatalogueManage();
        Author author = persistAuthor("Create Atomicity Author CRT");
        entityManager.flush();

        CreateTitleRequest request = new CreateTitleRequest(
                "9780000003CRT", "Create Atomicity Title CRT", null, null, null, Language.EN, null, null, null,
                List.of(author.getId()), List.of(-999L));

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> catalogueService.createTitle(request));

        assertThat(titleRepository.existsByIsbn("9780000003CRT")).isFalse();
    }

    // ---------------------------------------------------------------
    // updateTitle (DEV-06.5)
    // ---------------------------------------------------------------

    @Test
    void updateTitleNominalUpdatesFields() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Update Nominal Before CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        UpdateTitleRequest request = new UpdateTitleRequest();
        request.setTitle("Update Nominal After CRT");
        request.setPublisher("Update Nominal Publisher CRT");

        TitleDetailResponse response = catalogueService.updateTitle(title.getId(), request);

        assertThat(response.title()).isEqualTo("Update Nominal After CRT");
        assertThat(response.publisher()).isEqualTo("Update Nominal Publisher CRT");
    }

    @Test
    void updateTitleForNonExistentTitleThrowsTitleNotFound() {
        authenticateWithCatalogueManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueService.updateTitle(-1L, new UpdateTitleRequest()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND"));
    }

    @Test
    void updateTitlePreservesFieldsAbsentFromRequest() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Update Preserve Title CRT", Language.EN, TitleStatus.ACTIVE);
        title.setPublisher("Original Publisher CRT");
        title.setSubtitle("Original Subtitle CRT");
        entityManager.flush();

        UpdateTitleRequest request = new UpdateTitleRequest();
        request.setSummary("Only summary changes CRT");

        TitleDetailResponse response = catalogueService.updateTitle(title.getId(), request);

        assertThat(response.summary()).isEqualTo("Only summary changes CRT");
        assertThat(response.publisher()).isEqualTo("Original Publisher CRT");
        assertThat(response.subtitle()).isEqualTo("Original Subtitle CRT");
        assertThat(response.title()).isEqualTo("Update Preserve Title CRT");
    }

    @Test
    void updateTitleReplacesAuthors() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Update Authors Title CRT", Language.EN, TitleStatus.ACTIVE);
        Author original = persistAuthor("Update Authors Original CRT");
        Author replacement = persistAuthor("Update Authors Replacement CRT");
        linkAuthor(title, original);
        entityManager.flush();

        UpdateTitleRequest request = new UpdateTitleRequest();
        request.setAuthorIds(List.of(replacement.getId()));

        TitleDetailResponse response = catalogueService.updateTitle(title.getId(), request);

        assertThat(response.authors()).extracting(a -> a.fullName())
                .containsExactly("Update Authors Replacement CRT");
    }

    @Test
    void updateTitleWithEmptyAuthorIdsIsRejected() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Update Empty Authors Title CRT", Language.EN, TitleStatus.ACTIVE);
        Author author = persistAuthor("Update Empty Authors Author CRT");
        linkAuthor(title, author);
        entityManager.flush();

        UpdateTitleRequest request = new UpdateTitleRequest();
        request.setAuthorIds(List.of());

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> catalogueService.updateTitle(title.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("AUTHOR_IDS_MUST_NOT_BE_EMPTY"));
    }

    @Test
    void updateTitleReplacesGenres() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Update Genres Title CRT", Language.EN, TitleStatus.ACTIVE);
        Genre original = persistGenre("UPDATE-GENRES-ORIGINAL-CRT", "Update Genres Original CRT");
        Genre replacement = persistGenre("UPDATE-GENRES-REPLACEMENT-CRT", "Update Genres Replacement CRT");
        linkGenre(title, original);
        entityManager.flush();

        UpdateTitleRequest request = new UpdateTitleRequest();
        request.setGenreIds(List.of(replacement.getId()));

        TitleDetailResponse response = catalogueService.updateTitle(title.getId(), request);

        assertThat(response.genres()).extracting(g -> g.code())
                .containsExactly("UPDATE-GENRES-REPLACEMENT-CRT");
    }

    // ---------------------------------------------------------------
    // updateTitleStatus (DEV-06.5)
    // ---------------------------------------------------------------

    @Test
    void updateTitleStatusTransitionsActiveToWithdrawn() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Status Active To Withdrawn CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        TitleDetailResponse response =
                catalogueService.updateTitleStatus(title.getId(), new UpdateTitleStatusRequest(TitleStatus.WITHDRAWN));

        assertThat(response.titleStatus()).isEqualTo(TitleStatus.WITHDRAWN);
    }

    @Test
    void updateTitleStatusTransitionsWithdrawnToActive() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Status Withdrawn To Active CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        TitleDetailResponse response =
                catalogueService.updateTitleStatus(title.getId(), new UpdateTitleStatusRequest(TitleStatus.ACTIVE));

        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
    }

    @Test
    void updateTitleStatusIsIdempotentWhenAlreadyInTargetStatus() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Status Idempotent CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        TitleDetailResponse response =
                catalogueService.updateTitleStatus(title.getId(), new UpdateTitleStatusRequest(TitleStatus.ACTIVE));

        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
    }

    // ---------------------------------------------------------------
    // searchStaffTitles / getStaffTitleById (DEV-06.5)
    // ---------------------------------------------------------------

    @Test
    void searchStaffTitlesSeesActiveAndWithdrawnByDefault() {
        authenticateWithCatalogueManage();
        Title active = persistTitle("Staff List Active CRT", Language.EN, TitleStatus.ACTIVE);
        Title withdrawn = persistTitle("Staff List Withdrawn CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchStaffTitles(
                "staff list", null, null, null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id)
                .contains(active.getId(), withdrawn.getId());
    }

    @Test
    void searchStaffTitlesFiltersByTitleStatusActive() {
        authenticateWithCatalogueManage();
        Title active = persistTitle("Staff Filter Active CRT", Language.EN, TitleStatus.ACTIVE);
        persistTitle("Staff Filter Withdrawn CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchStaffTitles(
                "staff filter", null, null, null, TitleStatus.ACTIVE, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).containsExactly(active.getId());
    }

    @Test
    void searchStaffTitlesFiltersByTitleStatusWithdrawn() {
        authenticateWithCatalogueManage();
        persistTitle("Staff Filter Withdrawn Only Active CRT", Language.EN, TitleStatus.ACTIVE);
        Title withdrawn = persistTitle("Staff Filter Withdrawn Only Withdrawn CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        Page<TitleResponse> page = catalogueService.searchStaffTitles(
                "staff filter withdrawn only", null, null, null, TitleStatus.WITHDRAWN, PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TitleResponse::id).containsExactly(withdrawn.getId());
    }

    @Test
    void getStaffTitleByIdReturnsWithdrawnTitle() {
        authenticateWithCatalogueManage();
        Title withdrawn = persistTitle("Staff Detail Withdrawn CRT", Language.EN, TitleStatus.WITHDRAWN);
        entityManager.flush();

        TitleDetailResponse response = catalogueService.getStaffTitleById(withdrawn.getId());

        assertThat(response.titleStatus()).isEqualTo(TitleStatus.WITHDRAWN);
    }

    // ---------------------------------------------------------------
    // Non-régression public (DEV-06.4, mission §35)
    // ---------------------------------------------------------------

    @Test
    void publicSearchStillExcludesTitleWithdrawnByStaffAction() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Non-regression Withdrawal CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();
        catalogueService.updateTitleStatus(title.getId(), new UpdateTitleStatusRequest(TitleStatus.WITHDRAWN));

        Page<TitleResponse> publicPage = catalogueService.searchPublicTitles(
                "non-regression withdrawal", null, null, null, PageRequest.of(0, 20));

        assertThat(publicPage.getContent()).isEmpty();
    }

    @Test
    void publicDetailStillReturns404ForTitleWithdrawnByStaffAction() {
        authenticateWithCatalogueManage();
        Title title = persistTitle("Non-regression Detail Withdrawal CRT", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();
        catalogueService.updateTitleStatus(title.getId(), new UpdateTitleStatusRequest(TitleStatus.WITHDRAWN));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> catalogueService.getPublicTitleById(title.getId()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private Title persistTitle(String title, Language language, TitleStatus titleStatus) {
        Title entity = new Title();
        entity.setTitle(title);
        entity.setLanguage(language);
        entity.setTitleStatus(titleStatus);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entityManager.persist(entity);
        return entity;
    }

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

    private void linkAuthor(Title title, Author author) {
        TitleAuthor titleAuthor = new TitleAuthor();
        titleAuthor.setId(new TitleAuthorId(title.getId(), author.getId()));
        titleAuthor.setTitle(title);
        titleAuthor.setAuthor(author);
        entityManager.persist(titleAuthor);
    }

    private void linkGenre(Title title, Genre genre) {
        TitleGenre titleGenre = new TitleGenre();
        titleGenre.setId(new TitleGenreId(genre.getId(), title.getId()));
        titleGenre.setTitle(title);
        titleGenre.setGenre(genre);
        entityManager.persist(titleGenre);
    }

    private Copy persistCopy(Title title, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }
}
