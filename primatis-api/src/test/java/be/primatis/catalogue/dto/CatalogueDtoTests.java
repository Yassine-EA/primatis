package be.primatis.catalogue.dto;

import be.primatis.catalogue.Author;
import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Genre;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleAuthor;
import be.primatis.catalogue.TitleGenre;
import be.primatis.catalogue.TitleStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie les mappings {@code Entity → DTO} du domaine catalogue (DEV-06.3) :
 * {@link AuthorResponse#from}, {@link GenreResponse#from},
 * {@link TitleResponse#from}, {@link TitleDetailResponse#from},
 * {@link CopyResponse#from}. Tests unitaires purs — aucun Spring, aucun
 * PostgreSQL, aucune dépendance à {@code CatalogueRepositoryTests} (DEV-06.2).
 * Nommée {@code CatalogueDtoTests} (pas {@code CatalogueMapperTests}) : le
 * mapping suit le précédent DEV-05 (constructeur statique {@code from(...)}
 * directement sur chaque Response record), pas un {@code CatalogueMapper}
 * dédié — cf. le log DEV-06.3 pour la justification complète de ce choix.
 */
class CatalogueDtoTests {

    // ---------------------------------------------------------------
    // AuthorResponse
    // ---------------------------------------------------------------

    @Test
    void authorResponseMapsAllFields() {
        Author author = new Author();
        author.setFullName("Ursula K. Le Guin");
        author.setBirthDate(LocalDate.of(1929, 10, 21));
        author.setDeathDate(LocalDate.of(2018, 1, 22));
        author.setNationality("Américaine");
        author.setBiography("Autrice de science-fiction et de fantasy.");

        AuthorResponse response = AuthorResponse.from(author);

        assertThat(response.fullName()).isEqualTo("Ursula K. Le Guin");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1929, 10, 21));
        assertThat(response.deathDate()).isEqualTo(LocalDate.of(2018, 1, 22));
        assertThat(response.nationality()).isEqualTo("Américaine");
        assertThat(response.biography()).isEqualTo("Autrice de science-fiction et de fantasy.");
    }

    @Test
    void authorResponsePreservesNullOptionalFields() {
        Author author = new Author();
        author.setFullName("Auteur sans détails");

        AuthorResponse response = AuthorResponse.from(author);

        assertThat(response.birthDate()).isNull();
        assertThat(response.deathDate()).isNull();
        assertThat(response.nationality()).isNull();
        assertThat(response.biography()).isNull();
    }

    @Test
    void authorResponseFromNullAuthorThrowsExplicitly() {
        assertThatThrownBy(() -> AuthorResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // GenreResponse
    // ---------------------------------------------------------------

    @Test
    void genreResponseMapsAllFields() {
        Genre genre = new Genre();
        genre.setCode("SCI-FI");
        genre.setLabel("Science-fiction");
        genre.setDescription("Œuvres d'anticipation et de spéculation scientifique.");

        GenreResponse response = GenreResponse.from(genre);

        assertThat(response.code()).isEqualTo("SCI-FI");
        assertThat(response.label()).isEqualTo("Science-fiction");
        assertThat(response.description()).isEqualTo("Œuvres d'anticipation et de spéculation scientifique.");
    }

    @Test
    void genreResponsePreservesNullDescription() {
        Genre genre = new Genre();
        genre.setCode("DRAMA");
        genre.setLabel("Drame");
        genre.setDescription(null);

        GenreResponse response = GenreResponse.from(genre);

        assertThat(response.description()).isNull();
    }

    @Test
    void genreResponseFromNullGenreThrowsExplicitly() {
        assertThatThrownBy(() -> GenreResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // TitleResponse
    // ---------------------------------------------------------------

    @Test
    void titleResponseMapsListFieldsExactly() {
        Title title = baseTitle("Domain-Driven Design", "9780321125217", Language.EN, TitleStatus.ACTIVE);
        title.setSubtitle("Tackling Complexity in the Heart of Software");
        title.setPublicationYear(2003);
        title.setPublisher("Addison-Wesley");
        title.setCoverImageUrl("https://example.test/cover.jpg");

        TitleResponse response = TitleResponse.from(title);

        assertThat(response.isbn()).isEqualTo("9780321125217");
        assertThat(response.title()).isEqualTo("Domain-Driven Design");
        assertThat(response.subtitle()).isEqualTo("Tackling Complexity in the Heart of Software");
        assertThat(response.publicationYear()).isEqualTo(2003);
        assertThat(response.language()).isEqualTo(Language.EN);
        assertThat(response.publisher()).isEqualTo("Addison-Wesley");
        assertThat(response.coverImageUrl()).isEqualTo("https://example.test/cover.jpg");
        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
    }

    @Test
    void titleResponsePreservesNullIsbnAndSubtitle() {
        Title title = baseTitle("Ouvrage sans ISBN", null, Language.FR, TitleStatus.ACTIVE);
        title.setSubtitle(null);

        TitleResponse response = TitleResponse.from(title);

        assertThat(response.isbn()).isNull();
        assertThat(response.subtitle()).isNull();
    }

    @Test
    void titleResponseHasNoDetailOnlyComponent() {
        Set<String> forbidden = Set.of("summary", "pagecount", "authors", "genres", "copies",
                "createdat", "updatedat");

        assertThat(componentNamesLowercase(TitleResponse.class))
                .as("TitleResponse doit rester le contrat compact de liste/recherche")
                .noneMatch(forbidden::contains);
    }

    @Test
    void titleResponseFromNullTitleThrowsExplicitly() {
        assertThatThrownBy(() -> TitleResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // TitleDetailResponse
    // ---------------------------------------------------------------

    @Test
    void titleDetailResponseMapsAllTitleFields() {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-02-01T10:00:00Z");
        Title title = baseTitle("Clean Architecture", "9780134494166", Language.EN, TitleStatus.ACTIVE);
        title.setSubtitle("A Craftsman's Guide to Software Structure and Design");
        title.setSummary("Un guide sur l'architecture logicielle.");
        title.setPublicationYear(2017);
        title.setPageCount(432);
        title.setPublisher("Prentice Hall");
        title.setCoverImageUrl("https://example.test/clean-architecture.jpg");
        title.setCreatedAt(createdAt);
        title.setUpdatedAt(updatedAt);

        TitleDetailResponse response = TitleDetailResponse.from(title, List.of(), List.of());

        assertThat(response.isbn()).isEqualTo("9780134494166");
        assertThat(response.title()).isEqualTo("Clean Architecture");
        assertThat(response.subtitle()).isEqualTo("A Craftsman's Guide to Software Structure and Design");
        assertThat(response.summary()).isEqualTo("Un guide sur l'architecture logicielle.");
        assertThat(response.publicationYear()).isEqualTo(2017);
        assertThat(response.language()).isEqualTo(Language.EN);
        assertThat(response.pageCount()).isEqualTo(432);
        assertThat(response.publisher()).isEqualTo("Prentice Hall");
        assertThat(response.coverImageUrl()).isEqualTo("https://example.test/clean-architecture.jpg");
        assertThat(response.titleStatus()).isEqualTo(TitleStatus.ACTIVE);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void titleDetailResponseMapsMultipleAuthorsPreservingOrder() {
        Title title = baseTitle("Co-authored Work", null, Language.EN, TitleStatus.ACTIVE);
        Author first = namedAuthor("Andrew Hunt");
        Author second = namedAuthor("David Thomas");

        TitleDetailResponse response = TitleDetailResponse.from(title, List.of(first, second), List.of());

        assertThat(response.authors()).extracting(AuthorResponse::fullName)
                .containsExactly("Andrew Hunt", "David Thomas");
    }

    @Test
    void titleDetailResponseMapsMultipleGenresPreservingOrder() {
        Title title = baseTitle("Multi-genre Work", null, Language.FR, TitleStatus.ACTIVE);
        Genre first = codedGenre("FICTION", "Fiction");
        Genre second = codedGenre("DRAMA", "Drame");

        TitleDetailResponse response = TitleDetailResponse.from(title, List.of(), List.of(first, second));

        assertThat(response.genres()).extracting(GenreResponse::code).containsExactly("FICTION", "DRAMA");
    }

    @Test
    void titleDetailResponseMapsNullAuthorsListToEmptyList() {
        Title title = baseTitle("Sans auteur fourni", null, Language.FR, TitleStatus.ACTIVE);

        TitleDetailResponse response = TitleDetailResponse.from(title, null, List.of());

        assertThat(response.authors()).isEmpty();
    }

    @Test
    void titleDetailResponseMapsNullGenresListToEmptyList() {
        Title title = baseTitle("Sans genre fourni", null, Language.FR, TitleStatus.ACTIVE);

        TitleDetailResponse response = TitleDetailResponse.from(title, List.of(), null);

        assertThat(response.genres()).isEmpty();
    }

    @Test
    void titleDetailResponseRejectsNullElementInAuthorsListExplicitly() {
        Title title = baseTitle("Liste auteurs invalide", null, Language.FR, TitleStatus.ACTIVE);
        Author validAuthor = namedAuthor("Auteur valide");

        assertThatThrownBy(() -> TitleDetailResponse.from(title, Arrays.asList(validAuthor, null), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void titleDetailResponseRejectsNullElementInGenresListExplicitly() {
        Title title = baseTitle("Liste genres invalide", null, Language.FR, TitleStatus.ACTIVE);
        Genre validGenre = codedGenre("FICTION", "Fiction");

        assertThatThrownBy(() -> TitleDetailResponse.from(title, List.of(), Arrays.asList(validGenre, null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void titleDetailResponseFromNullTitleThrowsExplicitly() {
        assertThatThrownBy(() -> TitleDetailResponse.from(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // CopyResponse
    // ---------------------------------------------------------------

    @Test
    void copyResponseMapsAllFields() {
        // Title.id est généré par séquence DB (aucun setter exposé, par
        // conception) : en test unitaire pur sans persistance, title.getId()
        // reste null. L'assertion vérifie que titleId provient bien de
        // copy.getTitle().getId() (et non d'une autre source), pas une
        // valeur technique arbitraire — cf. CatalogueRepositoryTests
        // (DEV-06.2, PostgreSQL réel) pour la preuve avec un id réellement
        // généré.
        Title title = baseTitle("Ouvrage avec exemplaire", null, Language.FR, TitleStatus.ACTIVE);
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode("INV-000123");
        copy.setLocation("Rayon B3");
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        CopyResponse response = CopyResponse.from(copy);

        assertThat(response.titleId()).isEqualTo(title.getId());
        assertThat(response.inventoryCode()).isEqualTo("INV-000123");
        assertThat(response.location()).isEqualTo("Rayon B3");
        assertThat(response.copyCondition()).isEqualTo(CopyCondition.GOOD);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void copyResponseFromNullCopyThrowsExplicitly() {
        assertThatThrownBy(() -> CopyResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // Anti-fuite structurelle
    // ---------------------------------------------------------------

    @Test
    void noDtoComponentExposesACatalogueEntityDirectlyOrThroughAGenericType() {
        Set<Class<?>> forbiddenEntityTypes = Set.of(
                Title.class, Author.class, Genre.class, Copy.class, TitleAuthor.class, TitleGenre.class);
        List<Class<?>> catalogueDtos = List.of(
                AuthorResponse.class, GenreResponse.class, TitleResponse.class,
                TitleDetailResponse.class, CopyResponse.class);

        for (Class<?> dto : catalogueDtos) {
            for (RecordComponent component : dto.getRecordComponents()) {
                assertThat(forbiddenEntityTypes)
                        .as("%s.%s ne doit pas exposer une Entity directement", dto.getSimpleName(), component.getName())
                        .doesNotContain(component.getType());

                for (Class<?> typeArgument : genericTypeArgumentsOf(component)) {
                    assertThat(forbiddenEntityTypes)
                            .as("%s.%s ne doit pas exposer une Entity via un type générique",
                                    dto.getSimpleName(), component.getName())
                            .doesNotContain(typeArgument);
                }
            }
        }
    }

    private static List<Class<?>> genericTypeArgumentsOf(RecordComponent component) {
        Type genericType = component.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return List.of();
        }
        return Arrays.stream(parameterizedType.getActualTypeArguments())
                .filter(Class.class::isInstance)
                .<Class<?>>map(typeArgument -> (Class<?>) typeArgument)
                .toList();
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private static Set<String> componentNamesLowercase(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Title baseTitle(String title, String isbn, Language language, TitleStatus titleStatus) {
        Title entity = new Title();
        entity.setTitle(title);
        entity.setIsbn(isbn);
        entity.setLanguage(language);
        entity.setTitleStatus(titleStatus);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static Author namedAuthor(String fullName) {
        Author author = new Author();
        author.setFullName(fullName);
        return author;
    }

    private static Genre codedGenre(String code, String label) {
        Genre genre = new Genre();
        genre.setCode(code);
        genre.setLabel(label);
        return genre;
    }
}
