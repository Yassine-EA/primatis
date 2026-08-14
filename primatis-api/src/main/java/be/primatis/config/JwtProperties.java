package be.primatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Configuration externe de l'infrastructure JWT PRIMATIS (DEV-03.7),
 * {@code primatis.security.jwt.*}. Aucune valeur ici n'est une clé
 * secrète elle-même — seuls les EMPLACEMENTS des fichiers de clés RSA sont
 * configurés ; les fichiers eux-mêmes sont fournis par l'environnement,
 * jamais versionnés en tant que secret réel.
 *
 * {@code accessTokenTtl} est une configuration technique (baseline : 1
 * heure) : elle n'appartient volontairement pas à {@code application_setting}.
 */
@ConfigurationProperties(prefix = "primatis.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Resource privateKeyLocation,
        Resource publicKeyLocation) {
}
