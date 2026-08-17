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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Gestion staff minimale d'{@code Author} et {@code Genre} (DEV-06.5.1,
 * {@code CATALOGUE_MANAGE}) : rattacher des Authors/Genres existants à un
 * Title (DEV-06.5) exigeait déjà qu'ils puissent être créés/consultés/
 * corrigés côté staff — l'audit initial DEV-06.1 §L classait ce choix
 * (endpoints dédiés vs gestion inline) comme IMPLEMENTATION FREEDOM,
 * tranchée ici en faveur d'endpoints dédiés minimaux, symétriques entre les
 * deux entités.
 *
 * <p>Service dédié séparé de {@link CatalogueService} (Title) : les deux
 * domaines (agrégat principal vs entités de référence Author/Genre) n'ont
 * aucun couplage de logique, et {@code CatalogueService} atteignait déjà une
 * taille substantielle avec le seul périmètre Title (lecture publique +
 * staff + écriture, DEV-06.4/06.5) — les y ajouter aurait mélangé trois
 * responsabilités distinctes dans une seule classe.
 *
 * <p>Aucun {@code DELETE} (ni ici ni sur Title, K.1 FIGÉE) : {@code Author}/
 * {@code Genre} n'ont pas de statut de retrait dans la baseline, un
 * {@code DELETE} sortirait du contrat minimal explicitement fermé par la
 * mission DEV-06.5.1.
 */
@Service
public class CatalogueManagementService {

    private static final String AUTHOR_NOT_FOUND_CODE = "AUTHOR_NOT_FOUND";
    private static final String GENRE_NOT_FOUND_CODE = "GENRE_NOT_FOUND";

    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;

    public CatalogueManagementService(AuthorRepository authorRepository, GenreRepository genreRepository) {
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
    }

    // ---------------------------------------------------------------
    // Author
    // ---------------------------------------------------------------

    /**
     * {@code query} absent/blanc → {@code findAll(Pageable)} hérité (aucun
     * filtre) ; sinon {@link AuthorRepository#findByFullNameContainingIgnoreCase}
     * (DEV-06.4, jusque-là jamais appelée sans terme de recherche).
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<AuthorResponse> searchAuthors(String query, Pageable pageable) {
        Page<Author> page = (query == null || query.isBlank())
                ? authorRepository.findAll(pageable)
                : authorRepository.findByFullNameContainingIgnoreCase(query, pageable);
        return page.map(AuthorResponse::from);
    }

    /**
     * {@code fullName} jamais traité comme unique — aucune vérification
     * d'homonymie, aucun refus, aucune fusion. Seule contrainte vérifiée :
     * {@code birthDate <= deathDate} si les deux sont fournies.
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public AuthorResponse createAuthor(CreateAuthorRequest request) {
        requireCoherentDates(request.birthDate(), request.deathDate());

        Author author = new Author();
        author.setFullName(request.fullName());
        author.setBirthDate(request.birthDate());
        author.setDeathDate(request.deathDate());
        author.setNationality(request.nationality());
        author.setBiography(request.biography());
        authorRepository.save(author);

        return AuthorResponse.from(author);
    }

    /**
     * PATCH sparse (cf. {@link UpdateAuthorRequest}). La cohérence des dates
     * est revérifiée sur l'état final résultant (après application des
     * mutations demandées), pas uniquement sur les valeurs individuellement
     * fournies.
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public AuthorResponse updateAuthor(Long authorId, UpdateAuthorRequest request) {
        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AUTHOR_NOT_FOUND_CODE, "Aucun auteur pour l'identifiant " + authorId + "."));

        if (request.isFullNamePresent()) {
            if (request.getFullName() == null || request.getFullName().isBlank()) {
                throw new BusinessRuleException(
                        "AUTHOR_FULL_NAME_MUST_NOT_BE_BLANK", "fullName ne peut pas être vide.");
            }
            author.setFullName(request.getFullName());
        }
        if (request.isBirthDatePresent()) {
            author.setBirthDate(request.getBirthDate());
        }
        if (request.isDeathDatePresent()) {
            author.setDeathDate(request.getDeathDate());
        }
        if (request.isNationalityPresent()) {
            author.setNationality(request.getNationality());
        }
        if (request.isBiographyPresent()) {
            author.setBiography(request.getBiography());
        }
        requireCoherentDates(author.getBirthDate(), author.getDeathDate());

        return AuthorResponse.from(author);
    }

    private static void requireCoherentDates(LocalDate birthDate, LocalDate deathDate) {
        if (birthDate != null && deathDate != null && birthDate.isAfter(deathDate)) {
            throw new BusinessRuleException(
                    "AUTHOR_BIRTH_DATE_AFTER_DEATH_DATE", "birthDate ne peut pas être postérieure à deathDate.");
        }
    }

    // ---------------------------------------------------------------
    // Genre
    // ---------------------------------------------------------------

    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<GenreResponse> listGenres(Pageable pageable) {
        return genreRepository.findAll(pageable).map(GenreResponse::from);
    }

    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public GenreResponse createGenre(CreateGenreRequest request) {
        if (genreRepository.existsByCode(request.code())) {
            throw new ConflictException("GENRE_CODE_ALREADY_EXISTS", "Un genre existe déjà avec ce code.");
        }
        if (genreRepository.existsByLabel(request.label())) {
            throw new ConflictException("GENRE_LABEL_ALREADY_EXISTS", "Un genre existe déjà avec ce label.");
        }

        Genre genre = new Genre();
        genre.setCode(request.code());
        genre.setLabel(request.label());
        genre.setDescription(request.description());
        genreRepository.save(genre);

        return GenreResponse.from(genre);
    }

    /**
     * PATCH sparse (cf. {@link UpdateGenreRequest}). Unicité revérifiée
     * uniquement si {@code code}/{@code label} change réellement de valeur
     * — jamais un conflit avec l'entité courante elle-même.
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public GenreResponse updateGenre(Long genreId, UpdateGenreRequest request) {
        Genre genre = genreRepository.findById(genreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GENRE_NOT_FOUND_CODE, "Aucun genre pour l'identifiant " + genreId + "."));

        if (request.isCodePresent()) {
            if (request.getCode() == null || request.getCode().isBlank()) {
                throw new BusinessRuleException("GENRE_CODE_MUST_NOT_BE_BLANK", "code ne peut pas être vide.");
            }
            if (!request.getCode().equals(genre.getCode()) && genreRepository.existsByCode(request.getCode())) {
                throw new ConflictException("GENRE_CODE_ALREADY_EXISTS", "Un genre existe déjà avec ce code.");
            }
            genre.setCode(request.getCode());
        }
        if (request.isLabelPresent()) {
            if (request.getLabel() == null || request.getLabel().isBlank()) {
                throw new BusinessRuleException("GENRE_LABEL_MUST_NOT_BE_BLANK", "label ne peut pas être vide.");
            }
            if (!request.getLabel().equals(genre.getLabel()) && genreRepository.existsByLabel(request.getLabel())) {
                throw new ConflictException("GENRE_LABEL_ALREADY_EXISTS", "Un genre existe déjà avec ce label.");
            }
            genre.setLabel(request.getLabel());
        }
        if (request.isDescriptionPresent()) {
            genre.setDescription(request.getDescription());
        }

        return GenreResponse.from(genre);
    }
}
