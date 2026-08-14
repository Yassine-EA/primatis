package be.primatis.security;

import be.primatis.config.JwtProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

/**
 * Génère l'access token JWT PRIMATIS (DEV-03.7) à partir du principal/
 * Authentication déjà authentifié par {@code AuthService.login()}. Ne
 * mélange pas la gestion du lockout (qui reste dans {@code AuthService})
 * avec la génération du token.
 *
 * Toutes les décisions temporelles utilisent le {@link Clock} injecté,
 * jamais {@code Instant.now()} directement.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public AccessToken generateAccessToken(Authentication authentication) {
        PrimatisUserPrincipal principal = (PrimatisUserPrincipal) authentication.getPrincipal();

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());

        List<String> roles = extractByPrefix(principal, true);
        List<String> permissions = extractByPrefix(principal, false);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(principal.getUserId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return new AccessToken(
                jwt.getTokenValue(), "Bearer", expiresAt, jwtProperties.accessTokenTtl().getSeconds());
    }

    /**
     * Les authorities commençant par {@code ROLE_} deviennent des rôles, les
     * autres des permissions — aucune matrice codée en dur, tout dérive des
     * GrantedAuthority déjà construites en DEV-03.5. {@link TreeSet} garantit
     * un ordre déterministe et l'absence de doublon, indépendamment de
     * l'ordre de {@code principal.getAuthorities()}.
     */
    private List<String> extractByPrefix(PrimatisUserPrincipal principal, boolean roles) {
        TreeSet<String> values = new TreeSet<>();
        for (GrantedAuthority authority : principal.getAuthorities()) {
            String value = authority.getAuthority();
            boolean isRole = value.startsWith("ROLE_");
            if (isRole == roles) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }
}
