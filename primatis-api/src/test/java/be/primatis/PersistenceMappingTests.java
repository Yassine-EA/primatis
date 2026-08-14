package be.primatis;

import be.primatis.access.Permission;
import be.primatis.access.Role;
import be.primatis.access.RolePermission;
import be.primatis.access.RolePermissionId;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;
import be.primatis.article.ArticleTag;
import be.primatis.article.ArticleTagId;
import be.primatis.article.Tag;
import be.primatis.catalogue.Author;
import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Genre;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleAuthor;
import be.primatis.catalogue.TitleAuthorId;
import be.primatis.catalogue.TitleGenre;
import be.primatis.catalogue.TitleGenreId;
import be.primatis.catalogue.TitleStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie explicitement, contre PostgreSQL réel, les mappings JPA les plus
 * sensibles de DEV-02.4 : les 5 clés composites (@EmbeddedId + @MapsId) et
 * la persistance des enums en EnumType.STRING (pas ordinal).
 *
 * Chaque test s'exécute dans une transaction annulée en fin de méthode
 * (@Transactional par défaut en test) : aucune donnée ne persiste entre tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceMappingTests {

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // Clés composites
    // ---------------------------------------------------------------

    @Test
    void mapsUserRoleCompositeKey() {
        AppUser user = persistUser("composite-user-role@primatis.test");
        Role role = persistRole("ROLE_COMPOSITE_UR", "Rôle test UserRole");

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(Instant.now());
        entityManager.persist(userRole);
        entityManager.flush();
        entityManager.clear();

        UserRole reloaded = entityManager.find(UserRole.class, new UserRoleId(user.getId(), role.getId()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(reloaded.getRole().getId()).isEqualTo(role.getId());
    }

    @Test
    void mapsRolePermissionCompositeKey() {
        Role role = persistRole("ROLE_COMPOSITE_RP", "Rôle test RolePermission");
        Permission permission = persistPermission("PERMISSION_COMPOSITE_RP", "Permission test RolePermission");

        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(new RolePermissionId(role.getId(), permission.getId()));
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setAssignedAt(Instant.now());
        entityManager.persist(rolePermission);
        entityManager.flush();
        entityManager.clear();

        RolePermission reloaded = entityManager.find(RolePermission.class,
                new RolePermissionId(role.getId(), permission.getId()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getRole().getId()).isEqualTo(role.getId());
        assertThat(reloaded.getPermission().getId()).isEqualTo(permission.getId());
    }

    @Test
    void mapsTitleAuthorCompositeKey() {
        Title title = persistTitle();
        Author author = persistAuthor();

        TitleAuthor titleAuthor = new TitleAuthor();
        titleAuthor.setId(new TitleAuthorId(title.getId(), author.getId()));
        titleAuthor.setTitle(title);
        titleAuthor.setAuthor(author);
        entityManager.persist(titleAuthor);
        entityManager.flush();
        entityManager.clear();

        TitleAuthor reloaded = entityManager.find(TitleAuthor.class,
                new TitleAuthorId(title.getId(), author.getId()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getTitle().getId()).isEqualTo(title.getId());
        assertThat(reloaded.getAuthor().getId()).isEqualTo(author.getId());
    }

    @Test
    void mapsTitleGenreCompositeKey() {
        Genre genre = persistGenre();
        Title title = persistTitle();

        TitleGenre titleGenre = new TitleGenre();
        titleGenre.setId(new TitleGenreId(genre.getId(), title.getId()));
        titleGenre.setGenre(genre);
        titleGenre.setTitle(title);
        entityManager.persist(titleGenre);
        entityManager.flush();
        entityManager.clear();

        TitleGenre reloaded = entityManager.find(TitleGenre.class,
                new TitleGenreId(genre.getId(), title.getId()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getGenre().getId()).isEqualTo(genre.getId());
        assertThat(reloaded.getTitle().getId()).isEqualTo(title.getId());
    }

    @Test
    void mapsArticleTagCompositeKey() {
        AppUser author = persistUser("composite-article-tag@primatis.test");
        Article article = persistArticle(author);
        Tag tag = persistTag();

        ArticleTag articleTag = new ArticleTag();
        articleTag.setId(new ArticleTagId(article.getId(), tag.getId()));
        articleTag.setArticle(article);
        articleTag.setTag(tag);
        entityManager.persist(articleTag);
        entityManager.flush();
        entityManager.clear();

        ArticleTag reloaded = entityManager.find(ArticleTag.class,
                new ArticleTagId(article.getId(), tag.getId()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getArticle().getId()).isEqualTo(article.getId());
        assertThat(reloaded.getTag().getId()).isEqualTo(tag.getId());
    }

    // ---------------------------------------------------------------
    // Enums — EnumType.STRING (jamais ordinal)
    // ---------------------------------------------------------------

    @Test
    void titleEnumsArePersistedByName() {
        Title title = persistTitleWith(Language.ES, TitleStatus.WITHDRAWN);
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT language, title_status FROM title WHERE id = :id")
                .setParameter("id", title.getId())
                .getSingleResult();

        assertThat(row[0]).isEqualTo("ES");
        assertThat(row[1]).isEqualTo("WITHDRAWN");
    }

    @Test
    void appUserEnumsArePersistedByName() {
        AppUser user = persistUser("enum-check@primatis.test");
        user.setMemberStatus(MemberStatus.BLOCKED);
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT account_status, member_status FROM app_user WHERE id = :id")
                .setParameter("id", user.getId())
                .getSingleResult();

        assertThat(row[0]).isEqualTo("ACTIVE");
        assertThat(row[1]).isEqualTo("BLOCKED");
    }

    @Test
    void copyEnumsArePersistedByName() {
        Title title = persistTitle();
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode("COPY-ENUM-CHECK-1");
        copy.setCopyCondition(CopyCondition.DAMAGED);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT copy_condition, availability_status FROM copy WHERE id = :id")
                .setParameter("id", copy.getId())
                .getSingleResult();

        assertThat(row[0]).isEqualTo("DAMAGED");
        assertThat(row[1]).isEqualTo("AVAILABLE");
    }

    // ---------------------------------------------------------------
    // Types temporels — DATE (date métier) vs TIMESTAMPTZ (instant précis)
    // ---------------------------------------------------------------

    @Test
    void loanTemporalColumnsUseExpectedPostgresTypes() {
        AppUser user = persistUser("temporal-check@primatis.test");
        Title title = persistTitle();
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode("COPY-TEMPORAL-CHECK-1");
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(Instant.now());
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT pg_typeof(due_date)::text, pg_typeof(loan_date)::text FROM loan WHERE id = :id")
                .setParameter("id", loan.getId())
                .getSingleResult();

        assertThat(row[0]).isEqualTo("date");
        assertThat(row[1]).isEqualTo("timestamp with time zone");
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

    private Role persistRole(String code, String name) {
        Role role = new Role();
        role.setName(name);
        role.setCode(code);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(Instant.now());
        entityManager.persist(role);
        return role;
    }

    private Permission persistPermission(String code, String name) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setCode(code);
        permission.setCreatedAt(Instant.now());
        permission.setUpdatedAt(Instant.now());
        entityManager.persist(permission);
        return permission;
    }

    private Title persistTitle() {
        return persistTitleWith(Language.FR, TitleStatus.ACTIVE);
    }

    private Title persistTitleWith(Language language, TitleStatus status) {
        Title title = new Title();
        title.setTitle("Titre de test");
        title.setLanguage(language);
        title.setTitleStatus(status);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        entityManager.persist(title);
        return title;
    }

    private Author persistAuthor() {
        Author author = new Author();
        author.setFullName("Auteur de test");
        entityManager.persist(author);
        return author;
    }

    private Genre persistGenre() {
        Genre genre = new Genre();
        genre.setCode("GENRE_TEST_" + System.nanoTime());
        genre.setLabel("Genre de test " + System.nanoTime());
        entityManager.persist(genre);
        return genre;
    }

    private Tag persistTag() {
        Tag tag = new Tag();
        tag.setCode("TAG_TEST_" + System.nanoTime());
        tag.setLabel("Tag de test");
        entityManager.persist(tag);
        return tag;
    }

    private Article persistArticle(AppUser authorUser) {
        Article article = new Article();
        article.setAuthorUser(authorUser);
        article.setTitle("Article de test");
        article.setContent("Contenu de test");
        article.setSlug("article-test-" + System.nanoTime());
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setCreatedAt(Instant.now());
        article.setUpdatedAt(Instant.now());
        entityManager.persist(article);
        return article;
    }
}
