package be.primatis.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller strictement réservé aux tests de {@link GlobalExceptionHandler}
 * (scope src/test). Ne fait partie d'aucun binaire de production : il ne
 * fait qu'exercer chaque branche de traduction d'exception de l'infrastructure
 * DEV-03.3, sans introduire de Controller métier artificiel.
 */
@RestController
@Validated
class GlobalExceptionHandlerTestController {

    @GetMapping("/test-errors/not-found")
    public void notFound() {
        throw new ResourceNotFoundException("Ressource introuvable.");
    }

    @GetMapping("/test-errors/conflict")
    public void conflict() {
        throw new ConflictException("Conflit technique.");
    }

    @GetMapping("/test-errors/business-rule")
    public void businessRule() {
        throw new BusinessRuleException("Règle métier violée.");
    }

    @GetMapping("/test-errors/forbidden")
    public void forbidden() {
        throw new ForbiddenOperationException("Opération non autorisée.");
    }

    @PostMapping("/test-errors/validate-body")
    public void validateBody(@Valid @RequestBody TestRequest request) {
        // volontairement vide : la validation @Valid doit échouer avant d'atteindre ce corps.
    }

    @GetMapping("/test-errors/validate-param")
    public void validateParam(@RequestParam @Min(1) int quantity) {
        // volontairement vide : la validation de méthode doit échouer avant d'atteindre ce corps.
    }

    @GetMapping("/test-errors/unexpected")
    public void unexpected() {
        throw new IllegalStateException("Détail interne sensible qui ne doit jamais atteindre le client.");
    }

    record TestRequest(@NotBlank String name) {
    }
}
