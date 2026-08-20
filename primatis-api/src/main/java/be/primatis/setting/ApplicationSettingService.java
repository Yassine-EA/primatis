package be.primatis.setting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
 */
@Service
public class ApplicationSettingService {

    private final ApplicationSettingRepository applicationSettingRepository;

    public ApplicationSettingService(ApplicationSettingRepository applicationSettingRepository) {
        this.applicationSettingRepository = applicationSettingRepository;
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
}
