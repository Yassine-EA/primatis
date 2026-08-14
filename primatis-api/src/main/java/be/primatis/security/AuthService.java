package be.primatis.security;

import be.primatis.exception.AccountTemporarilyLockedException;
import be.primatis.exception.InvalidCredentialsException;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Orchestration du workflow métier de login PRIMATIS (DEV-03.6) :
 * identification du compte, verrouillage temporaire (3 échecs consécutifs →
 * 15 minutes), délégation de la vérification du mot de passe à
 * {@link AuthenticationManager}/{@code DaoAuthenticationProvider}. Ne génère
 * aucun JWT (DEV-03.7).
 *
 * Toutes les décisions temporelles utilisent le {@link Clock} injecté, jamais
 * {@code Instant.now()} directement, pour rester testable avec un Clock fixe.
 */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 3;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository appUserRepository;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;

    public AuthService(AppUserRepository appUserRepository, AuthenticationManager authenticationManager, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
    }

    /**
     * Tente une authentification username/password. Ne révèle jamais si un
     * échec est dû à un email inconnu, un mot de passe incorrect, ou un
     * compte {@code AccountStatus.DISABLED} : seules
     * {@link InvalidCredentialsException} (401, {@code INVALID_CREDENTIALS})
     * et {@link AccountTemporarilyLockedException} (401,
     * {@code ACCOUNT_TEMPORARILY_LOCKED}) sortent de cette méthode.
     *
     * {@code noRollbackFor} est indispensable : ces deux exceptions
     * représentent des résultats métier attendus du workflow d'authentification
     * (pas des erreurs techniques), et {@code registerFailedAttempt} mute
     * failedLoginCount/lockedUntil juste avant de lever
     * InvalidCredentialsException. Sans cette configuration explicite, le
     * comportement par défaut de Spring (rollback sur toute RuntimeException
     * sortant de la frontière @Transactional) annulerait silencieusement cet
     * incrément — bug réel découvert et corrigé en DEV-03.6, voir
     * decision-log.md.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountTemporarilyLockedException.class})
    public Authentication login(String email, String rawPassword) {
        Instant now = clock.instant();

        // Chargement verrouillé (FOR UPDATE) : nécessaire pour vérifier le
        // verrouillage temporaire et protéger la mise à jour du compteur
        // d'échecs contre une tentative concurrente sur le même compte.
        AppUser user = appUserRepository.findByEmailForAuthentication(email).orElse(null);
        if (user == null) {
            // Email inconnu : aucune donnée créée, aucun compteur modifié,
            // même code public que les autres échecs (non-divulgation).
            throw new InvalidCredentialsException();
        }

        if (user.getLockedUntil() != null) {
            if (now.isBefore(user.getLockedUntil())) {
                // Verrouillage en cours : rejet AVANT toute vérification du
                // mot de passe, compteur et lockedUntil inchangés.
                throw new AccountTemporarilyLockedException();
            }
            // Verrouillage expiré : nouvelle séquence, compteur nettoyé
            // avant de poursuivre normalement l'authentification.
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (BadCredentialsException ex) {
            // Seul cas où une comparaison de mot de passe a réellement eu
            // lieu et a échoué : c'est la seule situation qui incrémente le
            // compteur d'échecs.
            registerFailedAttempt(user, now);
            throw new InvalidCredentialsException();
        } catch (AuthenticationException ex) {
            // AccountStatus.DISABLED (DisabledException) et tout autre échec
            // Spring Security non lié à un mot de passe incorrect :
            // DaoAuthenticationProvider vérifie isEnabled() AVANT de
            // comparer le mot de passe, donc aucune comparaison n'a eu lieu
            // ici — le compteur d'échecs n'est délibérément pas modifié.
            // Décision documentée (DEV-03.6) : non-divulgation de l'état du
            // compte, même code public INVALID_CREDENTIALS que les autres
            // échecs plutôt qu'un code révélant que le compte est désactivé.
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);

        return authentication;
    }

    private void registerFailedAttempt(AppUser user, Instant now) {
        int updatedCount = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(updatedCount);
        if (updatedCount == MAX_FAILED_ATTEMPTS) {
            // La tentative qui déclenche le verrouillage reste elle-même
            // INVALID_CREDENTIALS (levée par l'appelant) ; seules les
            // tentatives suivantes, pendant la fenêtre de verrouillage,
            // recevront ACCOUNT_TEMPORARILY_LOCKED.
            user.setLockedUntil(now.plus(LOCK_DURATION));
        }
    }
}
