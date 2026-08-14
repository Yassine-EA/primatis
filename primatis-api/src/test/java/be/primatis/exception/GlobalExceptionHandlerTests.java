package be.primatis.exception;

import be.primatis.config.SecurityConfig;
import be.primatis.security.PrimatisUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie contre un contexte web isolé (pas de PostgreSQL nécessaire, aucun
 * rapport avec la persistance) que {@link GlobalExceptionHandler} traduit
 * correctement chaque catégorie d'exception en {@link ApiErrorResponse},
 * avec la configuration Spring Security réelle importée (permitAll DEV-03.2)
 * pour ne pas être bloqué par un 401 avant d'atteindre les handlers.
 *
 * {@link PrimatisUserDetailsService} est mocké (@MockitoBean, remplaçant non
 * déprécié de @MockBean) : depuis DEV-03.6, SecurityConfig déclare un
 * DaoAuthenticationProvider qui en dépend, mais ce bean @Service (et son
 * AppUserRepository JPA) n'existe pas dans la tranche @WebMvcTest — hors
 * sujet ici, cette classe ne teste que la traduction exception → HTTP.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTestController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrimatisUserDetailsService primatisUserDetailsService;

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
