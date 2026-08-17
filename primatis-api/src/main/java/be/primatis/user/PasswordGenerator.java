package be.primatis.user;

import java.security.SecureRandom;

/**
 * Génère un mot de passe initial (DEV-05.5, création administrative et futur
 * reset administratif) à partir d'une source cryptographiquement sûre
 * ({@link SecureRandom}, jamais {@code Random}/{@code Math.random()}/UUID
 * tronqué/valeur dérivée d'un timestamp ou d'une donnée personnelle).
 *
 * {@value #GENERATED_LENGTH} caractères, bien au-delà de
 * {@link PasswordPolicy#MIN_LENGTH} (15) : garantit constructivement la
 * conformité à {@link PasswordPolicy} (longueur et absence de collision
 * avec la liste de mots de passe courants, tous nettement plus courts et
 * fixes) sans avoir à boucler sur des candidats. Une vérification défensive
 * via {@link PasswordPolicy#validate(String)} reste appliquée une seule
 * fois après génération, jamais en boucle.
 */
public final class PasswordGenerator {

    private static final int GENERATED_LENGTH = 24;

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%&*-_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate() {
        StringBuilder generated = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            generated.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        String candidate = generated.toString();

        PasswordPolicy.ValidationResult validation = PasswordPolicy.validate(candidate);
        if (!validation.valid()) {
            throw new IllegalStateException(
                    "Le générateur de mot de passe a produit une valeur non conforme à PasswordPolicy.");
        }
        return candidate;
    }
}
