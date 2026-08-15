package be.primatis.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Traduction centralisée des exceptions en {@link ApiErrorResponse}
 * (architecture.md §4.7 : aucune exception technique/Spring/Hibernate ne
 * doit fuiter directement vers Angular).
 *
 * Aucune stack trace ni détail interne n'est jamais renvoyé au client ;
 * les erreurs inattendues sont journalisées côté serveur via SLF4J.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccountTemporarilyLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountTemporarilyLocked(
            AccountTemporarilyLockedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request, List.of());
    }

    /**
     * Refus d'autorisation provenant de Method Security ({@code @PreAuthorize},
     * DEV-03.9) : contrairement à un refus de la {@code SecurityFilterChain}
     * (filtre {@code AuthorizationFilter}, intercepté en amont par
     * {@code be.primatis.security.PrimatisAccessDeniedHandler}, DEV-03.10),
     * cette exception est levée depuis l'intérieur d'un appel Service invoqué
     * pendant le dispatch Spring MVC — {@code ExceptionTranslationFilter} ne
     * la voit jamais, elle doit donc être traduite ici pour respecter le même
     * contrat public {@code 403 / ACCESS_DENIED}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Vous n'êtes pas autorisé à effectuer cette action.", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Un ou plusieurs champs sont invalides.", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(lastPathNode(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION",
                "Un ou plusieurs paramètres sont invalides.", request, fieldErrors);
    }

    /**
     * Un paramètre présent mais non convertible vers le type attendu (ex.
     * {@code ?page=abc} sur un {@code @RequestParam int}) échoue pendant la
     * liaison Spring MVC, avant toute validation Bean Validation — distinct
     * de {@link #handleConstraintViolation}, qui suppose une conversion déjà
     * réussie mais une valeur hors contrainte. Le message d'origine de
     * {@link MethodArgumentTypeMismatchException} contient le type Java
     * cible et la valeur brute reçue : jamais renvoyé tel quel au client.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors =
                List.of(new ApiFieldError(ex.getName(), "Valeur invalide pour ce paramètre."));
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER",
                "Un ou plusieurs paramètres sont invalides.", request, fieldErrors);
    }

    /**
     * Corps de requête illisible : JSON malformé, ou valeur textuelle ne
     * correspondant à aucune constante d'un enum cible (ex. {@code {"status":
     * "BOGUS"}} sur {@link be.primatis.user.web.UpdateAccountStatusRequest})
     * — échoue pendant la désérialisation Jackson elle-même, avant que
     * Bean Validation ({@code @Valid}) n'ait la moindre chance de s'exécuter
     * (distinct de {@link #handleMethodArgumentNotValid}, qui suppose une
     * désérialisation déjà réussie). Le message d'origine peut contenir des
     * détails internes Jackson (nom de classe complet, valeurs acceptées) :
     * jamais renvoyé tel quel au client.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "Le corps de la requête est invalide ou illisible.", request, List.of());
    }

    /**
     * Aucune route ne correspond à la requête (ex. ancienne route
     * volontairement supprimée, comme {@code POST /users/{id}/disable}
     * depuis DEV-05.7). Depuis Spring Framework 6.1/Boot 3.2, un chemin non
     * mappé par un {@code @RequestMapping} est intercepté par le
     * {@code HandlerMapping} des ressources statiques (catch-all {@code
     * /**}), qui lève {@link NoResourceFoundException} plutôt que
     * {@link NoHandlerFoundException} — sans ce handler dédié, cette
     * exception tombait dans {@link #handleUnexpected}, produisant à tort
     * une 500 pour une route simplement inexistante. {@link
     * NoHandlerFoundException} reste géré par précaution (comportement
     * historique, selon configuration Spring MVC).
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNoRouteFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                "Aucune route ne correspond à cette requête.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur interne inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne est survenue.", request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String code, String message, HttpServletRequest request,
            List<ApiFieldError> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
