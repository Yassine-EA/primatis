package be.primatis.article;

import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link ArticleRepository}
 * ajoutées en DEV-11.5 : {@link ArticleRepository#findByArticleStatus},
 * {@link ArticleRepository#findBySlugAndArticleStatus}, {@link
 * ArticleRepository#findTagsByArticleId}. Test unitaire d'intégration —
 * {@code @Transactional} (rollback automatique par test, même précédent
 * que {@code CatalogueServiceTests}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArticleRepositoryTests {

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // findByArticleStatus — PUBLISHED-only, tri, pagination
    // ---------------------------------------------------------------

    @Test
    void findByArticleStatusOnlyReturnsPublishedArticles() {
        AppUser author = persistUser("published-only@primatis.test");
        persistArticle(author, "Draft Repo Test", ArticleStatus.DRAFT, null);
        persistArticle(author, "Archived Repo Test", ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));
        Article published = persistArticle(
                author, "Published Repo Test", ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        Page<Article> page = articleRepository.findByArticleStatus(
                ArticleStatus.PUBLISHED, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedAt", "id")));

        assertThat(page.getContent()).extracting(Article::getId).containsExactly(published.getId());
    }

    @Test
    void findByArticleStatusOrdersByPublishedAtDescending() {
        AppUser author = persistUser("order-desc@primatis.test");
        Article older = persistArticle(
                author, "Older Repo Test", ArticleStatus.PUBLISHED, Instant.parse("2026-08-01T10:00:00Z"));
        Article newer = persistArticle(
                author, "Newer Repo Test", ArticleStatus.PUBLISHED, Instant.parse("2026-08-15T10:00:00Z"));

        Page<Article> page = articleRepository.findByArticleStatus(
                ArticleStatus.PUBLISHED, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedAt", "id")));

        assertThat(page.getContent()).extracting(Article::getId)
                .containsSubsequence(newer.getId(), older.getId());
    }

    @Test
    void findByArticleStatusBreaksTiesByIdDescendingWhenPublishedAtIsEqual() {
        AppUser author = persistUser("order-tie-break@primatis.test");
        Instant samePublishedAt = Instant.parse("2026-08-20T10:00:00Z");
        Article first = persistArticle(author, "Tie Break First Repo Test", ArticleStatus.PUBLISHED, samePublishedAt);
        Article second = persistArticle(author, "Tie Break Second Repo Test", ArticleStatus.PUBLISHED, samePublishedAt);

        Page<Article> page = articleRepository.findByArticleStatus(
                ArticleStatus.PUBLISHED, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedAt", "id")));

        List<Long> ids = page.getContent().stream().map(Article::getId).toList();
        assertThat(ids.indexOf(second.getId())).isLessThan(ids.indexOf(first.getId()));
    }

    @Test
    void findByArticleStatusRespectsPagination() {
        AppUser author = persistUser("pagination@primatis.test");
        for (int i = 0; i < 3; i++) {
            persistArticle(author, "Pagination Repo Test " + i, ArticleStatus.PUBLISHED,
                    Instant.parse("2026-08-0" + (i + 1) + "T10:00:00Z"));
        }

        Page<Article> firstPage = articleRepository.findByArticleStatus(
                ArticleStatus.PUBLISHED, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "publishedAt", "id")));

        assertThat(firstPage.getContent()).hasSizeLessThanOrEqualTo(2);
        assertThat(firstPage.getSize()).isEqualTo(2);
        assertThat(firstPage.getNumber()).isEqualTo(0);
    }

    // ---------------------------------------------------------------
    // findBySlugAndArticleStatus — lookup slug public
    // ---------------------------------------------------------------

    @Test
    void findBySlugAndArticleStatusFindsAPublishedArticle() {
        AppUser author = persistUser("slug-found@primatis.test");
        Article article = persistArticleWithSlug(
                author, "Slug Found Repo Test", "slug-found-repo-test", ArticleStatus.PUBLISHED,
                Instant.parse("2026-08-10T10:00:00Z"));

        Optional<Article> found =
                articleRepository.findBySlugAndArticleStatus("slug-found-repo-test", ArticleStatus.PUBLISHED);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(article.getId());
    }

    @Test
    void findBySlugAndArticleStatusIsAbsentForADraftArticle() {
        AppUser author = persistUser("slug-draft@primatis.test");
        persistArticleWithSlug(author, "Slug Draft Repo Test", "slug-draft-repo-test", ArticleStatus.DRAFT, null);

        Optional<Article> found =
                articleRepository.findBySlugAndArticleStatus("slug-draft-repo-test", ArticleStatus.PUBLISHED);

        assertThat(found).isEmpty();
    }

    @Test
    void findBySlugAndArticleStatusIsAbsentForAnArchivedArticle() {
        AppUser author = persistUser("slug-archived@primatis.test");
        persistArticleWithSlug(author, "Slug Archived Repo Test", "slug-archived-repo-test", ArticleStatus.ARCHIVED,
                Instant.parse("2026-08-01T10:00:00Z"));

        Optional<Article> found =
                articleRepository.findBySlugAndArticleStatus("slug-archived-repo-test", ArticleStatus.PUBLISHED);

        assertThat(found).isEmpty();
    }

    @Test
    void findBySlugAndArticleStatusIsAbsentForANonExistentSlug() {
        Optional<Article> found = articleRepository.findBySlugAndArticleStatus(
                "slug-does-not-exist-repo-test", ArticleStatus.PUBLISHED);

        assertThat(found).isEmpty();
    }

    // ---------------------------------------------------------------
    // findTagsByArticleId
    // ---------------------------------------------------------------

    @Test
    void findTagsByArticleIdReturnsAssociatedTagsOrderedById() {
        AppUser author = persistUser("tags-lookup@primatis.test");
        Article article = persistArticle(
                author, "Tags Lookup Repo Test", ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        Tag first = persistTag("TAGS-LOOKUP-A", "Tag A");
        Tag second = persistTag("TAGS-LOOKUP-B", "Tag B");
        linkArticleTag(article, first);
        linkArticleTag(article, second);

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());

        assertThat(tags).extracting(Tag::getCode).containsExactly("TAGS-LOOKUP-A", "TAGS-LOOKUP-B");
    }

    @Test
    void findTagsByArticleIdReturnsEmptyListWhenNoTagsAssociated() {
        AppUser author = persistUser("tags-empty@primatis.test");
        Article article = persistArticle(
                author, "Tags Empty Repo Test", ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());

        assertThat(tags).isEmpty();
    }

    // ---------------------------------------------------------------
    // findAllByOrderByUpdatedAtDescIdDesc (DEV-11.12A) — tous statuts, tri
    // ---------------------------------------------------------------

    @Test
    void findAllByOrderByUpdatedAtDescIdDescReturnsAllStatuses() {
        AppUser author = persistUser("staff-list-all-statuses@primatis.test");
        Article draft = persistArticle(author, "Staff List Draft Repo Test", ArticleStatus.DRAFT, null);
        Article published = persistArticle(
                author, "Staff List Published Repo Test", ArticleStatus.PUBLISHED, Instant.parse("2026-08-10T10:00:00Z"));
        Article archived = persistArticle(
                author, "Staff List Archived Repo Test", ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        Page<Article> page = articleRepository.findAllByOrderByUpdatedAtDescIdDesc(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(Article::getId)
                .contains(draft.getId(), published.getId(), archived.getId());
    }

    @Test
    void findAllByOrderByUpdatedAtDescIdDescOrdersByUpdatedAtDescending() {
        AppUser author = persistUser("staff-list-order@primatis.test");
        Article older = persistArticle(author, "Staff List Older Repo Test", ArticleStatus.DRAFT, null);
        older.setUpdatedAt(Instant.parse("2026-08-01T10:00:00Z"));
        Article newer = persistArticle(author, "Staff List Newer Repo Test", ArticleStatus.DRAFT, null);
        newer.setUpdatedAt(Instant.parse("2026-08-15T10:00:00Z"));
        entityManager.flush();

        Page<Article> page = articleRepository.findAllByOrderByUpdatedAtDescIdDesc(PageRequest.of(0, 100));

        List<Long> ids = page.getContent().stream().map(Article::getId).toList();
        assertThat(ids.indexOf(newer.getId())).isLessThan(ids.indexOf(older.getId()));
    }

    @Test
    void findAllByOrderByUpdatedAtDescIdDescBreaksTiesByIdDescendingWhenUpdatedAtIsEqual() {
        AppUser author = persistUser("staff-list-tie-break@primatis.test");
        Instant sameUpdatedAt = Instant.parse("2026-08-20T10:00:00Z");
        Article first = persistArticle(author, "Staff List Tie First Repo Test", ArticleStatus.DRAFT, null);
        first.setUpdatedAt(sameUpdatedAt);
        Article second = persistArticle(author, "Staff List Tie Second Repo Test", ArticleStatus.DRAFT, null);
        second.setUpdatedAt(sameUpdatedAt);
        entityManager.flush();

        Page<Article> page = articleRepository.findAllByOrderByUpdatedAtDescIdDesc(PageRequest.of(0, 100));

        List<Long> ids = page.getContent().stream().map(Article::getId).toList();
        assertThat(ids.indexOf(second.getId())).isLessThan(ids.indexOf(first.getId()));
    }

    // ---------------------------------------------------------------
    // findByIdWithUsers (DEV-11.12A) — détail staff, tous statuts
    // ---------------------------------------------------------------

    @Test
    void findByIdWithUsersFindsADraftArticle() {
        AppUser author = persistUser("staff-detail-draft@primatis.test");
        Article article = persistArticle(author, "Staff Detail Draft Repo Test", ArticleStatus.DRAFT, null);

        Optional<Article> found = articleRepository.findByIdWithUsers(article.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getArticleStatus()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    void findByIdWithUsersFindsAnArchivedArticle() {
        AppUser author = persistUser("staff-detail-archived@primatis.test");
        Article article = persistArticle(
                author, "Staff Detail Archived Repo Test", ArticleStatus.ARCHIVED, Instant.parse("2026-08-01T10:00:00Z"));

        Optional<Article> found = articleRepository.findByIdWithUsers(article.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getArticleStatus()).isEqualTo(ArticleStatus.ARCHIVED);
    }

    @Test
    void findByIdWithUsersIsAbsentForANonExistentId() {
        Optional<Article> found = articleRepository.findByIdWithUsers(999999999L);

        assertThat(found).isEmpty();
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private AppUser persistUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
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

    private Article persistArticle(AppUser author, String title, ArticleStatus status, Instant publishedAt) {
        return persistArticleWithSlug(author, title, "slug-" + System.nanoTime(), status, publishedAt);
    }

    private Article persistArticleWithSlug(
            AppUser author, String title, String slug, ArticleStatus status, Instant publishedAt) {
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle(title);
        article.setContent("Contenu de test");
        article.setSlug(slug);
        article.setArticleStatus(status);
        article.setPublishedAt(publishedAt);
        article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        entityManager.persist(article);
        return article;
    }

    private Tag persistTag(String code, String label) {
        Tag tag = new Tag();
        tag.setCode(code);
        tag.setLabel(label);
        entityManager.persist(tag);
        return tag;
    }

    private void linkArticleTag(Article article, Tag tag) {
        ArticleTag articleTag = new ArticleTag();
        articleTag.setId(new ArticleTagId(article.getId(), tag.getId()));
        articleTag.setArticle(entityManager.getReference(Article.class, article.getId()));
        articleTag.setTag(entityManager.getReference(Tag.class, tag.getId()));
        entityManager.persist(articleTag);
    }
}
