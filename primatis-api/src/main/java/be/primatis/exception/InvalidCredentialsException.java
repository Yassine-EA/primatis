package be.primatis.exception;

/**
 * Identifiants invalides lors d'une tentative d'authentification. Traduite
 * en HTTP 401 par {@link GlobalExceptionHandler}.
 *
 * Utilisée indifféremment pour : email inconnu, mot de passe incorrect, et
 * tout autre échec Spring Security non lié à un mot de passe incorrect (ex.
 * AccountStatus.DISABLED) — volontairement, pour ne jamais révéler au
 * client si un compte existe ou pourquoi il est refusé (DEV-03.6).
 */
public class InvalidCredentialsException extends ApiException {

    private static final String DEFAULT_CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(DEFAULT_CODE, "Identifiants invalides.");
    }
}
