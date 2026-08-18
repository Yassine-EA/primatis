package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CopyRepository extends JpaRepository<Copy, Long> {

    /**
     * inventoryCode est l'identifiant fonctionnel de l'exemplaire
     * (database-model.md §25.3 : ne pas le remplacer par l'ID technique).
     */
    Optional<Copy> findByInventoryCode(String inventoryCode);

    /**
     * Pré-vérification d'unicité avant création/modification (DEV-06.6,
     * {@code CopyService}) — même précédent que {@code
     * TitleRepository.existsByIsbn}/{@code GenreRepository.existsByCode} :
     * refuse proprement un doublon (409 {@code INVENTORY_CODE_ALREADY_EXISTS})
     * plutôt que de laisser remonter {@code uq_copy_inventory_code} comme
     * {@code DataIntegrityViolationException} brute.
     */
    boolean existsByInventoryCode(String inventoryCode);

    /**
     * Scoping Title/Copy en une seule requête (DEV-06.6, invariant de
     * sécurité fonctionnelle §34 : {@code /{titleId}/copies/{copyId}} doit
     * vérifier {@code copy.title.id == titleId}) — encode le scoping
     * directement dans la requête plutôt qu'un {@code findById()} suivi
     * d'une comparaison manuelle, oubliable à chaque nouveau point d'appel.
     * Absent si le Copy n'existe pas, ou existe mais appartient à un autre
     * Title — les deux cas produisent {@code 404 COPY_NOT_FOUND},
     * volontairement indifférenciés (ne jamais révéler qu'un {@code copyId}
     * existe sous un autre Title).
     */
    Optional<Copy> findByIdAndTitleId(Long id, Long titleId);

    /**
     * Exemplaires d'un Title (DEV-06.4 détail catalogue, DEV-06.6 gestion
     * exemplaires) — collection naturellement bornée par le modèle métier
     * (profil de seeding cible : 1 à 5 Copies par Title), aucune pagination
     * nécessaire. Ordre par {@code inventoryCode} (identifiant fonctionnel
     * affiché, pas l'ID technique) — IMPLEMENTATION FREEDOM, aucune règle
     * métier ne fixe cet ordre.
     */
    List<Copy> findByTitleIdOrderByInventoryCodeAsc(Long titleId);

    /**
     * Existence d'au moins un Copy pour un Title — primitive générique
     * (aucune règle de suppression encodée ici), utile indépendamment de
     * l'arbitrage K.1 (audit DEV-06.1) : affichage d'un état vide catalogue
     * autant qu'une future vérification d'éligibilité à la suppression, le
     * cas échéant.
     */
    boolean existsByTitleId(Long titleId);
}
