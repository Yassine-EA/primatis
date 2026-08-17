package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    /**
     * Résolution par code métier ({@code genre.code}, {@code UNIQUE}) —
     * même précédent que {@code RoleRepository.findByCode} (DEV-03) : requis
     * pour rattacher un Genre existant à un Title par son code plutôt que par
     * son identifiant technique interne (DEV-06.5), et pour le filtre
     * "genre" de la recherche catalogue (DEV-06.4, {@link TitleSpecifications}).
     */
    Optional<Genre> findByCode(String code);

    /**
     * Pré-vérification d'unicité avant création/modification (DEV-06.5.1,
     * {@code CatalogueManagementService}) — même précédent que {@code
     * TitleRepository.existsByIsbn} : permet de refuser proprement un
     * doublon (409 {@code GENRE_CODE_ALREADY_EXISTS}) plutôt que de laisser
     * remonter {@code uq_genre_code} comme {@code DataIntegrityViolationException}
     * brute.
     */
    boolean existsByCode(String code);

    /**
     * Même précédent que {@link #existsByCode}, pour {@code uq_genre_label}
     * (409 {@code GENRE_LABEL_ALREADY_EXISTS}).
     */
    boolean existsByLabel(String label);

    /**
     * Genres d'un Title, en une seule requête (DEV-06.4, consultation
     * détaillée — même besoin/même construction que
     * {@code AuthorRepository.findByTitleIdOrderByFullNameAscIdAsc}, cf. sa
     * Javadoc pour la justification complète). Aucune duplication possible
     * (clé composite {@code (genre_id, title_id)} de {@code title_genre}).
     * Ordre déterministe ({@code label ASC, id ASC}) — IMPLEMENTATION
     * FREEDOM. Le second critère ({@code id}) reste un ordre secondaire
     * défensif ; il n'est pas exerçable par un cas de test réel, {@code
     * genre.label} étant {@code UNIQUE} en base (contrairement à {@code
     * author.fullName}, non unique — cf. {@code AuthorRepository}).
     */
    @Query("SELECT tg.genre FROM TitleGenre tg WHERE tg.title.id = :titleId "
            + "ORDER BY tg.genre.label ASC, tg.genre.id ASC")
    List<Genre> findByTitleIdOrderByLabelAscIdAsc(@Param("titleId") Long titleId);
}
