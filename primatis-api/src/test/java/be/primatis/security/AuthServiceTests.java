package be.primatis.security;

import be.primatis.exception.AccountTemporarilyLockedException;
import be.primatis.exception.InvalidCredentialsException;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie contre PostgreSQL réel (primatis_test) le workflow de login
 * DEV-03.6 : verrouillage temporaire (3 échecs → 15 minutes), non-divulgation
 * de la cause d'échec, distinction AccountStatus/MemberStatus, non-journalisation
 * du mot de passe/hash. Clock fixe et manipulable (aucun Thread.sleep()).
 *
 * PORTÉE IMPORTANTE : cette classe est annotée {@code @Transactional} au
 * niveau classe, donc chaque appel à {@code authService.login(...)} REJOINT
 * la transaction déjà ouverte par le test (propagation REQUIRED par défaut)
 * au lieu d'en ouvrir une nouvelle. Les assertions ici prouvent que les
 * bonnes VALEURS sont calculées et que les bonnes exceptions sont levées
 * (logique métier), mais PAS que ces valeurs survivent réellement à une fin
 * de transaction physique (commit) comme en production — un bug
 * transactionnel réel (rollback silencieux de failedLoginCount/lockedUntil
 * sur InvalidCredentialsException, corrigé par {@code noRollbackFor} sur
 * {@code AuthService.login}) a précisément été masqué par cette
 * configuration de test avant d'être détecté par un test de concurrence non
 * transactionnel. La preuve de persistance réelle inter-transaction se
 * trouve dans {@code AuthServiceLoginPersistenceTests} (séquentiel, sans
 * transaction de test englobante) et {@code AuthServiceConcurrencyTests}
 * (concurrent, sans transaction de test englobante non plus).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ClockTestConfig.class)
@Transactional
class AuthServiceTests {

    private static final String RAW_PASSWORD = "Correct-Horse-Battery-Staple-15";
    private static final String WRONG_PASSWORD = "Wrong-Horse-Battery-Staple-99";

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MutableClock clock;

    @PersistenceContext
    private EntityManager entityManager;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void resetClockAndAttachLogAppender() {
        clock.setInstant(ClockTestConfig.FIXED_INSTANT);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger().addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        rootLogger().detachAppender(logAppender);
    }

    // ---------------------------------------------------------------
    // 1. Email inconnu
    // ---------------------------------------------------------------

    @Test
    void unknownEmailReturnsInvalidCredentialsWithoutPersistingAnything() {
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login("absent@primatis.test", RAW_PASSWORD));

