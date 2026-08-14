package be.primatis.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie la SecurityFilterChain finale PRIMATIS (DEV-03.8) : surface
 * publique strictement définie (GET uniquement pour titles/articles),
 * resource server JWT RS256, conversion roles/permissions → GrantedAuthority
 * sans préfixe SCOPE_/double ROLE_, CORS explicite, absence de session HTTP.
 * Clés RSA de test générées en mémoire (DEV-03.7/JwtTestKeysConfig) — aucun
 * fichier PEM/KEY.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterChainTests {

    private static final String AUTHORIZED_ORIGIN = "http://localhost:4200";
    private static final String UNAUTHORIZED_ORIGIN = "http://attacker.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    // ---------------------------------------------------------------
    // Surface publique stricte (GET uniquement)
    // ---------------------------------------------------------------

    @Test
    void publicGetTitlesPassesSecurityWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/titles/sample"))
                .andExpect(status().isOk());
    }

    @Test
    void publicGetArticlesPassesSecurityWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/articles/sample"))
                .andExpect(status().isOk());
    }

    @Test
    void postOnTitlesWithoutJwtIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/titles/sample"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchOnArticlesWithoutJwtIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/articles/sample"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/protected/sample"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidJwtIsAuthenticated() throws Exception {
        String token = signToken(builder -> builder);

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Conversion roles/permissions -> GrantedAuthority
    // ---------------------------------------------------------------

    @Test
    void roleClaimProducesRoleMemberAuthority() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of("ROLE_MEMBER"))
                .claim("permissions", List.of()));

        mockMvc.perform(get("/api/v1/protected/authorities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("ROLE_MEMBER")));
    }

    @Test
    void permissionClaimProducesCatalogueReadAuthority() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of())
                .claim("permissions", List.of("CATALOGUE_READ")));

        mockMvc.perform(get("/api/v1/protected/authorities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("CATALOGUE_READ")));
    }

    @Test
    void rolesAndPermissionsTogetherProduceCorrectUnion() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of("ROLE_LIBRARIAN"))
                .claim("permissions", List.of("LOAN_MANAGE", "ARTICLE_PUBLISH")));

        mockMvc.perform(get("/api/v1/protected/authorities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.containsInAnyOrder(
                        "ROLE_LIBRARIAN", "LOAN_MANAGE", "ARTICLE_PUBLISH")));
    }

    @Test
    void roleClaimIsNeverDoublePrefixed() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of("ROLE_MEMBER"))
                .claim("permissions", List.of()));

        mockMvc.perform(get("/api/v1/protected/authorities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("ROLE_ROLE_MEMBER"))));
    }

    @Test
    void permissionClaimNeverGetsScopePrefix() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of())
                .claim("permissions", List.of("CATALOGUE_READ")));

        mockMvc.perform(get("/api/v1/protected/authorities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("SCOPE_CATALOGUE_READ"))));
    }

    @Test
    void duplicatedPermissionInClaimProducesSingleAuthority() throws Exception {
        String token = signToken(builder -> builder
                .claim("roles", List.of())
                .claim("permissions", List.of("CATALOGUE_READ", "CATALOGUE_READ")));

        mockMvc.perform(get("/api/v1/protected/authorities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.contains("CATALOGUE_READ")));
    }

    // ---------------------------------------------------------------
    // Rejet des tokens invalides
    // ---------------------------------------------------------------

    @Test
    void expiredJwtIsRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(7200);
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

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtSignedByAnotherKeyIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherKeyPair = generator.generateKeyPair();

        RSAKey otherRsaKey = new RSAKey.Builder((RSAPublicKey) otherKeyPair.getPublic())
                .privateKey((RSAPrivateKey) otherKeyPair.getPrivate())
                .build();
        JWKSource<SecurityContext> otherJwkSource = new ImmutableJWKSet<>(new JWKSet(otherRsaKey));
        JwtEncoder otherEncoder = new NimbusJwtEncoder(otherJwkSource);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of())
                .claim("permissions", List.of())
                .build();
        String token = otherEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtWithWrongAudienceIsRejected() throws Exception {
        String token = signToken(builder -> builder.audience(List.of("some-other-api")));

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtWithWrongIssuerIsRejected() throws Exception {
        String token = signToken(builder -> builder.issuer("attacker-issuer"));

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // CORS
    // ---------------------------------------------------------------

    @Test
    void requestFromAuthorizedOriginReceivesCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/titles/sample").header("Origin", AUTHORIZED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", AUTHORIZED_ORIGIN));
    }

    @Test
    void corsPreflightFromAuthorizedOriginIsNotBlockedByAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/protected/sample")
                        .header("Origin", AUTHORIZED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", AUTHORIZED_ORIGIN));
    }

    @Test
    void corsPreflightFromUnauthorizedOriginGrantsNoAccess() throws Exception {
        mockMvc.perform(options("/api/v1/protected/sample")
                        .header("Origin", UNAUTHORIZED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---------------------------------------------------------------
    // Aucune session HTTP créée par l'authentification Bearer
    // ---------------------------------------------------------------

    @Test
    void bearerAuthenticationCreatesNoHttpSession() throws Exception {
        String token = signToken(builder -> builder);

        mockMvc.perform(get("/api/v1/protected/sample").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private interface ClaimsCustomizer {
        JwtClaimsSet.Builder customize(JwtClaimsSet.Builder builder);
    }

    private String signToken(ClaimsCustomizer customizer) {
        Instant now = Instant.now();
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
