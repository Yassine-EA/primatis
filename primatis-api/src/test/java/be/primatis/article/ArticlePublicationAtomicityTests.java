package be.primatis.article;

import be.primatis.notification.Notification;
import be.primatis.notification.NotificationService;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Preuve d'atomicité réelle pour DEV-11.7 (architecture.md §7.2 : « publish
 * Article + expected Notification creation » — workflow atomique). {@link
 * NotificationService} est remplacé par un espion ({@code
 * @MockitoSpyBean}, délègue au comportement réel sauf stub explicite) pour
 * provoquer de façon déterministe un échec au milieu du fanout — jamais un
 * simple mock qui se contenterait de vérifier qu'une exception a été levée
 * (mission DEV-11.7 §15) : la preuve porte sur une relecture réelle de
 * PostgreSQL <em>après</em> l'échec, dans une transaction séparée
 * ({@link TransactionTemplate}), confirmant que rien n'a été committé —
 * même principe que {@code CopyLockConcurrencyTests}/{@code
 * ResidenceServiceConcurrencyTests} pour la vérification post-transaction.
 *
 * <p>Classe dédiée (jamais fusionnée à {@code ArticleServiceTests}) : {@code
 * @MockitoSpyBean} remplace le bean {@link NotificationService} pour tout
 * le contexte Spring de cette classe — même principe d'isolation que {@code
 * LoanDeadlineSchedulerTests} (« contexte partagé... donc son propre »
 * fichier), pour ne jamais affecter les tests de fanout réel d'{@code
 * ArticleServiceTests}. {@code maximum-pool-size} réduit à 2 (jamais plus
 * d'une connexion réellement utilisée ici, aucune concurrence testée dans
 * cette classe) — même mécanisme exact que {@code
 * LoanServiceConcurrencyTests} (qui l'augmente à 25 pour la raison inverse),
 * appliqué ici dans l'autre sens : limiter l'empreinte cumulée des contextes
 * Spring à contexte distinct (chacun avec son propre pool HikariCP) sur le
 * nombre total de connexions PostgreSQL simultanées pendant le gate complet
 * — un défaut d'exécution réel a été observé pendant DEV-11.7 (« remaining
 * connection slots are reserved for roles with the SUPERUSER attribute »)
 * une fois ce nouveau contexte distinct ajouté à la suite déjà existante.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class ArticlePublicationAtomicityTests {

    @Autowired
    private ArticleService articleService;

    @MockitoSpyBean
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Deux membres {@code ACTIVE} : la première diffusion réussit (délègue
     * réellement à {@link NotificationService#createForArticle}, donc une
     * ligne {@code Notification} est physiquement écrite dans le contexte
     * de persistance de la transaction en cours), la seconde échoue
     * délibérément — la transaction entière (mutation Article + les deux
     * tentatives de Notification) doit alors être annulée par Spring
     * ({@code @Transactional} de {@link ArticleService#publishArticle}),
     * y compris la première Notification déjà « écrite » côté Hibernate
     * mais jamais committée.
     */
    @Test
    void publishArticleRollsBackEntirelyWhenANotificationCreationFails() {
        TransactionTemplate transactionTemplate = transactionTemplate();

        Long authorId = transactionTemplate.execute(status -> persistUser("atomicity-author").getId());
        Long articleId = transactionTemplate.execute(status -> {
            AppUser author = entityManager.find(AppUser.class, authorId);
            return persistDraftArticle(author, "Atomicity Rollback", "atomicity-rollback").getId();
        });
        transactionTemplate.executeWithoutResult(status -> {
            persistMemberWithStatus("atomicity-member-1", MemberStatus.ACTIVE);
            persistMemberWithStatus("atomicity-member-2", MemberStatus.ACTIVE);
        });

        Mockito.doCallRealMethod()
                .doThrow(new RuntimeException("Échec simulé de la seconde Notification"))
                .when(notificationService)
                .createForArticle(any(), any(), any(), any());

        authenticateWithArticlePublish(authorId);
        assertThatThrownBy(() -> articleService.publishArticle(articleId, authorId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Échec simulé");

        // Relecture réelle PostgreSQL, transaction séparée : rien n'a été committé.
        transactionTemplate.executeWithoutResult(status -> {
            Article reloaded = entityManager.find(Article.class, articleId);
            assertThat(reloaded.getArticleStatus()).isEqualTo(ArticleStatus.DRAFT);
            assertThat(reloaded.getPublishedAt()).isNull();
            assertThat(reloaded.getLastModifiedByUser()).isNull();

            List<Notification> notifications = entityManager
                    .createQuery("SELECT n FROM Notification n WHERE n.article.id = :articleId", Notification.class)
                    .setParameter("articleId", articleId)
                    .getResultList();
            assertThat(notifications).isEmpty();
        });

        cleanup(articleId, authorId);
    }

    private static void authenticateWithArticlePublish(Long userId) {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ARTICLE_PUBLISH"));
        Authentication authentication = new TestingAuthenticationToken(String.valueOf(userId), null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AppUser persistUser(String slug) {
        AppUser user = new AppUser();
        user.setEmail(slug + "-" + System.nanoTime() + "@primatis.test");
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private AppUser persistMemberWithStatus(String slug, MemberStatus memberStatus) {
        AppUser user = persistUser(slug);
        user.setMemberStatus(memberStatus);
        return user;
    }

    private Article persistDraftArticle(AppUser author, String title, String slug) {
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle(title);
        article.setContent("Contenu de test");
        article.setSlug(slug + "-" + System.nanoTime());
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setPublishedAt(null);
        article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        entityManager.persist(article);
        return article;
    }

    private void cleanup(Long articleId, Long authorId) {
        transactionTemplate().executeWithoutResult(status -> {
            entityManager.createQuery("DELETE FROM Notification n WHERE n.article.id = :articleId")
                    .setParameter("articleId", articleId).executeUpdate();
            entityManager.createQuery("DELETE FROM Article a WHERE a.id = :articleId")
                    .setParameter("articleId", articleId).executeUpdate();
            entityManager.createQuery("DELETE FROM AppUser u WHERE u.email LIKE 'atomicity-%'").executeUpdate();
        });
    }
}
