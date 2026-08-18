package be.primatis.setting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link ApplicationSettingService} (DEV-07.5, premier consommateur
 * réel : {@code LOAN_DURATION_DAYS}) contre PostgreSQL réel : lecture de la
 * clé obligatoire réellement bootstrapée par V001, et absence de fallback
 * silencieux (§13.16) pour une clé absente, un {@code value_type} ou un
 * {@code setting_value} incohérent — les trois cas produisent une erreur
 * explicite (jamais une valeur codée en dur), traduite en 500
 * {@code INTERNAL_ERROR} au niveau HTTP (configuration serveur, jamais une
 * faute du client).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationSettingServiceTests {

    @Autowired
    private ApplicationSettingService applicationSettingService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void getIntegerReadsTheRealBootstrappedLoanDurationDays() {
        assertThat(applicationSettingService.getInteger("LOAN_DURATION_DAYS")).isEqualTo(21);
    }

    @Test
    void getIntegerThrowsExplicitlyWhenKeyIsMissing() {
        assertThatThrownBy(() -> applicationSettingService.getInteger("DOES_NOT_EXIST"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOES_NOT_EXIST");
    }

    @Test
    void getIntegerThrowsExplicitlyWhenValueTypeIsNotInteger() {
        persistSetting("A_STRING_SETTING", "hello", "STRING");

        assertThatThrownBy(() -> applicationSettingService.getInteger("A_STRING_SETTING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A_STRING_SETTING")
                .hasMessageContaining("INTEGER");
    }

    @Test
    void getIntegerThrowsExplicitlyWhenSettingValueIsNotParseable() {
        persistSetting("A_BROKEN_INTEGER_SETTING", "not-a-number", "INTEGER");

        assertThatThrownBy(() -> applicationSettingService.getInteger("A_BROKEN_INTEGER_SETTING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A_BROKEN_INTEGER_SETTING");
    }

    private void persistSetting(String key, String value, String valueType) {
        ApplicationSetting setting = new ApplicationSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setValueType(valueType);
        setting.setDescription("Fixture de test");
        setting.setUpdatedAt(Instant.now());
        entityManager.persist(setting);
        entityManager.flush();
    }
}
