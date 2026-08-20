package be.primatis.setting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link ApplicationSettingService} (DEV-07.5, premier consommateur
 * réel : {@code LOAN_DURATION_DAYS} ; DEV-09.3, {@link
 * ApplicationSettingService#getDecimal(String)}, clés {@code
 * FINE_WEEKLY_RATE}/{@code FINE_MAX_AMOUNT}, DEV-DEC-0046) contre PostgreSQL
 * réel : lecture des clés obligatoires réellement bootstrapées par V001/V006,
 * et absence de fallback silencieux (§13.16) pour une clé absente, un
 * {@code value_type} ou un {@code setting_value} incohérent — les trois cas
 * produisent une erreur explicite (jamais une valeur codée en dur), traduite
 * en 500 {@code INTERNAL_ERROR} au niveau HTTP (configuration serveur,
 * jamais une faute du client).
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

    @Test
    void getDecimalReadsTheRealBootstrappedFineWeeklyRate() {
        assertThat(applicationSettingService.getDecimal("FINE_WEEKLY_RATE"))
                .isEqualByComparingTo(new BigDecimal("0.80"));
    }

    @Test
    void getDecimalReadsTheRealBootstrappedFineMaxAmount() {
        assertThat(applicationSettingService.getDecimal("FINE_MAX_AMOUNT"))
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void getDecimalThrowsExplicitlyWhenKeyIsMissing() {
        assertThatThrownBy(() -> applicationSettingService.getDecimal("DOES_NOT_EXIST"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOES_NOT_EXIST");
    }

    @Test
    void getDecimalThrowsExplicitlyWhenValueTypeIsNotDecimal() {
        persistSetting("A_STRING_SETTING_FOR_DECIMAL", "hello", "STRING");

        assertThatThrownBy(() -> applicationSettingService.getDecimal("A_STRING_SETTING_FOR_DECIMAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A_STRING_SETTING_FOR_DECIMAL")
                .hasMessageContaining("DECIMAL");
    }

    @Test
    void getDecimalThrowsExplicitlyWhenSettingValueIsNotParseable() {
        persistSetting("A_BROKEN_DECIMAL_SETTING", "not-a-number", "DECIMAL");

        assertThatThrownBy(() -> applicationSettingService.getDecimal("A_BROKEN_DECIMAL_SETTING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A_BROKEN_DECIMAL_SETTING");
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
