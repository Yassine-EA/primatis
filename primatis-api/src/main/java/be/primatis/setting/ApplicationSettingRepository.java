package be.primatis.setting;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, Long> {

    /**
     * setting_key est l'unique clé d'accès aux paramètres métier globaux
     * (database-model.md §15.4).
     */
    Optional<ApplicationSetting> findBySettingKey(String settingKey);

    /**
     * Consultation administrative (DEV-12.2, {@code GET /api/v1/settings}) :
     * ordre lexical de {@code settingKey}, déterministe (aucun ordre métier
     * imposé par les sources — DEV-12.1 §22.4, IMPLEMENTATION FREEDOM).
     * {@code @EntityGraph} sur {@code updatedByUser} : évite un N+1 une fois
     * que plusieurs settings ont déjà été modifiés au moins une fois — même
     * précédent que {@code ArticleRepository#findByArticleStatus}. Collection
     * fixe à six lignes : jamais paginée (backend.md « Pagination »).
     */
    @EntityGraph(attributePaths = "updatedByUser")
    List<ApplicationSetting> findAllByOrderBySettingKeyAsc();
}
