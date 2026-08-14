package be.primatis.exception;

/**
 * Compte temporairement verrouillé suite à 3 échecs de mot de passe
 * consécutifs (verrouillage de 15 minutes). Traduite en HTTP 401 par
 * {@link GlobalExceptionHandler} — même statut que
 * {@link InvalidCredentialsException}, mais code distinct pour permettre au
 * frontend d'adapter le message affiché sans jamais recevoir le détail brut
 * de l'état du compte.
 */
public class AccountTemporarilyLockedException extends ApiException {

    private static final String DEFAULT_CODE = "ACCOUNT_TEMPORARILY_LOCKED";

    public AccountTemporarilyLockedException() {
        super(DEFAULT_CODE, "Compte temporairement verrouillé suite à plusieurs échecs de connexion.");
    }
}
