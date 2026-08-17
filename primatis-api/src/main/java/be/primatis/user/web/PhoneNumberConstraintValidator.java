package be.primatis.user.web;

import be.primatis.user.PhoneNumberNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Délègue entièrement à {@link PhoneNumberNormalizer} (seule implémentation
 * autoritaire de parsing téléphonique, DEV-05.9) — aucune logique de
 * validation dupliquée ici. Bean géré par Spring : {@code
 * spring-boot-starter-validation} injecte les {@code ConstraintValidator}
 * via {@code SpringConstraintValidatorFactory} (autoconfiguration standard).
 */
public class PhoneNumberConstraintValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public PhoneNumberConstraintValidator(PhoneNumberNormalizer phoneNumberNormalizer) {
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    /**
     * {@code null}/blanc sont toujours valides (voir {@link ValidPhoneNumber}) :
     * seule une valeur non blanche est effectivement soumise à {@link
     * PhoneNumberNormalizer#normalizeToE164}.
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return phoneNumberNormalizer.normalizeToE164(value).isPresent();
    }
}
