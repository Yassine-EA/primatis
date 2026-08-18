package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * {@code JpaSpecificationExecutor} porte la recherche catalogue multi-filtres
 * combinables (DEV-06.2, {@link TitleSpecifications}) : chaque filtre absent
 * (valeur Java {@code null}) ne produit aucun prédicat ni aucun paramètre lié
 * dans la requête générée, ce qui évite structurellement tout risque de
 * paramètre lié uniquement à une comparaison {@code IS NULL} — la même classe
 * de problème PostgreSQL/JDBC déjà rencontrée et documentée dans
 * {@link be.primatis.user.ResidenceRepository#existsOverlappingPeriod}
 * (SQLState {@code 42P18}).
 */
public interface TitleRepository extends JpaRepository<Title, Long>, JpaSpecificationExecutor<Title> {

    /**
     * Recherche exacte par ISBN (identifiant bibliographique unique lorsqu'il
     * est renseigné) — distincte de la recherche multi-filtres : combiner un
     * ISBN connu avec d'autres filtres n'a pas de sens métier (l'ISBN
     * identifie déjà un Title précis).
     */
    Optional<Title> findByIsbn(String isbn);

    /**
     * Pré-vérification d'unicité avant création (DEV-06.5), même précédent
     * que {@code AppUserRepository.existsByEmail}/{@code existsByMemberNumber}
     * (DEV-05.2) : permet au futur Service de refuser proprement un doublon
     * (409 métier) plutôt que de laisser remonter la contrainte
     * {@code uq_title_isbn} comme une {@code DataIntegrityViolationException}
     * brute.
     */
    boolean existsByIsbn(String isbn);
}
