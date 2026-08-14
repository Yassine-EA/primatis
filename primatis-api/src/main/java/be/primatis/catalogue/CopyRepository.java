package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CopyRepository extends JpaRepository<Copy, Long> {

    /**
     * inventoryCode est l'identifiant fonctionnel de l'exemplaire
     * (database-model.md §25.3 : ne pas le remplacer par l'ID technique).
     */
    Optional<Copy> findByInventoryCode(String inventoryCode);
}
