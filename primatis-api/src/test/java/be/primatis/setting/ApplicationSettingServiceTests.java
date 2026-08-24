package be.primatis.setting;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.setting.web.SettingResponse;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    // ---------------------------------------------------------------
    // listSettings (DEV-12.2, SETTING_READ)
    // ---------------------------------------------------------------

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listSettingsWithoutSettingReadAuthorityIsDenied() {
        authenticateWithAuthority("CATALOGUE_READ");

        assertThatThrownBy(() -> applicationSettingService.listSettings())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listSettingsReturnsAllSixBootstrappedSettingsOrderedBySettingKey() {
        authenticateWithAuthority("SETTING_READ");

        List<SettingResponse> settings = applicationSettingService.listSettings();

        assertThat(settings).extracting(SettingResponse::settingKey).containsExactly(
                "FINE_MAX_AMOUNT",
                "FINE_WEEKLY_RATE",
                "LOAN_DUE_SOON_DAYS",
                "LOAN_DURATION_DAYS",
                "MAX_ACTIVE_RESERVATIONS_PER_MEMBER",
                "RESERVATION_READY_HOLD_HOURS");
    }

    @Test
    void listSettingsExposesUpdatedByUserAfterAModification() {
        AppUser actingUser = persistActingUser("setting-service-list-updatedby@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");
        applicationSettingService.updateSettingValue("RESERVATION_READY_HOLD_HOURS", "72", actingUser.getId());

        authenticateWithAuthority("SETTING_READ");
        List<SettingResponse> settings = applicationSettingService.listSettings();

        SettingResponse updated = settings.stream()
                .filter(s -> s.settingKey().equals("RESERVATION_READY_HOLD_HOURS"))
                .findFirst().orElseThrow();
        assertThat(updated.settingValue()).isEqualTo("72");
        assertThat(updated.updatedByUser()).isNotNull();
        assertThat(updated.updatedByUser().id()).isEqualTo(actingUser.getId());
    }

    // ---------------------------------------------------------------
    // updateSettingValue (DEV-12.2, SETTING_MANAGE)
    // ---------------------------------------------------------------

    @Test
    void updateSettingValueWithoutSettingManageAuthorityIsDenied() {
        authenticateWithAuthority("SETTING_READ");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue("LOAN_DUE_SOON_DAYS", "5", 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateSettingValueUpdatesIntegerValueAndSetsAudit() {
        AppUser actingUser = persistActingUser("setting-service-update-integer@primatis.test");
        entityManager.flush();
        Instant before = applicationSettingUpdatedAtForTest("LOAN_DUE_SOON_DAYS");
        authenticateWithAuthority("SETTING_MANAGE");

        SettingResponse result = applicationSettingService.updateSettingValue(
                "LOAN_DUE_SOON_DAYS", "5", actingUser.getId());

        assertThat(result.settingValue()).isEqualTo("5");
        assertThat(result.settingKey()).isEqualTo("LOAN_DUE_SOON_DAYS");
        assertThat(result.valueType()).isEqualTo("INTEGER");
        assertThat(result.updatedAt()).isAfter(before);
        assertThat(result.updatedByUser().id()).isEqualTo(actingUser.getId());
    }

    @Test
    void updateSettingValueUpdatesDecimalValueAndSetsAudit() {
        AppUser actingUser = persistActingUser("setting-service-update-decimal@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        SettingResponse result = applicationSettingService.updateSettingValue(
                "FINE_WEEKLY_RATE", "1.25", actingUser.getId());

        assertThat(new BigDecimal(result.settingValue())).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(result.valueType()).isEqualTo("DECIMAL");
        assertThat(result.updatedByUser().id()).isEqualTo(actingUser.getId());
    }

    @Test
    void updateSettingValueTrimsSurroundingWhitespaceBeforeValidatingAndPersisting() {
        AppUser actingUser = persistActingUser("setting-service-update-trim@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        SettingResponse result = applicationSettingService.updateSettingValue(
                "MAX_ACTIVE_RESERVATIONS_PER_MEMBER", "  15  ", actingUser.getId());

        assertThat(result.settingValue()).isEqualTo("15");
    }

    @Test
    void updateSettingValueDoesNotModifySettingKeyValueTypeOrDescription() {
        AppUser actingUser = persistActingUser("setting-service-update-immutable-fields@primatis.test");
        entityManager.flush();
        String originalDescription = applicationSettingRepositoryDescriptionForTest("LOAN_DUE_SOON_DAYS");
        authenticateWithAuthority("SETTING_MANAGE");

        SettingResponse result = applicationSettingService.updateSettingValue(
                "LOAN_DUE_SOON_DAYS", "4", actingUser.getId());

        assertThat(result.settingKey()).isEqualTo("LOAN_DUE_SOON_DAYS");
        assertThat(result.valueType()).isEqualTo("INTEGER");
        assertThat(result.description()).isEqualTo(originalDescription);
    }

    @Test
    void updateSettingValueRejectsNegativeInteger() {
        AppUser actingUser = persistActingUser("setting-service-update-integer-negative@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "LOAN_DUE_SOON_DAYS", "-1", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_NOT_POSITIVE");
    }

    @Test
    void updateSettingValueRejectsZeroInteger() {
        AppUser actingUser = persistActingUser("setting-service-update-integer-zero@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "LOAN_DUE_SOON_DAYS", "0", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_NOT_POSITIVE");
    }

    @Test
    void updateSettingValueRejectsNonNumericInteger() {
        AppUser actingUser = persistActingUser("setting-service-update-integer-nan@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "LOAN_DUE_SOON_DAYS", "abc", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_NOT_INTEGER");
    }

    @Test
    void updateSettingValueRejectsNegativeDecimal() {
        AppUser actingUser = persistActingUser("setting-service-update-decimal-negative@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "FINE_WEEKLY_RATE", "-0.5", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_NOT_POSITIVE");
    }

    @Test
    void updateSettingValueRejectsZeroDecimal() {
        AppUser actingUser = persistActingUser("setting-service-update-decimal-zero@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "FINE_WEEKLY_RATE", "0.00", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_NOT_POSITIVE");
    }

    @Test
    void updateSettingValueRejectsNonNumericDecimal() {
        AppUser actingUser = persistActingUser("setting-service-update-decimal-nan@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "FINE_WEEKLY_RATE", "abc", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_NOT_DECIMAL");
    }

    @Test
    void updateSettingValueThrowsResourceNotFoundWhenKeyIsMissing() {
        AppUser actingUser = persistActingUser("setting-service-update-missing-key@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "DOES_NOT_EXIST", "5", actingUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_NOT_FOUND");
    }

    @Test
    void updateSettingValueThrowsBusinessRuleWhenValueTypeIsNotSupported() {
        persistSetting("AN_UNSUPPORTED_TYPE_SETTING", "hello", "STRING");
        AppUser actingUser = persistActingUser("setting-service-update-unsupported-type@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "AN_UNSUPPORTED_TYPE_SETTING", "world", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SETTING_VALUE_TYPE_NOT_SUPPORTED");
    }

    @Test
    void updateSettingValueLeavesSettingUnchangedWhenValidationFails() {
        AppUser actingUser = persistActingUser("setting-service-update-rollback@primatis.test");
        entityManager.flush();
        authenticateWithAuthority("SETTING_MANAGE");

        assertThatThrownBy(() -> applicationSettingService.updateSettingValue(
                "LOAN_DUE_SOON_DAYS", "-1", actingUser.getId()))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(applicationSettingService.getInteger("LOAN_DUE_SOON_DAYS")).isEqualTo(3);
    }

    private static void authenticateWithAuthority(String authority) {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority(authority));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AppUser persistActingUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash("{noop}unused");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private String applicationSettingRepositoryDescriptionForTest(String key) {
        return entityManager
                .createQuery("SELECT s.description FROM ApplicationSetting s WHERE s.settingKey = :key", String.class)
                .setParameter("key", key)
                .getSingleResult();
    }

    private Instant applicationSettingUpdatedAtForTest(String key) {
        return entityManager
                .createQuery("SELECT s.updatedAt FROM ApplicationSetting s WHERE s.settingKey = :key", Instant.class)
                .setParameter("key", key)
                .getSingleResult();
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
