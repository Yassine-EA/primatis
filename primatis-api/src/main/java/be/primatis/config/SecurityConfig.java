package be.primatis.config;

import be.primatis.security.PrimatisAccessDeniedHandler;
import be.primatis.security.PrimatisAuthenticationEntryPoint;
import be.primatis.security.PrimatisInvalidTokenAuthenticationEntryPoint;
import be.primatis.security.PrimatisUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * SecurityFilterChain finale PRIMATIS (DEV-03.8) : resource server JWT
 * Bearer, stateless, CSRF désactivé, CORS explicite, surface publique
 * strictement définie, tout le reste authentifié. Complète le socle
 * DEV-03.2, l'encodage des mots de passe DEV-03.4, la chaîne
 * d'authentification username/password DEV-03.6 et l'infrastructure JWT
 * RS256 DEV-03.7. Les rejets 401/403 (DEV-03.10) utilisent le contrat
 * {@code ApiErrorResponse} commun via des
 * {@link org.springframework.security.web.AuthenticationEntryPoint}/
 * {@link org.springframework.security.web.access.AccessDeniedHandler}
 * dédiés — jamais la page HTML ou le corps par défaut de Spring Security.
 *
 * Baseline stateless / JWT Bearer RS256 (architecture.md §5.3) : aucune
 * session HTTP d'authentification n'est maintenue, donc CSRF est désactivé
 * et la politique de session est STATELESS.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    /**
     * Surface publique strictement limitée : login, et consultation
     * (GET uniquement) du catalogue et des articles. Toute autre méthode
     * HTTP sur ces mêmes ressources (POST/PUT/PATCH/DELETE) reste
     * authentifiée — jamais permitAll par commodité. Tout le reste :
     * authenticated(). Le permitAll() global temporaire DEV-03.2 a disparu.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper,
            Clock clock) throws Exception {
        // DEV-03.10 : deux AuthenticationEntryPoint distincts (401), voir
        // leur javadoc respective — aucune Authentication du tout vs Bearer
        // présent mais rejeté par le Resource Server — plus un
        // AccessDeniedHandler (403) pour un refus d'autorisation HTTP.
        PrimatisAuthenticationEntryPoint authenticationEntryPoint =
                new PrimatisAuthenticationEntryPoint(objectMapper, clock);
        PrimatisInvalidTokenAuthenticationEntryPoint invalidTokenAuthenticationEntryPoint =
                new PrimatisInvalidTokenAuthenticationEntryPoint(objectMapper, clock);
        PrimatisAccessDeniedHandler accessDeniedHandler = new PrimatisAccessDeniedHandler(objectMapper, clock);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Préflight CORS : ne doit jamais être bloqué par l'authentification.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/titles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/articles/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(invalidTokenAuthenticationEntryPoint)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Reconstruit les GrantedAuthority Spring Security à partir des deux
     * claims JWT distincts posés par {@code JwtService} (DEV-03.7) :
     * {@code roles} (déjà préfixées {@code ROLE_}) et {@code permissions}
     * (jamais préfixées). Composition de deux
     * {@link JwtGrantedAuthoritiesConverter} standards (préfixe vidé
     * explicitement sur les deux — sinon le préfixe par défaut
     * {@code SCOPE_} s'appliquerait) via
     * {@link DelegatingJwtGrantedAuthoritiesConverter} — aucun converter
     * maison. Le résultat combiné est déduplique via un
     * {@code LinkedHashSet} (ordre déterministe, préservant l'ordre déjà
     * trié des claims). {@code sub} reste l'identité du principal (défaut
     * de {@link JwtAuthenticationConverter}, rendu explicite ici).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("");

        JwtGrantedAuthoritiesConverter permissionsConverter = new JwtGrantedAuthoritiesConverter();
        permissionsConverter.setAuthoritiesClaimName("permissions");
        permissionsConverter.setAuthorityPrefix("");

        DelegatingJwtGrantedAuthoritiesConverter combined =
                new DelegatingJwtGrantedAuthoritiesConverter(rolesConverter, permissionsConverter);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> new LinkedHashSet<>(combined.convert(jwt)));
        converter.setPrincipalClaimName(JwtClaimNames.SUB);
        return converter;
    }

    /**
     * CORS explicite, centralisé ici uniquement (pas de configuration CORS
     * dispersée ailleurs). {@code allowCredentials = false} : PRIMATIS
     * n'utilise pas de cookie d'authentification (JWT Bearer stateless),
     * donc pas de nécessité de credentials CORS ; aucun wildcard {@code *}
     * général — origines explicitement listées via
     * {@link CorsProperties}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * DelegatingPasswordEncoder avec BCrypt comme encodage courant
     * (architecture.md §5.14) : format stocké préfixé {@code {bcrypt}},
     * strength par défaut non modifiée (aucun besoin démontré).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider explicite (API Spring Security 6.5, pas de
     * méthode dépréciée : constructeur prenant directement le
     * UserDetailsService, plutôt que le setter setUserDetailsService
     * déprécié) — délègue le chargement du compte à
     * {@link PrimatisUserDetailsService} et la vérification du mot de passe
     * au {@link PasswordEncoder} ci-dessus.
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            PrimatisUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * AuthenticationManager exposé explicitement pour être injectable dans
     * AuthService (DEV-03.6), construit à partir du seul
     * DaoAuthenticationProvider PRIMATIS ci-dessus.
     */
    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }
}
