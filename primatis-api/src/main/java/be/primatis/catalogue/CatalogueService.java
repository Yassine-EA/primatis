package be.primatis.catalogue;

import be.primatis.catalogue.dto.CreateTitleRequest;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
import be.primatis.catalogue.dto.UpdateTitleRequest;
import be.primatis.catalogue.dto.UpdateTitleStatusRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Consultation publique (DEV-06.4) et gestion staff (DEV-06.5) du catalogue
 * {@code Title}. Même précédent que {@code UserService} : un seul Service
 * couvrant plusieurs niveaux d'autorisation, {@code @PreAuthorize} appliqué
 * par méthode (jamais au Controller), jamais au niveau classe.
 *
 * <p>Lectures publiques ({@code searchPublicTitles}/{@code getPublicTitleById})
 * : aucun {@code @PreAuthorize} — volontairement {@code permitAll}
 * ({@code SecurityConfig}, DEV-03.8), un utilisateur authentifié voyant
 * exactement la même surface qu'un Visitor (K.2, résolu DEV-06.4).
 * {@code TitleStatus.ACTIVE} y est imposé par ce Service, jamais par
 * l'appelant.
 *
 * <p>Lectures/écritures staff ({@code searchStaffTitles}/{@code
 * getStaffTitleById}/{@code createTitle}/{@code updateTitle}/{@code
 * updateTitleStatus}) : {@code @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")}.
 * La surface staff peut voir {@code WITHDRAWN} (DEV-06.5 §8) — les deux
 * lectures publiques restent strictement inchangées.
 *
 * <p>K.1 (audit DEV-06.1, FIGÉ DEV-06.5) : aucune suppression physique de
 * {@code Title} en V1 — seul le cycle {@code ACTIVE ↔ WITHDRAWN} existe.
 * Aucune méthode {@code deleteTitle} n'est donc introduite ici.
 */
@Service
public class CatalogueService {

    private static final String TITLE_NOT_FOUND_CODE = "TITLE_NOT_FOUND";
    private static final String AUTHOR_NOT_FOUND_CODE = "AUTHOR_NOT_FOUND";
    private static final String GENRE_NOT_FOUND_CODE = "GENRE_NOT_FOUND";

    private final TitleRepository titleRepository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final TitleAuthorRepository titleAuthorRepository;
    private final TitleGenreRepository titleGenreRepository;
    private final Clock clock;

    public CatalogueService(
            TitleRepository titleRepository,
            AuthorRepository authorRepository,
            GenreRepository genreRepository,
            TitleAuthorRepository titleAuthorRepository,
            TitleGenreRepository titleGenreRepository,
            Clock clock) {
        this.titleRepository = titleRepository;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
        this.titleAuthorRepository = titleAuthorRepository;
        this.titleGenreRepository = titleGenreRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------
    // Consultation publique (DEV-06.4) — inchangée
    // ---------------------------------------------------------------

    /**
     * Recherche paginée de Titles {@code ACTIVE} uniquement. {@code query}/
     * {@code genreCode} : {@code null}/blanc traités comme "aucun filtre" par
     * {@link TitleSpecifications#matching} elle-même — aucune normalisation
     * dupliquée ici. Mapping {@link TitleResponse#from} par élément : aucun
     * Author/Genre/Copy chargé pour la liste (contrat DEV-06.3 §9, aucun
     * N+1).
     */
    @Transactional(readOnly = true)
    public Page<TitleResponse> searchPublicTitles(
            String query, Long authorId, String genreCode, Language language, Pageable pageable) {
        var specification = TitleSpecifications.matching(query, authorId, genreCode, language, TitleStatus.ACTIVE);
        return titleRepository.findAll(specification, pageable).map(TitleResponse::from);
    }

    /**
     * Détail public d'un Title. {@code Title} inexistant OU {@code WITHDRAWN}
     * → même {@code 404 TITLE_NOT_FOUND} (contrat DEV-06.4 §5 : la surface
     * publique ne distingue pas les deux cas, jamais 403/409, jamais un
     * Title {@code WITHDRAWN} exposé avec {@code 200}). Authors/Genres
     * chargés en une requête chacun ({@link AuthorRepository},
     * {@link GenreRepository}) — aucune {@code Copy} chargée, aucune
     * disponibilité calculée.
     */
    @Transactional(readOnly = true)
    public TitleDetailResponse getPublicTitleById(Long titleId) {
        Title title = titleRepository.findById(titleId)
                .filter(candidate -> candidate.getTitleStatus() == TitleStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TITLE_NOT_FOUND_CODE, "Aucun titre disponible pour l'identifiant " + titleId + "."));
        List<Author> authors = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);
        List<Genre> genres = genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);
        return TitleDetailResponse.from(title, authors, genres);
    }

    // ---------------------------------------------------------------
    // Consultation staff (DEV-06.5, CATALOGUE_MANAGE)
    // ---------------------------------------------------------------

    /**
     * Recherche paginée staff : {@code titleStatus} contrôlé par l'appelant
     * (contrairement à la recherche publique). {@code null} → aucun filtre
     * de statut (ACTIVE et WITHDRAWN tous deux visibles), comportement déjà
     * natif de {@link TitleSpecifications#matching} — aucune requête
     * supplémentaire nécessaire.
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<TitleResponse> searchStaffTitles(
            String query, Long authorId, String genreCode, Language language, TitleStatus titleStatus,
            Pageable pageable) {
        var specification = TitleSpecifications.matching(query, authorId, genreCode, language, titleStatus);
        return titleRepository.findAll(specification, pageable).map(TitleResponse::from);
    }

    /**
     * Détail staff : visible quel que soit {@code TitleStatus} (contrairement
     * au détail public). Aucune {@code Copy} chargée (DEV-06.6).
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public TitleDetailResponse getStaffTitleById(Long titleId) {
        Title title = getRequiredTitle(titleId);
        List<Author> authors = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);
        List<Genre> genres = genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);
        return TitleDetailResponse.from(title, authors, genres);
    }

    // ---------------------------------------------------------------
    // Gestion staff (DEV-06.5, CATALOGUE_MANAGE)
    // ---------------------------------------------------------------

    /**
     * Création staff. {@code TitleStatus.ACTIVE} imposé backend (jamais
     * fourni par le client). {@code authorIds} vide/{@code null} refusé
     * explicitement ici (409) — garantie côté Service de l'invariant ≥1
     * Author, indépendante de {@code @NotEmpty} sur {@link CreateTitleRequest}
     * (défense en profondeur, pas une duplication superflue). Résout
     * {@code authorIds}/{@code genreIds} (404 si un identifiant est inconnu)
     * avant toute mutation — aucune création automatique d'Author/Genre à
     * partir d'un nom. Une seule transaction couvre {@code Title} et ses
     * associations : un identifiant invalide empêche toute mutation
     * partielle.
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public TitleDetailResponse createTitle(CreateTitleRequest request) {
        if (request.authorIds() == null || request.authorIds().isEmpty()) {
            throw new BusinessRuleException(
                    "AUTHOR_IDS_MUST_NOT_BE_EMPTY", "authorIds ne peut pas être vide : un Title doit avoir au moins un Author.");
        }
        String normalizedIsbn = normalizeIsbn(request.isbn());
        if (normalizedIsbn != null && titleRepository.existsByIsbn(normalizedIsbn)) {
            throw new ConflictException("ISBN_ALREADY_EXISTS", "Un titre existe déjà avec cet ISBN.");
        }

        List<Author> authors = resolveAuthors(request.authorIds());
        List<Genre> genres = resolveGenres(request.genreIds());

        Instant now = clock.instant();
        Title title = new Title();
        title.setIsbn(normalizedIsbn);
        title.setTitle(request.title());
        title.setSubtitle(request.subtitle());
        title.setSummary(request.summary());
        title.setPublicationYear(request.publicationYear());
        title.setLanguage(request.language());
        title.setPageCount(request.pageCount());
        title.setPublisher(request.publisher());
        title.setCoverImageUrl(request.coverImageUrl());
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(now);
        title.setUpdatedAt(now);
        titleRepository.save(title);

        replaceAuthors(title, authors);
        replaceGenres(title, genres);

        return TitleDetailResponse.from(title, authors, genres);
    }

    /**
     * Modification staff (PATCH sparse, même mécanisme que {@code
     * UpdateUserRequest}/{@code UserService.updateUser}, DEV-05.6). Ne
     * touche jamais {@code titleStatus} (action dédiée {@link
     * #updateTitleStatus}). {@code title}/{@code language} : présent+{@code
     * null} refusé (colonnes {@code NOT NULL}). Champs nullable restants :
     * présent+{@code null} efface. {@code authorIds} fourni : ne doit
     * jamais devenir vide (invariant ≥1 Author). {@code genreIds} fourni,
     * même vide : remplace intégralement l'ensemble (Genre non obligatoire).
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public TitleDetailResponse updateTitle(Long titleId, UpdateTitleRequest request) {
        Title title = getRequiredTitle(titleId);
        boolean mutated = false;

        if (request.isIsbnPresent()) {
            String normalizedIsbn = normalizeIsbn(request.getIsbn());
            if (normalizedIsbn != null && !normalizedIsbn.equals(title.getIsbn())
                    && titleRepository.existsByIsbn(normalizedIsbn)) {
                throw new ConflictException("ISBN_ALREADY_EXISTS", "Un titre existe déjà avec cet ISBN.");
            }
            title.setIsbn(normalizedIsbn);
            mutated = true;
        }
        if (request.isTitlePresent()) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BusinessRuleException("TITLE_MUST_NOT_BE_BLANK", "title ne peut pas être vide.");
            }
            title.setTitle(request.getTitle());
            mutated = true;
        }
        if (request.isSubtitlePresent()) {
            title.setSubtitle(request.getSubtitle());
            mutated = true;
        }
        if (request.isSummaryPresent()) {
            title.setSummary(request.getSummary());
            mutated = true;
        }
        if (request.isPublicationYearPresent()) {
            title.setPublicationYear(request.getPublicationYear());
            mutated = true;
        }
        if (request.isLanguagePresent()) {
            if (request.getLanguage() == null) {
                throw new BusinessRuleException("LANGUAGE_MUST_NOT_BE_NULL", "language ne peut pas être effacé.");
            }
            title.setLanguage(request.getLanguage());
            mutated = true;
        }
        if (request.isPageCountPresent()) {
            if (request.getPageCount() != null && request.getPageCount() <= 0) {
                throw new BusinessRuleException("PAGE_COUNT_MUST_BE_POSITIVE", "pageCount doit être > 0.");
            }
            title.setPageCount(request.getPageCount());
            mutated = true;
        }
        if (request.isPublisherPresent()) {
            title.setPublisher(request.getPublisher());
            mutated = true;
        }
        if (request.isCoverImageUrlPresent()) {
            title.setCoverImageUrl(request.getCoverImageUrl());
            mutated = true;
        }

        List<Author> authors = null;
        if (request.getAuthorIds() != null) {
            if (request.getAuthorIds().isEmpty()) {
                throw new BusinessRuleException(
                        "AUTHOR_IDS_MUST_NOT_BE_EMPTY", "authorIds ne peut pas être vide : un Title garde au moins un Author.");
            }
            authors = resolveAuthors(request.getAuthorIds());
            replaceAuthors(title, authors);
            mutated = true;
        }
        List<Genre> genres = null;
        if (request.getGenreIds() != null) {
            genres = resolveGenres(request.getGenreIds());
            replaceGenres(title, genres);
            mutated = true;
        }

        if (mutated) {
            title.setUpdatedAt(clock.instant());
        }

        List<Author> finalAuthors = authors != null ? authors : authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);
        List<Genre> finalGenres = genres != null ? genres : genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);
        return TitleDetailResponse.from(title, finalAuthors, finalGenres);
    }

    /**
     * Transition {@code TitleStatus} ({@code ACTIVE ↔ WITHDRAWN}, K.1 FIGÉE
     * DEV-06.5 : aucune suppression physique). Idempotent : appliquer le
     * statut déjà courant est un succès sans effet de bord (même contrat que
     * {@code UserService.updateAccountStatus}, DEV-05.7).
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public TitleDetailResponse updateTitleStatus(Long titleId, UpdateTitleStatusRequest request) {
        Title title = getRequiredTitle(titleId);
        if (title.getTitleStatus() != request.status()) {
            title.setTitleStatus(request.status());
            title.setUpdatedAt(clock.instant());
        }
        List<Author> authors = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);
        List<Genre> genres = genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);
        return TitleDetailResponse.from(title, authors, genres);
    }

    // ---------------------------------------------------------------
    // Utilitaires privés
    // ---------------------------------------------------------------

    private Title getRequiredTitle(Long titleId) {
        return titleRepository.findById(titleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TITLE_NOT_FOUND_CODE, "Aucun titre pour l'identifiant " + titleId + "."));
    }

    private List<Author> resolveAuthors(List<Long> authorIds) {
        List<Author> authors = new ArrayList<>();
        for (Long authorId : authorIds) {
            authors.add(authorRepository.findById(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            AUTHOR_NOT_FOUND_CODE, "Aucun auteur pour l'identifiant " + authorId + ".")));
        }
        return authors;
    }

    private List<Genre> resolveGenres(List<Long> genreIds) {
        if (genreIds == null) {
            return List.of();
        }
        List<Genre> genres = new ArrayList<>();
        for (Long genreId : genreIds) {
            genres.add(genreRepository.findById(genreId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            GENRE_NOT_FOUND_CODE, "Aucun genre pour l'identifiant " + genreId + ".")));
        }
        return genres;
    }

    private static String normalizeIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        String trimmed = isbn.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Remplace intégralement les associations {@code TitleAuthor} d'un
     * Title par {@code targetAuthors} : supprime les associations absentes
     * du nouvel ensemble, ajoute celles qui manquent — même précédent exact
     * que {@code UserService.updateRoles} (DEV-05.6) côté {@code UserRole}.
     * Réutilisée telle quelle par {@link #createTitle} (aucune association
     * existante, donc uniquement des ajouts) et {@link #updateTitle}.
     */
    private void replaceAuthors(Title title, List<Author> targetAuthors) {
        List<TitleAuthor> currentAssociations = titleAuthorRepository.findByIdTitleId(title.getId());
        Set<Long> currentAuthorIds = new HashSet<>();
        for (TitleAuthor association : currentAssociations) {
            currentAuthorIds.add(association.getAuthor().getId());
        }
        Set<Long> targetAuthorIds = new HashSet<>();
        for (Author author : targetAuthors) {
            targetAuthorIds.add(author.getId());
        }

        for (TitleAuthor association : currentAssociations) {
            if (!targetAuthorIds.contains(association.getAuthor().getId())) {
                titleAuthorRepository.delete(association);
            }
        }
        for (Author author : targetAuthors) {
            if (!currentAuthorIds.contains(author.getId())) {
                TitleAuthor newAssociation = new TitleAuthor();
                newAssociation.setId(new TitleAuthorId(title.getId(), author.getId()));
                newAssociation.setTitle(title);
                newAssociation.setAuthor(author);
                titleAuthorRepository.save(newAssociation);
            }
        }
    }

    /**
     * Même mécanisme exact que {@link #replaceAuthors}, côté {@code Genre} —
     * {@code targetGenres} peut être vide (Genre non obligatoire).
     */
    private void replaceGenres(Title title, List<Genre> targetGenres) {
        List<TitleGenre> currentAssociations = titleGenreRepository.findByIdTitleId(title.getId());
        Set<Long> currentGenreIds = new HashSet<>();
        for (TitleGenre association : currentAssociations) {
            currentGenreIds.add(association.getGenre().getId());
        }
        Set<Long> targetGenreIds = new HashSet<>();
        for (Genre genre : targetGenres) {
            targetGenreIds.add(genre.getId());
        }

        for (TitleGenre association : currentAssociations) {
            if (!targetGenreIds.contains(association.getGenre().getId())) {
                titleGenreRepository.delete(association);
            }
        }
        for (Genre genre : targetGenres) {
            if (!currentGenreIds.contains(genre.getId())) {
                TitleGenre newAssociation = new TitleGenre();
                newAssociation.setId(new TitleGenreId(genre.getId(), title.getId()));
                newAssociation.setTitle(title);
                newAssociation.setGenre(genre);
                titleGenreRepository.save(newAssociation);
            }
        }
    }
}
