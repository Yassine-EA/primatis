package be.primatis.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie contre un contexte web isolé (pas de PostgreSQL nécessaire, aucun
 * rapport avec la persistance) que {@link GlobalExceptionHandler} traduit
 * correctement chaque catégorie d'exception en {@link ApiErrorResponse}.
 *
 * {@code addFilters = false} désactive tous les filtres servlet, y compris
 * la SecurityFilterChain (DEV-03.8, plus de permitAll global) : l'objet de
 * cette classe est la traduction exception → HTTP, pas l'autorisation —
 * inutile de charger {@code SecurityConfig} ou de fournir un JWT valide
 * pour chaque scénario d'erreur (mocks/désactivation ciblés, la sécurité
 * elle-même n'est pas testée ici).
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resourceNotFoundReturns404WithEmptyFieldErrors() throws Exception {
        mockMvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ressource introuvable."))
                .andExpect(jsonPath("$.path").value("/test-errors/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void conflictExceptionReturns409() throws Exception {
        mockMvc.perform(get("/test-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void businessRuleExceptionReturns409() throws Exception {
        mockMvc.perform(get("/test-errors/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void forbiddenOperationExceptionReturns403() throws Exception {
        mockMvc.perform(get("/test-errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void invalidRequestBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test-errors/validate-body")
                        .contentType("application/json")
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void invalidRequestParamReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/test-errors/validate-param").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
    }

    /**
     * {@code quantity} est un {@code @RequestParam int} : "abc" échoue la
     * liaison Spring MVC elle-même (conversion de type), avant que
     * {@code @Min(1)} n'ait la moindre chance de s'évaluer — distinct de
     * {@link #invalidRequestParamReturns400WithFieldErrors} (valeur
     * convertie avec succès mais hors contrainte).
     */
    @Test
    void invalidRequestParamTypeReturns400WithoutExposingInternalDetail() throws Exception {
        mockMvc.perform(get("/test-errors/validate-param").param("quantity", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java.lang"))))
                .andExpect(jsonPath("$.fieldErrors[0].message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java.lang"))));
    }

    /**
     * JSON syntaxiquement invalide échoue la désérialisation Jackson
     * elle-même, avant que {@code @Valid} n'ait la moindre chance de
     * s'exécuter — même mécanisme que la valeur textuelle inconnue d'un
     * enum cible (DEV-05.7 gate, {@code UpdateAccountStatusRequest.status}),
     * reproduit ici sans endpoint dédié supplémentaire.
     */
    @Test
    void malformedRequestBodyReturns400WithoutExposingInternalDetail() throws Exception {
        mockMvc.perform(post("/test-errors/validate-body")
                        .contentType("application/json")
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("com.fasterxml"))));
    }

    /**
     * Contrat DEV-05.7 gate PostgreSQL réel : une route simplement
     * inexistante (ex. ancienne route supprimée) doit produire 404, jamais
     * 500. Reproduit ici sans endpoint dédié : n'importe quel chemin non
     * mappé sous cette même tranche {@code @WebMvcTest} déclenche déjà
     * {@link org.springframework.web.servlet.resource.NoResourceFoundException}.
     */
    @Test
    void unmappedRouteReturns404NotServerError() throws Exception {
        mockMvc.perform(post("/test-errors/this-route-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void unexpectedExceptionReturns500WithoutExposingInternalDetail() throws Exception {
        mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Une erreur interne est survenue."))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensible"))))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
}
