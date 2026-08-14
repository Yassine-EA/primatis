package be.primatis.config;

import be.primatis.security.PrimatisAccessDeniedHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat 401/403 PRIMATIS (DEV-03.10) : toute erreur de sécurité HTTP
 * (absence d'authentification, Bearer invalide, refus d'autorisation)
 * utilise exactement {@code ApiErrorResponse} — jamais la page HTML ou le
 * corps par défaut de Spring Security. Clés RSA de test en mémoire
 * (DEV-03.7/{@code JwtTestKeysConfig}), aucun fichier PEM/KEY.
 *
 * Deux origines distinctes de {@code 403 ACCESS_DENIED} sont couvertes :
 * un refus {@code SecurityFilterChain} (testé directement au niveau du
 * {@link PrimatisAccessDeniedHandler}, la baseline actuelle n'exprimant
 * aucune règle d'autorité fine au niveau HTTP — toute l'autorisation
 * métier passe par Method Security, DEV-03.9) et un refus
 * {@code @PreAuthorize} (testé via une route HTTP réelle).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityErrorHandlingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    // ---------------------------------------------------------------
    // 401 — aucune Authentication (AUTHENTICATION_REQUIRED)
    // ---------------------------------------------------------------

    @Test
    void protectedEndpointWithoutAuthorizationHeaderReturnsAuthenticationRequired() throws Exception {
        mockMvc.perform(get("/api/v1/protected/sample"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/protected/sample"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void timestampOfAuthenticationRequiredResponseIsCloseToApplicationClock() throws Exception {
        Instant before = clock.instant();

        String body = mockMvc.perform(get("/api/v1/protected/sample"))
                .andReturn().getResponse().getContentAsString();
        Instant timestamp = objectMapper.readTree(body).get("timestamp").asText().transform(Instant::parse);

        assertThat(timestamp).isBetween(before.minusSeconds(5), clock.instant().plusSeconds(5));
    }

    // ---------------------------------------------------------------
    // 401 — Bearer présent mais rejeté (INVALID_TOKEN)
    // ---------------------------------------------------------------

    @Test
    void validJwtGrantsAccessToProtectedEndpoint() throws Exception {
        String token = signToken(builder -> builder);

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredJwtReturnsInvalidToken() throws Exception {
        Instant past = clock.instant().minusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(past)
                .expiresAt(past.plusSeconds(60))
                .claim("roles", List.of())
                .claim("permissions", List.of())
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        assertInvalidToken(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token));
    }

    @Test
    void jwtSignedByAnotherKeyReturnsInvalidToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherKeyPair = generator.generateKeyPair();

        RSAKey otherRsaKey = new RSAKey.Builder((RSAPublicKey) otherKeyPair.getPublic())
                .privateKey((RSAPrivateKey) otherKeyPair.getPrivate())
                .build();
        JWKSource<SecurityContext> otherJwkSource = new ImmutableJWKSet<>(new JWKSet(otherRsaKey));
        JwtEncoder otherEncoder = new NimbusJwtEncoder(otherJwkSource);

        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of())
                .claim("permissions", List.of())
                .build();
        String token = otherEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        assertInvalidToken(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token));
    }

    @Test
    void jwtWithWrongIssuerReturnsInvalidToken() throws Exception {
        String token = signToken(builder -> builder.issuer("attacker-issuer"));
        assertInvalidToken(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token));
    }

    @Test
    void jwtWithWrongAudienceReturnsInvalidToken() throws Exception {
        String token = signToken(builder -> builder.audience(List.of("some-other-api")));
        assertInvalidToken(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token));
    }

    @Test
    void malformedJwtReturnsInvalidToken() throws Exception {
        assertInvalidToken(get("/api/v1/protected/sample").header("Authorization", "Bearer not-a-jwt-at-all"));
    }

    @Test
    void invalidTokenResponseBodyExposesNoInternalDetail() throws Exception {
        String token = signToken(builder -> builder.issuer("attacker-issuer"));

        String body = mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("nimbus")
                .doesNotContainIgnoringCase("jwtvalidationexception")
                .doesNotContain("be.primatis")
                .doesNotContain("org.springframework");
    }

    private void assertInvalidToken(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.path").exists())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
    }

    // ---------------------------------------------------------------
    // 403 — Authentication valide mais autorisation refusée (ACCESS_DENIED)
    // ---------------------------------------------------------------

    @Test
    void preAuthorizeDeniedWithValidAuthenticationReturnsAccessDenied() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of())
                .claim("permissions", List.of("CATALOGUE_READ")));

        mockMvc.perform(get("/api/v1/protected/loan-manage-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/api/v1/protected/loan-manage-only"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void preAuthorizeAuthorizedWithCorrectPermissionSucceeds() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of())
                .claim("permissions", List.of("LOAN_MANAGE")));

        mockMvc.perform(get("/api/v1/protected/loan-manage-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * Preuve directe du chemin {@code SecurityFilterChain} (par opposition à
     * Method Security ci-dessus) : la baseline actuelle n'exprime aucune
     * règle d'autorité fine au niveau {@code authorizeHttpRequests} (toute
     * l'autorisation métier passe par {@code @PreAuthorize}, DEV-03.9), donc
     * aucune route HTTP réelle ne déclenche aujourd'hui
     * {@code AccessDeniedHandler} via {@code ExceptionTranslationFilter}.
     * Ce test appelle directement le composant de production, prouvant son
     * comportement exact — prêt pour le jour où une règle HTTP fine sera
     * introduite.
     */
    @Test
    void accessDeniedHandlerWritesStandardContractDirectly() throws Exception {
        PrimatisAccessDeniedHandler handler = new PrimatisAccessDeniedHandler(objectMapper, clock);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/some/filter-chain-protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        var json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/some/filter-chain-protected");
        assertThat(json.get("fieldErrors").isArray()).isTrue();
        assertThat(json.get("fieldErrors")).isEmpty();
        assertThat(json.get("timestamp").asText()).isNotBlank();
    }

    // ---------------------------------------------------------------
    // Régressions : public/CORS non affectés par le nouveau branchement
    // ---------------------------------------------------------------

    @Test
    void publicEndpointWithoutJwtRemainsAccessibleAndEntryPointNeverInvoked() throws Exception {
        mockMvc.perform(get("/api/v1/titles/sample"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void corsPreflightOnProtectedEndpointStillWorksWithNewErrorHandling() throws Exception {
        mockMvc.perform(options("/api/v1/protected/sample")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private interface ClaimsCustomizer {
        JwtClaimsSet.Builder customize(JwtClaimsSet.Builder builder);
    }

    private String signToken(ClaimsCustomizer customizer) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of())
                .claim("permissions", List.of());
        JwtClaimsSet claims = customizer.customize(builder).build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
