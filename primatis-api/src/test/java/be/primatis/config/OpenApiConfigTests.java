package be.primatis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentation OpenAPI PRIMATIS (DEV-03.12) : le schéma {@code bearerAuth}
 * doit correspondre exactement au JWT Bearer réel (type HTTP, scheme
 * bearer, bearerFormat JWT), sans authentification concurrente (Basic, API
 * key, OAuth2), sans donnée sensible, et sans exposer accidentellement
 * {@code /v3/api-docs} au public (la SecurityFilterChain DEV-03.8/03.10
 * reste inchangée : cette route reste {@code authenticated()}).
 *
 * Vérifie à la fois le bean {@link OpenAPI} directement (structure, aucune
 * dépendance à un vrai token/clé) et le document généré via
 * {@code /v3/api-docs} en conditions réelles (MockMvc, clé RSA de test en
 * mémoire DEV-03.7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTests {

    @Autowired
    private OpenAPI openApi;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void bearerAuthSecuritySchemeIsDeclaredWithCorrectShape() {
        assertThat(openApi.getComponents()).isNotNull();
        Map<String, SecurityScheme> schemes = openApi.getComponents().getSecuritySchemes();
        assertThat(schemes).containsKey("bearerAuth");

        SecurityScheme bearerAuth = schemes.get("bearerAuth");
        assertThat(bearerAuth.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerAuth.getScheme()).isEqualTo("bearer");
        assertThat(bearerAuth.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    void noCompetingOrSensitiveSecuritySchemeIsDeclared() {
        Map<String, SecurityScheme> schemes = openApi.getComponents().getSecuritySchemes();

        assertThat(schemes).hasSize(1);
        assertThat(schemes.values()).noneMatch(scheme -> scheme.getType() == SecurityScheme.Type.APIKEY);
        assertThat(schemes.values()).noneMatch(scheme -> scheme.getType() == SecurityScheme.Type.OAUTH2);
        assertThat(schemes.values()).noneMatch(scheme -> scheme.getType() == SecurityScheme.Type.OPENIDCONNECT);
        assertThat(schemes.get("bearerAuth").getFlows()).isNull();
        assertThat(schemes.get("bearerAuth").getIn()).isNull();

        // Aucun secret/URL interne dans la description ou ailleurs dans le bean.
        String description = schemes.get("bearerAuth").getDescription();
        assertThat(description).doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("private key")
                .doesNotContain("-----BEGIN");
    }

    @Test
    void globalSecurityRequirementReferencesBearerAuthOnly() {
        assertThat(openApi.getSecurity()).isNotNull().hasSize(1);
        assertThat(openApi.getSecurity().get(0)).containsOnlyKeys("bearerAuth");
    }

    @Test
    void apiDocsEndpointIsGeneratedAndDeclaresBearerAuth() throws Exception {
        String token = signToken();

        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.apiKeyAuth").doesNotExist());
    }

    /**
     * Aucune ouverture publique par effet de bord : la SecurityFilterChain
     * (DEV-03.8/03.10) n'a pas été modifiée pour Swagger, `/v3/api-docs`
     * reste soumis à `anyRequest().authenticated()` — DEV-03.12 ne
     * documente pas des endpoints ouverts implicitement.
     */
    @Test
    void apiDocsEndpointRequiresAuthenticationLikeAnyOtherProtectedRoute() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void swaggerUiIndexAlsoRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    private String signToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of())
                .claim("permissions", List.of())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
