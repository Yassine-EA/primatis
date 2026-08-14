package be.primatis.exception;

/**
 * Base commune des exceptions métier traduites en {@link ApiErrorResponse}
 * par {@link GlobalExceptionHandler}.
 *
 * {@code code} est le code d'erreur technique stable exposé au client
 * (anglais, UPPER_SNAKE_CASE). Chaque sous-classe fixe un code par défaut
 * cohérent avec son statut HTTP, tout en permettant à l'appelant de fournir
 * un code plus précis lorsque le contexte métier le justifie.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;

    protected ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
