package be.primatis;

import be.primatis.user.Country;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prouve que Flyway reconstruit intégralement le schéma PRIMATIS depuis une
 * base primatis_test vidée — PostgreSQL réel, aucun Docker/Testcontainers.
 *
 * spring.flyway.clean-disabled=false n'est activé QUE dans le contexte
 * Spring dédié à cette classe (@TestPropertySource local à ce test), donc
 * Spring Boot démarre un ApplicationContext séparé rien que pour elle :
 * aucune autre classe de test ne partage ce réglage, application.yml et
 * application-test.yml restent inchangés, et primatis_dev n'est jamais visé
 * (la datasource du profil "test" cible exclusivement primatis_test).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.clean-disabled=false")
@Transactional
class FlywaySchemaRebuildTests {

    @Autowired
    private Flyway flyway;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void rebuildsSchemaFromEmptyDatabase() {
        flyway.clean();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(result.targetSchemaVersion).isEqualTo("001");

        long tableCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'")
                .getSingleResult()).longValue();
        assertThat(tableCount).isEqualTo(23);

        long settingCount = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM application_setting")
                .getSingleResult()).longValue();
        assertThat(settingCount).isEqualTo(4);

        Object loanDurationDays = entityManager
                .createNativeQuery("SELECT setting_value FROM application_setting WHERE setting_key = 'LOAN_DURATION_DAYS'")
                .getSingleResult();
        assertThat(loanDurationDays).isEqualTo("21");

        // Hibernate doit rester capable d'écrire contre le schéma fraîchement
        // reconstruit (ddl-auto=validate a déjà été vérifié au démarrage du
        // contexte ; cette écriture confirme que le mapping reste compatible
        // après le clean/migrate déclenché explicitement par ce test).
        Country country = new Country();
        country.setName("Pays reconstruction test");
        country.setCode("RB" + (System.nanoTime() % 100000));
        entityManager.persist(country);
        entityManager.flush();
        assertThat(country.getId()).isNotNull();
    }
}
