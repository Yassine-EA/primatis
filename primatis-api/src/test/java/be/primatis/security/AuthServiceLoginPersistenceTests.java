package be.primatis.security;

import be.primatis.exception.InvalidCredentialsException;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Reproduit le scénario de production exact qui a révélé le bug
 * transactionnel DEV-03.6 : un unique appel à {@code AuthService.login}
 * SANS transaction de test englobante (contrairement à
 * {@code AuthServiceTests}), suivi d'une relecture depuis une transaction
 * entièrement neuve. Prouve que failedLoginCount/lockedUntil sont réellement
 * committés en base — pas seulement visibles en mémoire dans la transaction
 * qui les a modifiés.
 *
 * Avant correction ({@code @Transactional} sans {@code noRollbackFor} sur
 * {@code AuthService.login}), ce test échouait : la RuntimeException
 * InvalidCredentialsException déclenchait un rollback Spring par défaut qui
 * annulait silencieusement l'incrément du compteur.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceLoginPersistenceTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void wrongPasswordAttemptReallyPersistsFailedLoginCountAfterTransactionEnds() {
        String email = "persistence-check@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-Never-Used-Here-15"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
        });

        try {
            // Appel réel, hors de toute transaction de test déjà ouverte :
            // login() constitue ici sa propre frontière transactionnelle,
            // exactement comme en production (ex. futur AuthController).
            assertThatExceptionOfType(InvalidCredentialsException.class)
                    .isThrownBy(() -> authService.login(email, "Wrong-Password-Attempt"));

            // Relecture depuis une transaction NEUVE, distincte de celle de
            // login() : si le compteur n'avait pas été réellement committé,
            // on lirait 0, pas 1.
            AppUser reloaded = transactionTemplate.execute(status ->
                    appUserRepository.findByEmail(email).orElseThrow());

            assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
            assertThat(reloaded.getLockedUntil()).isNull();
        } finally {
            transactionTemplate.executeWithoutResult(status ->
                    appUserRepository.findByEmail(email).ifPresent(appUserRepository::delete));
        }
    }
}
