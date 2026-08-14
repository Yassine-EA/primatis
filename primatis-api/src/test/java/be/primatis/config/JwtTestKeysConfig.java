package be.primatis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

/**
 * Substitut de test de {@link JwtConfig} : mêmes types de beans
 * ({@link RSAPrivateKey}, {@link RSAPublicKey}, {@link JwtEncoder},
 * {@link JwtDecoder}), mais paire RSA générée EN MÉMOIRE
 * ({@link KeyPairGenerator}, API JDK standard, aucune dépendance
 * supplémentaire) — aucun fichier de clé, aucune écriture sur disque,
 * conformément au contexte maître §7.27 (« clé privée JWT → ne doit jamais
 * être versionnée »).
 *
 * Classe {@code @Configuration} normale (pas {@code @TestConfiguration}) :
 * elle doit être détectée automatiquement par le component scan de TOUS les
 * {@code @SpringBootTest} du profil "test" (pas seulement les tests JWT),
 * puisque {@code JwtConfig} lui-même est component-scanné globalement.
 * {@code @Profile("test")} / {@code @Profile("!test")} sur {@link JwtConfig}
 * garantit qu'une seule des deux configurations est active à la fois.
 *
 * La paire de clés est un bean singleton : générée une seule fois par
 * contexte Spring, donc stable pendant toute l'exécution d'une classe de
 * test (JwtEncoder et JwtDecoder utilisent la même paire).
 */
@Configuration
@Profile("test")
@EnableConfigurationProperties(JwtProperties.class)
class JwtTestKeysConfig {

    @Bean
    KeyPair testJwtKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    RSAPrivateKey rsaPrivateKey(KeyPair testJwtKeyPair) {
        return (RSAPrivateKey) testJwtKeyPair.getPrivate();
    }

    @Bean
    RSAPublicKey rsaPublicKey(KeyPair testJwtKeyPair) {
        return (RSAPublicKey) testJwtKeyPair.getPublic();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
        return JwtCryptoSupport.buildEncoder(rsaPublicKey, rsaPrivateKey);
    }

    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey, JwtProperties properties, Clock clock) {
        return JwtCryptoSupport.buildDecoder(rsaPublicKey, properties.issuer(), properties.audience(), clock);
    }
}
