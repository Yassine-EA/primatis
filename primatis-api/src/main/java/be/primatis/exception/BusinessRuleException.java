package be.primatis.exception;

/**
 * Violation d'une règle métier PRIMATIS. Traduite en HTTP 409 par
 * {@link GlobalExceptionHandler}, conformément à l'exemple de contrat
 * d'erreur de la baseline (architecture.md §4.5 : code
 * {@code BUSINESS_RULE_VIOLATION}, statut 409).
 */
public class BusinessRuleException extends ApiException {

    private static final String DEFAULT_CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String message) {
        super(DEFAULT_CODE, message);
    }

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
