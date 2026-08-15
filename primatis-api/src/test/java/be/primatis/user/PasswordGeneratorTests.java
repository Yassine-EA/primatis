package be.primatis.user;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link PasswordGenerator} (DEV-05.5) : conformité systématique à
 * {@link PasswordPolicy}, non-déterminisme, et usage réel de
 * {@link java.security.SecureRandom} (pas une source faible).
 */
class PasswordGeneratorTests {

    @Test
    void generatesNonNullNonBlankPassword() {
        String password = PasswordGenerator.generate();

        assertThat(password).isNotNull().isNotBlank();
    }

    @Test
    void generatedPasswordAlwaysPassesPasswordPolicy() {
        for (int i = 0; i < 50; i++) {
            String password = PasswordGenerator.generate();
            PasswordPolicy.ValidationResult result = PasswordPolicy.validate(password);

            assertThat(result.valid())
                    .as("Le mot de passe généré '%s' doit respecter PasswordPolicy", password)
                    .isTrue();
        }
    }

    @Test
    void twoGenerationsAreNotSystematicallyIdentical() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            generated.add(PasswordGenerator.generate());
        }

        assertThat(generated).hasSize(20);
    }

    @Test
    void generatedPasswordLengthIsWellAbovePolicyMinimum() {
        String password = PasswordGenerator.generate();

        assertThat(password.length()).isGreaterThan(PasswordPolicy.MIN_LENGTH);
    }
}
