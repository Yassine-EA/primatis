package be.primatis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI PRIMATIS (DEV-03.12, {@code springdoc}) : déclare le
 * schéma de sécurité {@code bearerAuth} correspondant au JWT Bearer RS256
 * réel (DEV-03.7/03.8) — {@code Authorization: Bearer <JWT>}. Ne modifie ni
 * le mécanisme JWT ({@code JwtService}/{@code JwtConfig}) ni la
 * {@code SecurityFilterChain}.
 *
 * Convention retenue pour les futurs Controllers (aucun Controller métier
 * de production n'existe encore) : {@code bearerAuth} est appliqué par
 * défaut à toute opération ({@link #addSecurityItem}, portée globale),
 * conformément à la baseline « tout est authentifié sauf exception ».
 * Les endpoints publics (DEV-03.8 : {@code POST /api/v1/auth/login},
 * {@code GET /api/v1/titles/**}, {@code GET /api/v1/articles/**}) devront
 * lever explicitement cette exigence sur leur opération, par exemple avec
 * {@code @SecurityRequirements} (liste vide) de springdoc/Swagger — jamais
 * en retirant {@code bearerAuth} globalement.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI primatisOpenApi() {
        SecurityScheme bearerAuthScheme = new SecurityScheme()
                .name(BEARER_AUTH_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Authentification par JWT Bearer RS256 : "
                        + "en-tête Authorization: Bearer <token>.");

        return new OpenAPI()
                .info(new Info()
                        .title("PRIMATIS API")
                        .description("API REST de gestion de bibliothèque PRIMATIS.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME_NAME, bearerAuthScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME_NAME));
    }
}
