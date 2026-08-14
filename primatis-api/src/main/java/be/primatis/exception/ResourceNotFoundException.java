package be.primatis.exception;

/**
 * Ressource inexistante. Traduite en HTTP 404 par {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends ApiException {

    private static final String DEFAULT_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(DEFAULT_CODE, message);
    }

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
