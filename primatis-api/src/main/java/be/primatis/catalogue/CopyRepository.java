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
