package be.primatis.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.List;

/**
 * Construction partagée de {@link JwtEncoder}/{@link JwtDecoder} RS256 à
 * partir d'une paire de clés RSA déjà obtenue — indépendamment de la façon
 * dont ces clés ont été obtenues (fichiers PEM externes en production via
 * {@code JwtConfig}, paire générée en mémoire pour les tests via
 * {@code JwtTestKeysConfig}). Évite de dupliquer la composition des
 * validateurs (voir DEV-DEC-0007 : Clock applicatif injecté dans
 * {@link JwtTimestampValidator}) entre les deux configurations.
 *
 * Package-private : usage interne à {@code be.primatis.config} uniquement.
 */
final class JwtCryptoSupport {

    private JwtCryptoSupport() {
    }

    static JwtEncoder buildEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    static JwtDecoder buildDecoder(RSAPublicKey publicKey, String issuer, String audience, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);

        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", claimAudience -> claimAudience != null && claimAudience.contains(audience));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator, issuerValidator, audienceValidator));

        return decoder;
    }
}
