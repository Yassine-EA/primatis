package be.primatis.article;

import be.primatis.exception.BusinessRuleException;
import be.primatis.notification.Notification;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve de concurrence réelle pour DEV-11.7 : deux appels concurrents à
 * {@link ArticleService#publishArticle(Long, Long)} ciblant le même
 * Article {@code DRAFT} ne produisent jamais deux publications effectives
 * ni un double fanout {@code ARTICLE_PUBLISHED}. Grâce au verrou {@code
 * PESSIMISTIC_WRITE} ciblé sur {@code Article} ({@link
 * ArticleRepository#findByIdForUpdate(Long)}), le second thread se bloque
 * réellement au niveau PostgreSQL le temps que le premier committe sa
 * transaction (mutation + fanout complet), puis sa revalidation post-lock
 * découvre {@code articleStatus == PUBLISHED} et rejette proprement
 * ({@code ARTICLE_NOT_PUBLISHABLE}) — jamais une violation de contrainte
 * brute, jamais une seconde diffusion. Même précédent structurel exact que
 * {@code LoanServiceConcurrencyTests.concurrentRegisterReturnOnTheSameLoanYieldsExactlyOneSuccessAndOneCleanRejection}.
 *
 * <p>Volontairement SANS {@code @Transactional} de classe (chaque thread
 * ouvre sa propre transaction/connexion réelle, même principe que {@code
 * LoanServiceConcurrencyTests}). Chaque thread positionne son propre
 * {@link SecurityContextHolder} ({@code ARTICLE_PUBLISH}) car le contexte
 * de sécurité est {@code ThreadLocal} et n'est pas hérité par les threads
 * de l'{@link ExecutorService}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleServiceConcurrencyTests {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Trois membres {@code ACTIVE}, deux tentatives concurrentes de
     * publication du même Article {@code DRAFT}. Attendu : exactement une
     * {@code SUCCESS}, exactement un rejet {@code ARTICLE_NOT_PUBLISHABLE},
     * l'Article final {@code PUBLISHED} avec une seule valeur de {@code
     * publishedAt}, et <b>exactement 3 Notifications {@code
     * ARTICLE_PUBLISHED}</b> au total (jamais 6 — prévention du double
     * fanout, mission DEV-11.7 §34).
     */
    @Test
    void concurrentPublishOnTheSameArticleYieldsExactlyOneSuccessAndExactlyOneNotificationBatch()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long publisherId = transactionTemplate.execute(status -> persistUser("publish-concurrency-publisher").getId());

        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int index = i;
            Long memberId = transactionTemplate.execute(status ->
                    persistMemberWithStatus("publish-concurrency-member-" + index, MemberStatus.ACTIVE).getId());
            memberIds.add(memberId);
        }

        Long articleId = transactionTemplate.execute(status ->
                persistDraftArticle(entityManager.find(AppUser.class, publisherId),
                        "Concurrence publication", "publish-concurrency-" + System.nanoTime()).getId());

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    authenticateWithArticlePublish(publisherId);
                    ready.countDown();
                    go.await();
                    try {
                        articleService.publishArticle(articleId, publisherId);
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }

            ready.await();
            go.countDown();
            Map<String, Long> outcomeCounts = new HashMap<>();
            for (Future<String> future : futures) {
                outcomeCounts.merge(future.get(), 1L, Long::sum);
            }
            executor.shutdown();

            assertThat(outcomeCounts.getOrDefault("SUCCESS", 0L)).isEqualTo(1L);
            assertThat(outcomeCounts.getOrDefault("ARTICLE_NOT_PUBLISHABLE", 0L)).isEqualTo(1L);

            Article finalArticle = transactionTemplate.execute(status -> articleRepository.findById(articleId).orElseThrow());
            assertThat(finalArticle.getArticleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
            assertThat(finalArticle.getPublishedAt()).isNotNull();

            List<Notification> notifications = transactionTemplate.execute(status -> entityManager
                    .createQuery("SELECT n FROM Notification n WHERE n.article.id = :articleId", Notification.class)
                    .setParameter("articleId", articleId)
                    .getResultList());
            assertThat(notifications).hasSize(3);
            assertThat(notifications).extracting(n -> n.getRecipientUser().getId())
                    .containsExactlyInAnyOrderElementsOf(memberIds);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                entityManager.createQuery("DELETE FROM Notification n WHERE n.article.id = :articleId")
                        .setParameter("articleId", articleId).executeUpdate();
                entityManager.createQuery("DELETE FROM Article a WHERE a.id = :articleId")
                        .setParameter("articleId", articleId).executeUpdate();
                appUserRepository.deleteById(publisherId);
                memberIds.forEach(appUserRepository::deleteById);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour DEV-11.8 (mission §33) : même
     * précédent structurel exact que {@link
     * #concurrentPublishOnTheSameArticleYieldsExactlyOneSuccessAndExactlyOneNotificationBatch}
     * — {@link ArticleService#archiveArticle} réutilise le même verrou
     * {@code PESSIMISTIC_WRITE} ({@link ArticleRepository#findByIdForUpdate}).
     * Deux archivages concurrents sur le même Article {@code PUBLISHED} ne
     * produisent jamais deux succès : exactement 1 {@code SUCCESS} + 1 rejet
     * {@code ARTICLE_NOT_ARCHIVABLE}, état final {@code ARCHIVED}.
     */
    @Test
    void concurrentArchiveOnTheSameArticleYieldsExactlyOneSuccess() throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long archiverId = transactionTemplate.execute(status -> persistUser("archive-concurrency-archiver").getId());

        Long articleId = transactionTemplate.execute(status ->
                persistArticleWithStatus(entityManager.find(AppUser.class, archiverId),
                        "Concurrence archivage", "archive-concurrency-" + System.nanoTime()).getId());

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    authenticateWithArticleManage(archiverId);
                    ready.countDown();
                    go.await();
                    try {
                        articleService.archiveArticle(articleId, archiverId);
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }

            ready.await();
            go.countDown();
            Map<String, Long> outcomeCounts = new HashMap<>();
            for (Future<String> future : futures) {
                outcomeCounts.merge(future.get(), 1L, Long::sum);
            }
            executor.shutdown();

            assertThat(outcomeCounts.getOrDefault("SUCCESS", 0L)).isEqualTo(1L);
            assertThat(outcomeCounts.getOrDefault("ARTICLE_NOT_ARCHIVABLE", 0L)).isEqualTo(1L);

            Article finalArticle = transactionTemplate.execute(status -> articleRepository.findById(articleId).orElseThrow());
            assertThat(finalArticle.getArticleStatus()).isEqualTo(ArticleStatus.ARCHIVED);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                entityManager.createQuery("DELETE FROM Article a WHERE a.id = :articleId")
                        .setParameter("articleId", articleId).executeUpdate();
                appUserRepository.deleteById(archiverId);
            });
        }
    }

    private static void authenticateWithArticleManage(Long userId) {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ARTICLE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken(String.valueOf(userId), null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Article persistArticleWithStatus(AppUser author, String title, String slug) {
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle(title);
        article.setContent("Contenu de test");
        article.setSlug(slug);
        article.setArticleStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(Instant.parse("2026-08-01T10:00:00Z"));
        article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        articleRepository.save(article);
        return article;
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
        appUserRepository.save(user);
        return user;
    }

    private AppUser persistMemberWithStatus(String slug, MemberStatus memberStatus) {
        AppUser user = persistUser(slug);
        user.setMemberStatus(memberStatus);
        appUserRepository.save(user);
        return user;
    }

    private Article persistDraftArticle(AppUser author, String title, String slug) {
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle(title);
        article.setContent("Contenu de test");
        article.setSlug(slug);
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setPublishedAt(null);
        article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        articleRepository.save(article);
        return article;
    }
}
