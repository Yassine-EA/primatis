package be.primatis.exception;

/**
 * Conflit d'état technique (par exemple une contrainte d'unicité déjà
 * occupée). Traduite en HTTP 409 par {@link GlobalExceptionHandler}.
 *
 * Pour un conflit qui exprime une règle métier PRIMATIS (et non un simple
 * conflit technique), préférer {@link BusinessRuleException}.
 */
public class ConflictException extends ApiException {

    private static final String DEFAULT_CODE = "CONFLICT";

    public ConflictException(String message) {
        super(DEFAULT_CODE, message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
