package be.primatis.config;

import be.primatis.security.PrimatisUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Socle Spring Security minimal (DEV-03.2), encodage des mots de passe
 * (DEV-03.4) et chaîne d'authentification standard (DEV-03.6).
 *
 * Baseline stateless / JWT Bearer RS256 (architecture.md §5.3) : aucune
 * session HTTP d'authentification n'est maintenue, donc CSRF est désactivé
 * et la politique de session est STATELESS.
 *
 * IMPORTANT : le permitAll() ci-dessous est un échafaudage temporaire propre
 * à DEV-03.2, uniquement destiné à ne pas casser l'application pendant
 * l'installation du socle Spring Security, avant que le resource server JWT
 * (decoder RS256, endpoints publics vs authentifiés) ne soit implémenté dans
 * une étape ultérieure. Il devra être supprimé et remplacé par une politique
 * d'autorisation explicite dès que le décodage JWT sera en place.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
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
