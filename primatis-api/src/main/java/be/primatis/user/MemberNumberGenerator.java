package be.primatis.user;

import be.primatis.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Génère le prochain {@code memberNumber} (DEV-05.5) au format {@code "M"} +
 * 9 chiffres ({@code M000000001} à {@code M999999999}), à partir de la
 * séquence PostgreSQL dédiée {@code member_number_seq} (V003) : {@code
 * nextval()} garantit l'unicité et la non-réutilisation, y compris en cas
 * d'accès concurrent (jamais {@code SELECT MAX(member_number)+1}, parsing
 * du dernier numéro, ou comptage de lignes — interdit explicitement).
 *
 * Requête native minimale via {@link EntityManager} ({@code nextval()} est
 * intrinsèquement spécifique à PostgreSQL, baseline figée de PRIMATIS)
 * plutôt qu'introduire {@code JdbcTemplate}, absent du reste du projet —
 * Implementation Freedom, cf. {@code decision-log.md}.
 */
@Component
public class MemberNumberGenerator {

    private static final int MEMBER_NUMBER_DIGITS = 9;
    private static final long MAX_MEMBER_NUMBER = 999_999_999L;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Détecte explicitement le dépassement de capacité (au-delà de
     * {@code M999999999}) plutôt que de produire un format invalide.
     */
    public String generateNext() {
        Number nextValue = (Number) entityManager
                .createNativeQuery("SELECT nextval('member_number_seq')")
                .getSingleResult();
        long value = nextValue.longValue();
        if (value > MAX_MEMBER_NUMBER) {
            throw new BusinessRuleException(
                    "MEMBER_NUMBER_SEQUENCE_EXHAUSTED",
                    "La séquence de numéros d'adhérent a atteint sa capacité maximale.");
        }
        return "M" + String.format("%0" + MEMBER_NUMBER_DIGITS + "d", value);
    }
}
