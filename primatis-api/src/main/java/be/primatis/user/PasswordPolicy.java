package be.primatis.user;

import java.util.Locale;
import java.util.Set;

/**
 * Politique de mot de passe PRIMATIS, centralisée et réutilisable par tout
 * futur workflow créant ou modifiant un mot de passe (création
 * administrative d'{@link AppUser}, futur changement de mot de passe).
 *
 * Règles figées (contexte maître, project-context.md) :
 * <ul>
 *   <li>longueur minimale {@value #MIN_LENGTH} caractères ;</li>
 *   <li>aucune longueur maximale imposée artificiellement — les mots de
 *       passe longs sont acceptés sans troncature silencieuse ;</li>
 *   <li>aucune exigence arbitraire de majuscule, chiffre ou symbole ;</li>
 *   <li>rejet des mots de passe extrêmement courants/prévisibles.</li>
 * </ul>
 *
 * LIMITE DOCUMENTÉE : la détection des mots de passe courants repose sur une
 * liste locale déterministe, volontairement restreinte pour DEV-03.4 (pas de
 * dépendance externe type zxcvbn, pas d'appel réseau type Have I Been
 * Pwned). Cette liste ne constitue donc pas une vérification exhaustive des
 * mots de passe compromis ; une telle vérification nécessiterait une source
 * externe, hors périmètre de cette étape.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 15;

    // NOTE : MIN_LENGTH (15) filtre déjà la plupart des mots de passe
    // courants classiques (généralement < 12 caractères). Cette liste inclut
    // donc volontairement des variantes "longues" réellement observées dans
    // les compilations publiques de fuites (mots de passe courants étirés ou
    // "keyboard walks" longs), pour que le rejet par liste reste une règle
    // effective et pas seulement théorique une fois la longueur minimale
    // franchie.
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123",
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "qwerty", "qwertyuiop", "azerty",
            "letmein", "iloveyou", "welcome", "welcome123",
            "admin", "admin123", "changeme",
            "motdepasse", "motdepasse1", "bienvenue123",
            "password1234567", "password12345678",
            "1qaz2wsx3edc4rfv", "qwertyuiopasdfghjkl",
            "iloveyou12345678");

    private PasswordPolicy() {
    }

    /**
     * Valide {@code rawPassword} contre la politique PRIMATIS. Ne journalise
     * jamais la valeur du mot de passe.
     */
    public static ValidationResult validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return ValidationResult.rejected("Le mot de passe est obligatoire.");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            return ValidationResult.rejected(
                    "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (isCommon(rawPassword)) {
            return ValidationResult.rejected("Ce mot de passe est trop courant ou prévisible.");
        }
        return ValidationResult.accepted();
    }

    private static boolean isCommon(String rawPassword) {
        return COMMON_PASSWORDS.contains(rawPassword.toLowerCase(Locale.ROOT));
    }

    /**
     * Résultat de validation, réutilisable par un futur Service pour
     * construire une réponse d'erreur ({@code reason} vaut {@code null}
     * lorsque {@code valid} est {@code true}).
     */
    public record ValidationResult(boolean valid, String reason) {

        public static ValidationResult accepted() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
