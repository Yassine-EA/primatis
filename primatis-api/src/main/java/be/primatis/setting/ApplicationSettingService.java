package be.primatis.setting;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.setting.web.SettingResponse;
import be.primatis.user.AppUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

/**
 * Point d'accès métier unique à {@code application_setting} (backend.md
 * « Application settings » : « Ne pas lire directement application_setting
 * depuis chaque Service métier. Ne pas dupliquer le parsing. »). Introduit
 * par DEV-07.5, premier consommateur réel ({@code LOAN_DURATION_DAYS}).
 *
 * <p>{@link #getInteger(String)} (DEV-07.5, clés {@code INTEGER}) et
 * {@link #getDecimal(String)} (DEV-09.3, clés {@code FINE_WEEKLY_RATE}/
 * {@code FINE_MAX_AMOUNT}, DEV-DEC-0046) sont implémentés — aucun
 * {@code BOOLEAN}/{@code STRING} réel n'existe à ce jour. Les accesseurs
 * correspondants (API conceptuelle complète décrite par
 * PRIMATIS_CONTEXT_DEV_v1.0.md §13.14) seront ajoutés lorsqu'une clé réelle
 * de ce type l'exigera, conformément à la documentation interne du projet §10.2 (pas d'architecture
 * spéculative).
 *
 * <p>Aucun fallback silencieux (§13.16) : une clé absente ou un
 * {@code value_type}/{@code setting_value} incohérent est une erreur de
 * configuration serveur, jamais masquée par une valeur codée en dur ici —
 * traduite en 500 {@code INTERNAL_ERROR} par {@code GlobalExceptionHandler}
 * (le client n'est jamais responsable d'une configuration manquante).
 *
 * <p>{@link #listSettings()}/{@link #updateSettingValue(String, String, Long)}
 * (DEV-12.2) exposent l'administration ({@code SETTING_READ}/{@code
 * SETTING_MANAGE}, {@code ROLE_ADMIN} uniquement — bootstrap V002) —
 * {@code @PreAuthorize} au niveau Service, jamais au niveau Controller,
 * même précédent que {@code UserService} (DEV-12.1 §11).
 */
@Service
public class ApplicationSettingService {

    private static final String SETTING_NOT_FOUND_CODE = "SETTING_NOT_FOUND";
    private static final String INTEGER_TYPE = "INTEGER";
    private static final String DECIMAL_TYPE = "DECIMAL";

    private final ApplicationSettingRepository applicationSettingRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public ApplicationSettingService(
            ApplicationSettingRepository applicationSettingRepository,
            AppUserRepository appUserRepository,
            Clock clock) {
        this.applicationSettingRepository = applicationSettingRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int getInteger(String key) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Paramètre applicatif obligatoire absent : " + key));

        if (!"INTEGER".equals(setting.getValueType())) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " n'est pas de type INTEGER (value_type=" + setting.getValueType() + ").");
        }

        try {
            return Integer.parseInt(setting.getSettingValue());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " contient une valeur INTEGER invalide : " + setting.getSettingValue(), e);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Paramètre applicatif obligatoire absent : " + key));

        if (!"DECIMAL".equals(setting.getValueType())) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " n'est pas de type DECIMAL (value_type=" + setting.getValueType() + ").");
        }

        try {
            return new BigDecimal(setting.getSettingValue());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Paramètre applicatif " + key
                    + " contient une valeur DECIMAL invalide : " + setting.getSettingValue(), e);
        }
    }

    /**
     * Consultation administrative (DEV-12.2, {@code GET /api/v1/settings}) :
     * les six paramètres existants, triés par {@code settingKey} (ordre
     * déterministe, {@link ApplicationSettingRepository#findAllByOrderBySettingKeyAsc}).
     */
    @PreAuthorize("hasAuthority('SETTING_READ')")
    @Transactional(readOnly = true)
    public List<SettingResponse> listSettings() {
        return applicationSettingRepository.findAllByOrderBySettingKeyAsc().stream()
                .map(SettingResponse::from)
                .toList();
    }

    /**
     * Modification administrative (DEV-12.2, {@code PATCH
     * /api/v1/settings/{settingKey}}) de la seule {@code settingValue} d'une
     * clé existante. {@code settingKey}/{@code valueType}/{@code
     * description} ne sont jamais modifiés par ce workflow — hors scope
     * DEV-12.2 (mandat §5 : aucune modification de clé, aucune modification
     * de type, aucune modification de description). Aucune création de clé
     * : une clé absente échoue en {@link ResourceNotFoundException} (404),
     * jamais une création implicite.
     *
     * <p>{@code rawValue} est normalisé (trim) avant validation/persistance
     * — normalisation purement technique, aucune règle métier nouvelle
     * (mandat §9). La validation dépend du {@code value_type} déjà persisté
     * de la clé : entier ou décimal strictement positif pour les six clés
     * réelles actuelles ; tout autre {@code value_type} (schéma autorisant
     * {@code BOOLEAN}/{@code STRING}, aucune clé V1 réelle) échoue
     * explicitement, jamais un fallback silencieux (mandat §9, même
     * principe que {@link #getInteger(String)}/{@link #getDecimal(String)}).
     *
     * <p>Aucun verrou pessimiste : mutation administrative mono-ligne, même
     * précédent que {@code UserService#updateAccountStatus} (aucun invariant
     * multi-lignes à protéger, contrairement à Copy/Loan/Reservation —
     * backend.md « Concurrency » ne cite que ces trois domaines critiques).
     */
    @PreAuthorize("hasAuthority('SETTING_MANAGE')")
    @Transactional
    public SettingResponse updateSettingValue(String settingKey, String rawValue, Long actingUserId) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(settingKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        SETTING_NOT_FOUND_CODE, "Aucun paramètre applicatif pour la clé " + settingKey + "."));

        String normalizedValue = rawValue.trim();
        validateValueForType(settingKey, setting.getValueType(), normalizedValue);

        setting.setSettingValue(normalizedValue);
        setting.setUpdatedAt(clock.instant());
        setting.setUpdatedByUser(appUserRepository.getReferenceById(actingUserId));

        return SettingResponse.from(setting);
    }

    private void validateValueForType(String settingKey, String valueType, String value) {
        switch (valueType) {
            case INTEGER_TYPE -> validatePositiveInteger(settingKey, value);
            case DECIMAL_TYPE -> validatePositiveDecimal(settingKey, value);
            default -> throw new BusinessRuleException("SETTING_VALUE_TYPE_NOT_SUPPORTED",
                    "Le paramètre " + settingKey + " est de type " + valueType
                            + ", non pris en charge par la modification administrative en V1.");
        }
    }

    private void validatePositiveInteger(String settingKey, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_INTEGER",
                    "La valeur de " + settingKey + " doit être un entier : " + value + ".");
        }
        if (parsed <= 0) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_POSITIVE",
                    "La valeur de " + settingKey + " doit être strictement positive.");
        }
    }

    private void validatePositiveDecimal(String settingKey, String value) {
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_DECIMAL",
                    "La valeur de " + settingKey + " doit être un nombre décimal : " + value + ".");
        }
        if (parsed.signum() <= 0) {
            throw new BusinessRuleException("SETTING_VALUE_NOT_POSITIVE",
                    "La valeur de " + settingKey + " doit être strictement positive.");
        }
    }
}
