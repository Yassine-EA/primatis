package be.primatis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Expose les images d'Articles stockées localement
 * ({@link ArticleMediaProperties#storagePath()}) sous {@code
 * /media/articles/**} (DEV-ARTICLES-MEDIA, DEV-DEC-0079) — jamais
 * l'ensemble du filesystem, uniquement ce dossier dédié. {@code
 * SecurityConfig} complète ce mapping par un {@code permitAll} en
 * lecture (GET) sur ce même préfixe : une image intégrée au contenu
 * HTML d'un Article publié doit rester visible à un visiteur anonyme,
 * même précédent exact que {@code GET /api/v1/articles/**}.
 */
@Configuration
@EnableConfigurationProperties(ArticleMediaProperties.class)
public class ArticleMediaWebConfig implements WebMvcConfigurer {

    private final ArticleMediaProperties properties;

    public ArticleMediaWebConfig(ArticleMediaProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(properties.storagePath()).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler(ArticleMediaProperties.PUBLIC_PATH_PREFIX + "**")
                .addResourceLocations(location);
    }
}
