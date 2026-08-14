package be.primatis.exception;

/**
 * Identité authentifiée mais opération non autorisée. Traduite en HTTP 403
 * par {@link GlobalExceptionHandler}.
 *
 * Ne concerne pas l'absence d'authentification (401) : ce cas relève de la
 * chaîne de sécurité (AuthenticationEntryPoint), hors périmètre DEV-03.3.
 */
public class ForbiddenOperationException extends ApiException {

    private static final String DEFAULT_CODE = "FORBIDDEN_OPERATION";

    public ForbiddenOperationException(String message) {
        super(DEFAULT_CODE, message);
    }

    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }
}
