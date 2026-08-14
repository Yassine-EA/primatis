package be.primatis.security;

import be.primatis.config.JwtProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie l'infrastructure JWT RS256 PRIMATIS (DEV-03.7) : signature,
 * décodage, claims, extraction roles/permissions, et rejet des tokens
 * invalides (expirés, mauvais issuer/audience, mauvaise clé, altérés).
 * Clock fixe injecté (aucun Thread.sleep()). Paire RSA de test générée EN
 * MÉMOIRE ({@code JwtTestKeysConfig}, profil "test") — aucune clé, même de
 * test, versionnée dans le dépôt (contexte maître §7.27).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ClockTestConfig.class)
class JwtServiceTests {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RSAPrivateKey rsaPrivateKey;

    @Autowired
    private MutableClock clock;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void resetClockAndAttachLogAppender() {
        clock.setInstant(ClockTestConfig.FIXED_INSTANT);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger().addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        rootLogger().detachAppender(logAppender);
    }

    @Test
    void tokenIsSignedWithRs256() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.getHeaders().get("alg")).hasToString("RS256");
    }

    @Test
    void tokenIsDecodedWithMatchingPublicKey() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded).isNotNull();
        assertThat(decoded.getTokenValue()).isEqualTo(accessToken.token());
    }

    @Test
    void subjectIsAppUserId() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(42L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.getSubject()).isEqualTo("42");
    }

    @Test
    void issuerIsCorrect() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        // decoded.getIssuer() tente une conversion en java.net.URL (le
        // claim "iss" PRIMATIS est une chaîne simple, pas une URI) : on lit
        // le claim brut plutôt que d'utiliser cet accesseur de convenance.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(jwtProperties.issuer());
    }

    @Test
    void audienceContainsPrimatisApi() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.getAudience()).contains(jwtProperties.audience());
    }

    @Test
    void issuedAtIsPresent() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.getIssuedAt()).isEqualTo(ClockTestConfig.FIXED_INSTANT);
    }

    @Test
    void expirationIsPresent() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.getExpiresAt()).isNotNull();
    }

    @Test
    void tokenDurationIsOneHourWithFixedClock() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));

        assertThat(accessToken.expiresAt()).isEqualTo(ClockTestConfig.FIXED_INSTANT.plusSeconds(3600));
        assertThat(accessToken.expiresInSeconds()).isEqualTo(3600L);

        Jwt decoded = jwtDecoder.decode(accessToken.token());
        assertThat(decoded.getExpiresAt()).isEqualTo(ClockTestConfig.FIXED_INSTANT.plusSeconds(3600));
    }

    @Test
    void rolesAreExtractedFromRolePrefixedAuthorities() {
        Set<GrantedAuthority> authorities = authoritySet("ROLE_LIBRARIAN", "CATALOGUE_READ");

        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, authorities));
        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.<List<String>>getClaim("roles")).containsExactly("ROLE_LIBRARIAN");
    }

    @Test
    void permissionsAreExtractedFromNonRoleAuthorities() {
        Set<GrantedAuthority> authorities = authoritySet("ROLE_LIBRARIAN", "CATALOGUE_READ", "ARTICLE_PUBLISH");

        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, authorities));
        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.<List<String>>getClaim("permissions"))
                .containsExactlyInAnyOrder("CATALOGUE_READ", "ARTICLE_PUBLISH");
    }

    @Test
    void authoritiesAreDeduplicated() {
        // Reproduit le scénario réaliste DEV-03.5 : une permission accordée
        // par deux rôles différents ne doit apparaître qu'une seule fois.
        // Le Set<GrantedAuthority> garantit déjà l'absence de doublon en
        // amont (comme dans PrimatisUserDetailsService) ; ce test prouve que
        // la chaîne complète jusqu'au claim JWT préserve cette garantie.
        TreeSet<GrantedAuthority> authorities = new TreeSet<>(
                java.util.Comparator.comparing(GrantedAuthority::getAuthority));
        authorities.add(new SimpleGrantedAuthority("ROLE_LIBRARIAN"));
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        authorities.add(new SimpleGrantedAuthority("CATALOGUE_READ")); // accordée par ROLE_LIBRARIAN
        authorities.add(new SimpleGrantedAuthority("CATALOGUE_READ")); // et aussi par ROLE_ADMIN : même code

        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, authorities));
        Jwt decoded = jwtDecoder.decode(accessToken.token());

        assertThat(decoded.<List<String>>getClaim("permissions")).containsExactly("CATALOGUE_READ");
        assertThat(decoded.<List<String>>getClaim("roles")).containsExactlyInAnyOrder("ROLE_LIBRARIAN", "ROLE_ADMIN");
    }

    @Test
    void claimsContainNoSensitiveDataBeyondTheExpectedSet() {
        Set<GrantedAuthority> authorities = authoritySet("ROLE_MEMBER", "CATALOGUE_READ");

        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(7L, authorities));
        Jwt decoded = jwtDecoder.decode(accessToken.token());

        // Preuve par exhaustivité : uniquement les 7 claims attendus, rien
        // d'autre — donc ni passwordHash, ni failedLoginCount, ni
        // lockedUntil, ni adresse, ni aucune autre donnée privée.
        assertThat(decoded.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "aud", "roles", "permissions");
    }

    @Test
    void expiredTokenIsRejected() {
        Instant past = ClockTestConfig.FIXED_INSTANT.minusSeconds(7200);
        Jwt expiredJwt = signRawClaims(JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(past)
                .expiresAt(past.plusSeconds(60)) // expiré bien avant l'instant figé du test
                .build());

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> jwtDecoder.decode(expiredJwt.getTokenValue()));
    }

    @Test
    void wrongIssuerIsRejected() {
        Jwt wrongIssuerJwt = signRawClaims(JwtClaimsSet.builder()
                .issuer("attacker-issuer")
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(ClockTestConfig.FIXED_INSTANT)
                .expiresAt(ClockTestConfig.FIXED_INSTANT.plusSeconds(3600))
                .build());

        assertThatExceptionOfType(JwtException.class)
                .isThrownBy(() -> jwtDecoder.decode(wrongIssuerJwt.getTokenValue()));
    }

    @Test
    void wrongAudienceIsRejected() {
        Jwt wrongAudienceJwt = signRawClaims(JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of("some-other-api"))
                .subject("1")
                .issuedAt(ClockTestConfig.FIXED_INSTANT)
                .expiresAt(ClockTestConfig.FIXED_INSTANT.plusSeconds(3600))
                .build());

        assertThatExceptionOfType(JwtException.class)
                .isThrownBy(() -> jwtDecoder.decode(wrongAudienceJwt.getTokenValue()));
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws NoSuchAlgorithmException {
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
                .issuedAt(ClockTestConfig.FIXED_INSTANT)
                .expiresAt(ClockTestConfig.FIXED_INSTANT.plusSeconds(3600))
                .build();
        Jwt signedByOtherKey = otherEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));

        assertThatExceptionOfType(JwtException.class)
                .isThrownBy(() -> jwtDecoder.decode(signedByOtherKey.getTokenValue()));
    }

    @Test
    void tamperedTokenIsRejected() {
        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));
        String[] parts = accessToken.token().split("\\.");
        // Altère un caractère du payload (2e segment du JWS compact) : la
        // signature ne correspondra plus au contenu modifié.
        char[] payloadChars = parts[1].toCharArray();
        payloadChars[payloadChars.length / 2] = payloadChars[payloadChars.length / 2] == 'A' ? 'B' : 'A';
        String tamperedToken = parts[0] + "." + new String(payloadChars) + "." + parts[2];

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> jwtDecoder.decode(tamperedToken));
    }

    @Test
    void privateKeyIsNeverLogged() {
        // Clé privée en mémoire (JwtTestKeysConfig) : on compare directement
        // au contenu encodé de la clé, pas à un fichier.
        String privateKeyBase64 = java.util.Base64.getEncoder().encodeToString(rsaPrivateKey.getEncoded());

        AccessToken accessToken = jwtService.generateAccessToken(authenticationFor(1L, Set.of()));
        jwtDecoder.decode(accessToken.token());

        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(privateKeyBase64);
        }
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private Jwt signRawClaims(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));
    }

    private Set<GrantedAuthority> authoritySet(String... authorities) {
        TreeSet<GrantedAuthority> set = new TreeSet<>(java.util.Comparator.comparing(GrantedAuthority::getAuthority));
        for (String authority : authorities) {
            set.add(new SimpleGrantedAuthority(authority));
        }
        return set;
    }

    private Authentication authenticationFor(Long userId, Set<GrantedAuthority> authorities) {
        PrimatisUserPrincipal principal = new PrimatisUserPrincipal(
                userId, "user" + userId + "@primatis.test", "hash-not-relevant", true, authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