        assertThat(appUserRepository.findByEmail("absent@primatis.test")).isEmpty();
    }

    // ---------------------------------------------------------------
    // 2-4. Compteur d'échecs jusqu'au verrouillage
    // ---------------------------------------------------------------

    @Test
    void firstWrongPasswordSetsFailedLoginCountToOne() {
        String email = persistUser("first-fail@primatis.test", AccountStatus.ACTIVE, null);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(email, WRONG_PASSWORD));

        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void secondWrongPasswordSetsFailedLoginCountToTwo() {
        String email = persistUser("second-fail@primatis.test", AccountStatus.ACTIVE, null);

        attemptWrongPassword(email);
        attemptWrongPassword(email);

        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(2);
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void thirdWrongPasswordLocksAccountButStillReturnsInvalidCredentials() {
        String email = persistUser("third-fail@primatis.test", AccountStatus.ACTIVE, null);

        attemptWrongPassword(email);
        attemptWrongPassword(email);
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(email, WRONG_PASSWORD));

        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(3);
        assertThat(reloaded.getLockedUntil()).isEqualTo(clock.instant().plus(AuthService.LOCK_DURATION));
    }

    // ---------------------------------------------------------------
    // 5-6. Tentative pendant le verrouillage
    // ---------------------------------------------------------------

    @Test
    void attemptDuringLockReturnsAccountTemporarilyLockedWithoutCheckingPassword() {
        String email = persistUser("during-lock@primatis.test", AccountStatus.ACTIVE, null);
        lockAccountWithThreeFailedAttempts(email);
        Instant lockedUntilBefore = reload(email).getLockedUntil();

        // Mot de passe CORRECT ici : si la vérification du mot de passe
        // était exécutée, l'authentification réussirait. Le verrouillage
        // doit l'empêcher avant même cette comparaison.
        assertThatExceptionOfType(AccountTemporarilyLockedException.class)
                .isThrownBy(() -> authService.login(email, RAW_PASSWORD));

        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(3);
        assertThat(reloaded.getLockedUntil()).isEqualTo(lockedUntilBefore);
    }

    @Test
    void attemptJustBeforeExpirationIsStillLocked() {
        String email = persistUser("just-before-expiry@primatis.test", AccountStatus.ACTIVE, null);
        lockAccountWithThreeFailedAttempts(email);
        Instant lockedUntil = reload(email).getLockedUntil();

        clock.setInstant(lockedUntil.minusSeconds(1));

        assertThatExceptionOfType(AccountTemporarilyLockedException.class)
                .isThrownBy(() -> authService.login(email, RAW_PASSWORD));
    }

    // ---------------------------------------------------------------
    // 7-8, 10. Expiration du verrouillage
    // ---------------------------------------------------------------

    @Test
    void successAtExactExpirationResetsStateCompletely() {
        String email = persistUser("success-after-expiry@primatis.test", AccountStatus.ACTIVE, null);
        lockAccountWithThreeFailedAttempts(email);
        Instant lockedUntil = reload(email).getLockedUntil();

        clock.setInstant(lockedUntil);

        Authentication authentication = authService.login(email, RAW_PASSWORD);

        assertThat(authentication).isNotNull();
        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.getLastLoginAt()).isEqualTo(clock.instant());
    }

    @Test
    void firstWrongPasswordAfterExpirationResetsCounterToOneNotFour() {
        String email = persistUser("wrong-after-expiry@primatis.test", AccountStatus.ACTIVE, null);
        lockAccountWithThreeFailedAttempts(email);
        Instant lockedUntil = reload(email).getLockedUntil();

        clock.setInstant(lockedUntil.plusSeconds(1));

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(email, WRONG_PASSWORD));

        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    // ---------------------------------------------------------------
    // 9. Succès depuis un état jamais verrouillé
    // ---------------------------------------------------------------

    @Test
    void successResetsCounterAndSetsLastLoginAt() {
        String email = persistUser("clean-success@primatis.test", AccountStatus.ACTIVE, null);

        Authentication authentication = authService.login(email, RAW_PASSWORD);

        assertThat(authentication).isNotNull();
        AppUser reloaded = reload(email);
        assertThat(reloaded.getFailedLoginCount()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.getLastLoginAt()).isEqualTo(clock.instant());
    }

    // ---------------------------------------------------------------
    // 11-13. AccountStatus / MemberStatus
    // ---------------------------------------------------------------

    @Test
    void disabledAccountStatusIsRejectedWithoutTouchingFailedLoginCount() {
        String email = persistUser("disabled@primatis.test", AccountStatus.DISABLED, null);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(email, RAW_PASSWORD));

        AppUser reloaded = reload(email);
        // Aucune comparaison de mot de passe n'a eu lieu (DisabledException
        // levée avant) : le compteur d'échecs reste inchangé.
        assertThat(reloaded.getFailedLoginCount()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void blockedMemberStatusDoesNotPreventAuthenticationWhenAccountActive() {
        String email = persistUser("blocked-member@primatis.test", AccountStatus.ACTIVE, MemberStatus.BLOCKED);

        Authentication authentication = authService.login(email, RAW_PASSWORD);

        assertThat(authentication).isNotNull();
    }

    @Test
    void expiredMemberStatusDoesNotPreventAuthenticationAlone() {
        String email = persistUser("expired-member@primatis.test", AccountStatus.ACTIVE, MemberStatus.EXPIRED);

        Authentication authentication = authService.login(email, RAW_PASSWORD);

        assertThat(authentication).isNotNull();
    }

    // ---------------------------------------------------------------
    // 14. Même code public pour email inconnu et mauvais mot de passe
    // ---------------------------------------------------------------

    @Test
    void unknownEmailAndWrongPasswordShareTheSamePublicCode() {
        String email = persistUser("same-code@primatis.test", AccountStatus.ACTIVE, null);

        InvalidCredentialsException fromUnknownEmail = catchInvalidCredentials(
                () -> authService.login("still-absent@primatis.test", RAW_PASSWORD));
        InvalidCredentialsException fromWrongPassword = catchInvalidCredentials(
                () -> authService.login(email, WRONG_PASSWORD));

        assertThat(fromUnknownEmail.getCode()).isEqualTo(fromWrongPassword.getCode());
        assertThat(fromUnknownEmail.getCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    // ---------------------------------------------------------------
    // 15. Non-journalisation du mot de passe / hash
    // ---------------------------------------------------------------

    @Test
    void loginNeverLogsRawPasswordOrPasswordHash() {
        String email = persistUser("no-log@primatis.test", AccountStatus.ACTIVE, null);
        String hash = reload(email).getPasswordHash();

        authService.login(email, RAW_PASSWORD);
        try {
            authService.login(email, WRONG_PASSWORD);
        } catch (InvalidCredentialsException ignored) {
            // attendu : seul l'absence de fuite dans les logs est vérifiée ici.
        }

        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain(RAW_PASSWORD)
                    .doesNotContain(WRONG_PASSWORD)
                    .doesNotContain(hash);
        }
    }

    // ---------------------------------------------------------------
    // Fixtures et utilitaires
    // ---------------------------------------------------------------

    private void attemptWrongPassword(String email) {
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(email, WRONG_PASSWORD));
    }

    private void lockAccountWithThreeFailedAttempts(String email) {
        attemptWrongPassword(email);
        attemptWrongPassword(email);
        attemptWrongPassword(email);
        assertThat(reload(email).getLockedUntil()).isNotNull();
    }

    private InvalidCredentialsException catchInvalidCredentials(Runnable action) {
        try {
            action.run();
        } catch (InvalidCredentialsException ex) {
            return ex;
        }
        throw new AssertionError("InvalidCredentialsException attendue mais non levée.");
    }

    private String persistUser(String email, AccountStatus accountStatus, MemberStatus memberStatus) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(accountStatus);
        user.setMemberStatus(memberStatus);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        entityManager.flush();
        return email;
    }

    private AppUser reload(String email) {
        // flush avant clear : les mutations faites par AuthService.login()
        // (même transaction, propagation REQUIRED) ne sont sinon jamais
        // poussées vers PostgreSQL avant le rollback de fin de test.
        entityManager.flush();
        entityManager.clear();
        return appUserRepository.findByEmail(email).orElseThrow();
    }

    private Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
