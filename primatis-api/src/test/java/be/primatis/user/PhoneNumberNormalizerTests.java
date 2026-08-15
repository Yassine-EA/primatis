package be.primatis.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link PhoneNumberNormalizer} (DEV-05.9) : parsing/validation/
 * normalisation via {@code libphonenumber}, région par défaut {@code BE}
 * uniquement sans indicatif explicite, numéro international interprété
 * selon son propre indicatif.
 *
 * Tests unitaires purs, sans Spring, sans PostgreSQL — même principe que
 * {@code PasswordPolicyTests}.
 */
class PhoneNumberNormalizerTests {

    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();

    @Test
    void nullIsEmpty() {
        assertThat(normalizer.normalizeToE164(null)).isEmpty();
    }

    @Test
    void blankIsEmpty() {
        assertThat(normalizer.normalizeToE164("   ")).isEmpty();
    }

    @Test
    void emptyStringIsEmpty() {
        assertThat(normalizer.normalizeToE164("")).isEmpty();
    }

    @Test
    void obviouslyInvalidValueIsEmpty() {
        assertThat(normalizer.normalizeToE164("not-a-phone-number")).isEmpty();
    }

    @Test
    void tooShortNationalNumberIsEmpty() {
        assertThat(normalizer.normalizeToE164("12")).isEmpty();
    }

    @Test
    void belgianNationalMobileNumberIsNormalizedToE164() {
        assertThat(normalizer.normalizeToE164("0470 12 34 56")).contains("+32470123456");
    }

    @Test
    void belgianNationalMobileNumberWithoutSpacesIsNormalizedToE164() {
        assertThat(normalizer.normalizeToE164("0470123456")).contains("+32470123456");
    }

    @Test
    void explicitInternationalFrenchNumberIsInterpretedByItsOwnCountryCode() {
        assertThat(normalizer.normalizeToE164("+33 6 12 34 56 78")).contains("+33612345678");
    }

    @Test
    void explicitInternationalGermanNumberIsInterpretedByItsOwnCountryCode() {
        assertThat(normalizer.normalizeToE164("+49 151 23456789")).contains("+4915123456789");
    }

    @Test
    void alreadyE164BelgianNumberRemainsUnchanged() {
        assertThat(normalizer.normalizeToE164("+32470123456")).contains("+32470123456");
    }
}
