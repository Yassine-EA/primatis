package be.primatis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vérifie le socle technique de persistance : démarrage Spring Boot,
 * exécution des migrations Flyway et validation du schéma par Hibernate
 * contre une instance PostgreSQL réelle.
 *
 * Le profil "test" charge application-test.yml, qui cible explicitement
 * primatis_test et ne doit jamais pointer vers la base de développement.
 */
@SpringBootTest
@ActiveProfiles("test")
class PrimatisApplicationTests {

    @Test
    void contextLoads() {
    }
}
