package be.primatis.user;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Point unique d'intégration de {@code libphonenumber} dans PRIMATIS
 * (DEV-05.9). Seule implémentation autoritaire de parsing/validation/
 * normalisation téléphonique — réutilisée à l'identique par {@link
 * be.primatis.user.web.PhoneNumberConstraintValidator} (validation DTO,
 * 400) et par {@link MeProfileService} (normalisation avant persistance) :
 * aucune logique de parsing dupliquée.
 *
 * Région par défaut {@code BE}, appliquée uniquement lorsque la saisie ne
 * contient pas d'indicatif international explicite (comportement natif de
 * {@link PhoneNumberUtil#parse}) — jamais dérivée de {@code Residence}/
 * {@code Country} (DEV-05.9-DEC-07).
 */
@Component
public class PhoneNumberNormalizer {

    private static final String DEFAULT_REGION = "BE";

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    /**
     * @return le numéro normalisé au format E.164 si {@code rawNumber} est
     * un numéro valide, {@link Optional#empty()} si {@code rawNumber} est
     * {@code null}/blanc ou n'est pas un numéro de téléphone valide.
     */
    public Optional<String> normalizeToE164(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return Optional.empty();
        }
        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(rawNumber, DEFAULT_REGION);
            if (!phoneNumberUtil.isValidNumber(parsed)) {
                return Optional.empty();
            }
            return Optional.of(phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException ex) {
            return Optional.empty();
        }
    }
}
