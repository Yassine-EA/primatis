package be.primatis.setting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, Long> {

    /**
     * setting_key est l'unique clé d'accès aux paramètres métier globaux
     * (database-model.md §15.4).
     */
    Optional<ApplicationSetting> findBySettingKey(String settingKey);
}
