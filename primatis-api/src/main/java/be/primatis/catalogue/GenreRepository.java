package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
