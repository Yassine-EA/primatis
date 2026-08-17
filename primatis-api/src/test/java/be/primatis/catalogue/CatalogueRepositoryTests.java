package be.primatis.catalogue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link TitleRepository},
 * {@link AuthorRepository}, {@link GenreRepository} et {@link CopyRepository}
 * ajoutées en DEV-06.2 : recherche catalogue multi-filtres
 * ({@link TitleSpecifications}), résolution Author/Genre, exemplaires d'un
 * Title. Ne teste pas les méthodes JpaRepository standard déjà couvertes par
 * Spring Data lui-même, ni {@code CopyRepository.findByInventoryCode}, déjà
 * couvert par {@code RepositoryQueryTests} (DEV-02.5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogueRepositoryTests {

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private TitleAuthorRepository titleAuthorRepository;

    @Autowired
    private TitleGenreRepository titleGenreRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // TitleRepository.findByIsbn / existsByIsbn
    // ---------------------------------------------------------------

    @Test
    void findsTitleByIsbnWhenPresent() {
        Title title = persistTitle("Effective Java", "9780134685991", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(titleRepository.findByIsbn("9780134685991"))
                .isPresent()
                .get()
                .extracting(Title::getId)
                .isEqualTo(title.getId());
    }

    @Test
    void findByIsbnIsEmptyWhenAbsent() {
        assertThat(titleRepository.findByIsbn("0000000000000")).isEmpty();
    }

    @Test
    void existsByIsbnReflectsPresenceAndAbsence() {
        persistTitle("Clean Code", "9780132350884", Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(titleRepository.existsByIsbn("9780132350884")).isTrue();
        assertThat(titleRepository.existsByIsbn("9999999999999")).isFalse();
    }

    // ---------------------------------------------------------------
    // TitleRepository / TitleSpecifications — recherche multi-filtres
    // ---------------------------------------------------------------

    @Test
    void searchMatchesTitleKeywordCaseInsensitivelyAndPartially() {
        Title matching = persistTitle("Domain-Driven Design", null, Language.EN, TitleStatus.ACTIVE);
        persistTitle("Les Misérables", null, Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching("driven", null, null, null, null), PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId).containsExactly(matching.getId());
    }

    @Test
    void searchWithNullKeywordAppliesNoTitleFilter() {
        Title first = persistTitle("Refactoring", null, Language.EN, TitleStatus.ACTIVE);
        Title second = persistTitle("Patterns of Enterprise Application Architecture", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching(null, null, null, null, null), PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId)
                .contains(first.getId(), second.getId());
    }

    @Test
    void searchWithBlankKeywordBehavesAsNoFilter() {
        Title title = persistTitle("Working Effectively with Legacy Code", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching("   ", null, null, null, null), PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId).contains(title.getId());
    }

    @Test
    void searchByAuthorReturnsTitleExactlyOnceEvenWithMultipleAuthors() {
        Author first = persistAuthor("Eric Evans");
        Author second = persistAuthor("Martin Fowler");
        Title title = persistTitle("Co-authored Work", null, Language.EN, TitleStatus.ACTIVE);
        linkAuthor(title, first);
        linkAuthor(title, second);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching(null, first.getId(), null, null, null), PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId).containsExactly(title.getId());
        assertThat(found.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchByAuthorExcludesTitlesOfOtherAuthors() {
        Author target = persistAuthor("Robert C. Martin");
        Author other = persistAuthor("Kent Beck");
        Title targetTitle = persistTitle("Clean Architecture", null, Language.EN, TitleStatus.ACTIVE);
        Title otherTitle = persistTitle("Test-Driven Development", null, Language.EN, TitleStatus.ACTIVE);
        linkAuthor(targetTitle, target);
        linkAuthor(otherTitle, other);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching(null, target.getId(), null, null, null), PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId).containsExactly(targetTitle.getId());
    }

    @Test
    void searchByGenreReturnsTitleExactlyOnceEvenWithMultipleGenres() {
        Genre fiction = persistGenre("FICTION-CRT-1", "Fiction CRT 1");
        Genre drama = persistGenre("DRAMA-CRT-1", "Drame CRT 1");
        Title title = persistTitle("Multi-genre Work", null, Language.FR, TitleStatus.ACTIVE);
        linkGenre(title, fiction);
        linkGenre(title, drama);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching(null, null, "FICTION-CRT-1", null, null), PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId).containsExactly(title.getId());
        assertThat(found.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchByLanguageFiltersExactMatch() {
        Title frenchTitle = persistTitle("Ouvrage francophone CRT", null, Language.FR, TitleStatus.ACTIVE);
        persistTitle("English Work CRT", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching(null, null, null, Language.FR, null), PageRequest.of(0, 100));

        assertThat(found.getContent()).extracting(Title::getId).contains(frenchTitle.getId());
        assertThat(found.getContent()).extracting(Title::getLanguage).allMatch(language -> language == Language.FR);
    }

    @Test
    void searchByTitleStatusFiltersExactMatch() {
        Title withdrawn = persistTitle("Ouvrage retiré CRT", null, Language.FR, TitleStatus.WITHDRAWN);
        persistTitle("Ouvrage actif CRT", null, Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching(null, null, null, null, TitleStatus.WITHDRAWN), PageRequest.of(0, 100));

        assertThat(found.getContent()).extracting(Title::getId).contains(withdrawn.getId());
        assertThat(found.getContent()).extracting(Title::getTitleStatus)
                .allMatch(status -> status == TitleStatus.WITHDRAWN);
    }

    @Test
    void searchCombinesMultipleFiltersWithAnd() {
        Title matches = persistTitle("Combined Filter Match CRT", null, Language.EN, TitleStatus.ACTIVE);
        persistTitle("Combined Filter Match CRT", null, Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching("combined filter", null, null, Language.EN, TitleStatus.ACTIVE),
                PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Title::getId).containsExactly(matches.getId());
    }

    @Test
    void searchReturnsEmptyPageWhenNoTitleMatches() {
        persistTitle("Some Existing Work CRT", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> found = titleRepository.findAll(
                TitleSpecifications.matching("no such keyword whatsoever crt", null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(found.getContent()).isEmpty();
        assertThat(found.getTotalElements()).isZero();
    }

    @Test
    void searchPaginatesResultsConsistently() {
        String sharedKeyword = "Pagination Probe CRT";
        Title first = persistTitle(sharedKeyword + " 1", null, Language.EN, TitleStatus.ACTIVE);
        Title second = persistTitle(sharedKeyword + " 2", null, Language.EN, TitleStatus.ACTIVE);
        Title third = persistTitle(sharedKeyword + " 3", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        Page<Title> firstPage = titleRepository.findAll(
                TitleSpecifications.matching("pagination probe crt", null, null, null, null), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent()).extracting(Title::getId)
                .isSubsetOf(first.getId(), second.getId(), third.getId());
    }

    // ---------------------------------------------------------------
    // AuthorRepository.findByFullNameContainingIgnoreCase
    // ---------------------------------------------------------------

    @Test
    void findsAuthorByPartialNameCaseInsensitively() {
        Author author = persistAuthor("Ursula K. Le Guin");
        entityManager.flush();

        Page<Author> found = authorRepository.findByFullNameContainingIgnoreCase("le guin", PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(Author::getId).containsExactly(author.getId());
    }

    @Test
    void findByFullNameReturnsEmptyPageWhenNoAuthorMatches() {
        persistAuthor("Isaac Asimov");
        entityManager.flush();

        Page<Author> found = authorRepository.findByFullNameContainingIgnoreCase(
                "no such author crt", PageRequest.of(0, 20));

        assertThat(found.getContent()).isEmpty();
    }

    // ---------------------------------------------------------------
    // AuthorRepository.findByTitleIdOrderByFullNameAscIdAsc (DEV-06.4)
    // ---------------------------------------------------------------

    @Test
    void findsMultipleAuthorsOfATitleOrderedByFullName() {
        Title title = persistTitle("Multi-author Work CRT", null, Language.EN, TitleStatus.ACTIVE);
        Author zed = persistAuthor("Zed Author CRT");
        Author anna = persistAuthor("Anna Author CRT");
        linkAuthor(title, zed);
        linkAuthor(title, anna);
        entityManager.flush();

        List<Author> found = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(title.getId());

        assertThat(found).extracting(Author::getId).containsExactly(anna.getId(), zed.getId());
    }

    @Test
    void findByTitleIdReturnsEmptyListWhenTitleHasNoAuthors() {
        Title title = persistTitle("Authorless Work CRT", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(authorRepository.findByTitleIdOrderByFullNameAscIdAsc(title.getId())).isEmpty();
    }

    @Test
    void findsAuthorsByTitleIdExcludesAuthorsOfOtherTitles() {
        Title targetTitle = persistTitle("Target Title CRT Authors", null, Language.EN, TitleStatus.ACTIVE);
        Title otherTitle = persistTitle("Other Title CRT Authors", null, Language.EN, TitleStatus.ACTIVE);
        Author targetAuthor = persistAuthor("Target Author CRT");
        Author otherAuthor = persistAuthor("Other Author CRT");
        linkAuthor(targetTitle, targetAuthor);
        linkAuthor(otherTitle, otherAuthor);
        entityManager.flush();

        List<Author> found = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(targetTitle.getId());

        assertThat(found).extracting(Author::getId).containsExactly(targetAuthor.getId());
    }

    @Test
    void findsAuthorsOfATitleBreaksTiesByIdOnEqualFullName() {
        Title title = persistTitle("Homonym Authors Work CRT", null, Language.EN, TitleStatus.ACTIVE);
        Author first = persistAuthor("Homonym Author CRT");
        Author second = persistAuthor("Homonym Author CRT");
        linkAuthor(title, second);
        linkAuthor(title, first);
        entityManager.flush();

        List<Author> found = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(title.getId());

        assertThat(found).extracting(Author::getId).containsExactly(first.getId(), second.getId());
    }

    // ---------------------------------------------------------------
    // GenreRepository.findByCode
    // ---------------------------------------------------------------

    @Test
    void findsGenreByCodeWhenPresent() {
        Genre genre = persistGenre("SCI-FI-CRT-1", "Science-fiction CRT 1");
        entityManager.flush();

        assertThat(genreRepository.findByCode("SCI-FI-CRT-1"))
                .isPresent()
                .get()
                .extracting(Genre::getId)
                .isEqualTo(genre.getId());
    }

    @Test
    void findByCodeIsEmptyWhenAbsent() {
        assertThat(genreRepository.findByCode("ABSENT-CODE-CRT")).isEmpty();
    }

    // ---------------------------------------------------------------
    // GenreRepository.findByTitleIdOrderByLabelAscIdAsc (DEV-06.4)
    // ---------------------------------------------------------------

    @Test
    void findsMultipleGenresOfATitleOrderedByLabel() {
        Title title = persistTitle("Multi-genre Work CRT Detail", null, Language.FR, TitleStatus.ACTIVE);
        Genre zed = persistGenre("ZED-GENRE-CRT", "Zed Genre CRT");
        Genre anna = persistGenre("ANNA-GENRE-CRT", "Anna Genre CRT");
        linkGenre(title, zed);
        linkGenre(title, anna);
        entityManager.flush();

        List<Genre> found = genreRepository.findByTitleIdOrderByLabelAscIdAsc(title.getId());

        assertThat(found).extracting(Genre::getId).containsExactly(anna.getId(), zed.getId());
    }

    @Test
    void findByTitleIdReturnsEmptyListWhenTitleHasNoGenres() {
        Title title = persistTitle("Genreless Work CRT", null, Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(genreRepository.findByTitleIdOrderByLabelAscIdAsc(title.getId())).isEmpty();
    }

    @Test
    void findsGenresByTitleIdExcludesGenresOfOtherTitles() {
        Title targetTitle = persistTitle("Target Title CRT Genres", null, Language.FR, TitleStatus.ACTIVE);
        Title otherTitle = persistTitle("Other Title CRT Genres", null, Language.FR, TitleStatus.ACTIVE);
        Genre targetGenre = persistGenre("TARGET-GENRE-CRT", "Target Genre CRT");
        Genre otherGenre = persistGenre("OTHER-GENRE-CRT", "Other Genre CRT");
        linkGenre(targetTitle, targetGenre);
        linkGenre(otherTitle, otherGenre);
        entityManager.flush();

        List<Genre> found = genreRepository.findByTitleIdOrderByLabelAscIdAsc(targetTitle.getId());

        assertThat(found).extracting(Genre::getId).containsExactly(targetGenre.getId());
    }

    // ---------------------------------------------------------------
    // CopyRepository.findByTitleIdOrderByInventoryCodeAsc / existsByTitleId
    // ---------------------------------------------------------------

    @Test
    void findsCopiesOfATitleOrderedByInventoryCode() {
        Title title = persistTitle("Multi-copy Work", null, Language.FR, TitleStatus.ACTIVE);
        Copy second = persistCopy(title, "CRT-COPY-002");
        Copy first = persistCopy(title, "CRT-COPY-001");
        entityManager.flush();

        List<Copy> found = copyRepository.findByTitleIdOrderByInventoryCodeAsc(title.getId());

        assertThat(found).extracting(Copy::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void findByTitleIdReturnsEmptyListWhenTitleHasNoCopies() {
        Title title = persistTitle("Copyless Work", null, Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(copyRepository.findByTitleIdOrderByInventoryCodeAsc(title.getId())).isEmpty();
    }

    @Test
    void existsByTitleIdReflectsPresenceAndAbsenceOfCopies() {
        Title withCopy = persistTitle("Work With Copy", null, Language.FR, TitleStatus.ACTIVE);
        Title withoutCopy = persistTitle("Work Without Copy", null, Language.FR, TitleStatus.ACTIVE);
        persistCopy(withCopy, "CRT-COPY-EXISTS-1");
        entityManager.flush();

        assertThat(copyRepository.existsByTitleId(withCopy.getId())).isTrue();
        assertThat(copyRepository.existsByTitleId(withoutCopy.getId())).isFalse();
    }

    // ---------------------------------------------------------------
    // TitleAuthorRepository.findByIdTitleId / TitleGenreRepository.findByIdTitleId (DEV-06.5)
    // ---------------------------------------------------------------

    @Test
    void findsTitleAuthorAssociationsOfATitle() {
        Title title = persistTitle("Title Author Assoc CRT", null, Language.EN, TitleStatus.ACTIVE);
        Author author = persistAuthor("Title Author Assoc Author CRT");
        linkAuthor(title, author);
        entityManager.flush();

        List<TitleAuthor> found = titleAuthorRepository.findByIdTitleId(title.getId());

        assertThat(found).extracting(ta -> ta.getAuthor().getId()).containsExactly(author.getId());
    }

    @Test
    void findByIdTitleIdReturnsEmptyListWhenTitleAuthorHasNoAssociations() {
        Title title = persistTitle("Title Author No Assoc CRT", null, Language.EN, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(titleAuthorRepository.findByIdTitleId(title.getId())).isEmpty();
    }

    @Test
    void findsTitleGenreAssociationsOfATitle() {
        Title title = persistTitle("Title Genre Assoc CRT", null, Language.FR, TitleStatus.ACTIVE);
        Genre genre = persistGenre("TITLE-GENRE-ASSOC-CRT", "Title Genre Assoc CRT");
        linkGenre(title, genre);
        entityManager.flush();

        List<TitleGenre> found = titleGenreRepository.findByIdTitleId(title.getId());

        assertThat(found).extracting(tg -> tg.getGenre().getId()).containsExactly(genre.getId());
    }

    @Test
    void findByIdTitleIdReturnsEmptyListWhenTitleGenreHasNoAssociations() {
        Title title = persistTitle("Title Genre No Assoc CRT", null, Language.FR, TitleStatus.ACTIVE);
        entityManager.flush();

        assertThat(titleGenreRepository.findByIdTitleId(title.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private Title persistTitle(String title, String isbn, Language language, TitleStatus titleStatus) {
        Title entity = new Title();
        entity.setTitle(title);
        entity.setIsbn(isbn);
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
