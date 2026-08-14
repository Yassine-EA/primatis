package be.primatis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Socle Spring Security minimal (DEV-03.2).
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
}
