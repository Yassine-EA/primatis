package be.primatis.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link MemberNumberGenerator} contre PostgreSQL réel (séquence
 * {@code member_number_seq}, V003). N'affirme volontairement aucune valeur
 * absolue (ex. {@code M000000001}) : la séquence est partagée par toute la
 * suite de tests et son état dépend de l'ordre d'exécution global — seule
 * {@code FlywaySchemaRebuildTests} (contexte isolé, rebuild propre) vérifie
 * la première valeur. Ici, seul le comportement relatif est prouvé :
 * format, incrément strict, unicité entre deux appels consécutifs.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberNumberGeneratorTests {

    @Autowired
    private MemberNumberGenerator memberNumberGenerator;

    @Test
    void generatesValueMatchingExpectedFormat() {
        String memberNumber = memberNumberGenerator.generateNext();

        assertThat(memberNumber).matches("^M[0-9]{9}$");
    }

    @Test
    void consecutiveCallsProduceDistinctIncrementingValues() {
        String first = memberNumberGenerator.generateNext();
        String second = memberNumberGenerator.generateNext();

        long firstValue = Long.parseLong(first.substring(1));
        long secondValue = Long.parseLong(second.substring(1));

        assertThat(second).isNotEqualTo(first);
        assertThat(secondValue).isEqualTo(firstValue + 1);
    }
}
