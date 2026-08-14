package be.primatis.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la politique de mot de passe PRIMATIS (DEV-03.4) : longueur
 * minimale 15, aucune longueur maximale artificielle, aucune exigence de
 * complexité arbitraire, rejet des mots de passe extrêmement courants.
 *
 * Tests unitaires purs, sans Spring, sans PostgreSQL : {@link PasswordPolicy}
 * est une classe statique déterministe.
 */
class PasswordPolicyTests {

    @Test
    void nullPasswordIsRejected() {
        PasswordPolicy.ValidationResult result = PasswordPolicy.validate(null);
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void emptyPasswordIsRejected() {
        assertThat(PasswordPolicy.validate("").valid()).isFalse();
    }

    @Test
    void fourteenCharactersIsRejected() {
        String password = "a".repeat(14);
        assertThat(PasswordPolicy.validate(password).valid()).isFalse();
    }

    @Test
    void fifteenCharactersIsAccepted() {
        String password = "correct-horse-b"; // 15 caractères, absent de la liste des mots de passe courants
        assertThat(password).hasSize(15);
        assertThat(PasswordPolicy.validate(password).valid()).isTrue();
    }

    @Test
    void sixtyFourCharactersIsAccepted() {
        String password = "z" + "x".repeat(63); // 64 caractères
        assertThat(password).hasSize(64);
        assertThat(PasswordPolicy.validate(password).valid()).isTrue();
    }

    @Test
    void doesNotArtificiallyRejectPasswordsLongerThanSixtyFourCharacters() {
        String password = "y" + "w".repeat(199); // 200 caractères, bien au-delà de 64
        assertThat(PasswordPolicy.validate(password).valid()).isTrue();
    }

    @Test
    void extremelyCommonPasswordIsRejectedEvenWhenLongEnough() {
        // "password12345678" (17 caractères) dépasse MIN_LENGTH mais figure
        // dans la liste des mots de passe courants connus : le rejet prouve
        // que la règle "mot de passe courant" s'applique indépendamment de
        // la règle de longueur, pas seulement en théorie.
        String commonLongPassword = "password12345678";
        assertThat(commonLongPassword.length()).isGreaterThanOrEqualTo(PasswordPolicy.MIN_LENGTH);

        PasswordPolicy.ValidationResult result = PasswordPolicy.validate(commonLongPassword);
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void commonPasswordCheckIsCaseInsensitive() {
        assertThat(PasswordPolicy.validate("PASSWORD12345678").valid()).isFalse();
    }

    @Test
    void doesNotRequireUppercaseDigitOrSymbol() {
        String allLowercaseNoDigitNoSymbol = "juste-des-lettres-minuscules";
        assertThat(PasswordPolicy.validate(allLowercaseNoDigitNoSymbol).valid()).isTrue();
    }
}
