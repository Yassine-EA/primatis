package be.primatis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;

/**
 * Infrastructure JWT RS256 PRIMATIS (DEV-03.7) : chargement des clés RSA
 * DEV/production depuis des fichiers PEM externes (chemins fournis par
 * {@link JwtProperties} via variables d'environnement, jamais un contenu de
 * clé en dur), {@link JwtEncoder} (signature) et {@link JwtDecoder}
 * (vérification signature + expiration + issuer + audience) construits
 * exclusivement à partir des composants standards Spring Security / Nimbus
 * déjà présents via spring-boot-starter-oauth2-resource-server — aucune
 * implémentation cryptographique ni validation JWT maison.
 *
 * RS256 uniquement, jamais HS256 implicite (algorithme fixé explicitement
 * dans {@link JwtCryptoSupport}).
 *
 * {@code @Profile("!test")} : sous le profil "test", {@code JwtTestKeysConfig}
 * (src/test) fournit les mêmes types de beans à partir d'une paire RSA
 * générée en mémoire — aucune clé, même de test, n'est versionnée dans le
 * dépôt (contexte maître §7.27). Le mécanisme runtime DEV/production
 * ci-dessous reste inchangé et n'est jamais actif pendant les tests.
 *
 * Classe séparée de {@link SecurityConfig} : ces beans ne doivent pas
 * devenir une dépendance implicite des tests {@code @WebMvcTest} qui
 * n'importent que {@code SecurityConfig} (voir l'incident DEV-03.6 sur
 * {@code GlobalExceptionHandlerTests}).
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public RSAPrivateKey rsaPrivateKey(JwtProperties properties) {
        byte[] decoded = decodePem(properties.privateKeyLocation());
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Impossible de charger la clé privée RSA JWT.", ex);
        }
    }

    @Bean
    public RSAPublicKey rsaPublicKey(JwtProperties properties) {
        byte[] decoded = decodePem(properties.publicKeyLocation());
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Impossible de charger la clé publique RSA JWT.", ex);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
        return JwtCryptoSupport.buildEncoder(rsaPublicKey, rsaPrivateKey);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey, JwtProperties properties, Clock clock) {
        return JwtCryptoSupport.buildDecoder(rsaPublicKey, properties.issuer(), properties.audience(), clock);
    }

    /**
     * Extrait le contenu base64 d'un fichier PEM (clé publique ou privée),
     * en ignorant tout ce qui se trouve avant {@code -----BEGIN...-----} ou
     * après {@code -----END...-----}. Ne réalise aucune opération
     * cryptographique — uniquement un parsing de format texte, préalable
     * nécessaire à {@link KeyFactory}.
     */
    private byte[] decodePem(Resource resource) {
        String content;
        try (var inputStream = resource.getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Impossible de lire le fichier de clé RSA JWT : " + resource, ex);
        }

        StringBuilder base64 = new StringBuilder();
        boolean insidePemBlock = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("-----BEGIN")) {
                insidePemBlock = true;
                continue;
            }
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("-----END")) {
                break;
            }
            if (insidePemBlock) {
                base64.append(trimmed);
            }
        }
        return Base64.getDecoder().decode(base64.toString());
    }
}
