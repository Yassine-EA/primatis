package be.primatis.article;

import be.primatis.article.dto.CreateTagRequest;
import be.primatis.article.dto.TagResponse;
import be.primatis.article.dto.UpdateTagRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie {@link TagService} contre PostgreSQL réel (DEV-11.9, {@code
 * ARTICLE_MANAGE}). Même précédent structurel exact que {@code
 * CatalogueManagementServiceTests} (Genre) pour le style CRUD ; {@code
 * @Transactional} (rollback automatique par test), même précédent que
 * {@code ArticleServiceTests}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TagServiceTests {

    @Autowired
    private TagService tagService;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithArticleManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ARTICLE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithArticlePublish() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ARTICLE_PUBLISH"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithoutArticleManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Tag persistTag(String code, String label) {
        Tag tag = new Tag();
        tag.setCode(code);
        tag.setLabel(label);
        entityManager.persist(tag);
        return tag;
    }

    // ---------------------------------------------------------------
    // listTags — sécurité
    // ---------------------------------------------------------------

    @Test
    void listTagsWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> tagService.listTags(PageRequest.of(0, 20)));
    }

    @Test
    void listTagsWithoutArticleManageIsDenied() {
        authenticateWithoutArticleManage();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> tagService.listTags(PageRequest.of(0, 20)));
    }

    // ---------------------------------------------------------------
    // createTag — sécurité
    // ---------------------------------------------------------------

    @Test
    void createTagWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> tagService.createTag(new CreateTagRequest("create-anon", "Anon", null)));
    }

    @Test
    void createTagWithArticlePublishOnlyIsDenied() {
        authenticateWithArticlePublish();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> tagService.createTag(new CreateTagRequest("create-publish", "Publish", null)));
    }

    @Test
    void createTagWithoutArticleManageIsDenied() {
        authenticateWithoutArticleManage();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> tagService.createTag(new CreateTagRequest("create-member", "Member", null)));
    }

    // ---------------------------------------------------------------
    // createTag — comportement
    // ---------------------------------------------------------------

    @Test
    void createTagWithValidRequestSucceeds() {
        authenticateWithArticleManage();

        TagResponse response = tagService.createTag(new CreateTagRequest("create-valid", "Valid Label", "Une description"));

        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo("create-valid");
        assertThat(response.label()).isEqualTo("Valid Label");
        assertThat(response.description()).isEqualTo("Une description");
    }

    @Test
    void createTagWithNullDescriptionSucceeds() {
        authenticateWithArticleManage();

        TagResponse response = tagService.createTag(new CreateTagRequest("create-null-desc", "No Description", null));

        assertThat(response.description()).isNull();
    }

    @Test
    void createTagRejectsDuplicateCode() {
        persistTag("create-duplicate", "Original");
        authenticateWithArticleManage();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> tagService.createTag(new CreateTagRequest("create-duplicate", "Duplicate", null)))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("TAG_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createTagAllowsDuplicateLabel() {
        persistTag("create-label-a", "Même Label");
        authenticateWithArticleManage();

        TagResponse response = tagService.createTag(new CreateTagRequest("create-label-b", "Même Label", null));

        assertThat(response.label()).isEqualTo("Même Label");
    }

    // ---------------------------------------------------------------
    // updateTag — sécurité
    // ---------------------------------------------------------------

    @Test
    void updateTagWithoutAuthenticationIsDenied() {
        Tag tag = persistTag("update-security-anon", "Anon");
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> tagService.updateTag(tag.getId(), new UpdateTagRequest()));
    }

    @Test
    void updateTagWithoutArticleManageIsDenied() {
        Tag tag = persistTag("update-security-member", "Member");
        authenticateWithoutArticleManage();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> tagService.updateTag(tag.getId(), new UpdateTagRequest()));
    }

    // ---------------------------------------------------------------
    // updateTag — comportement
    // ---------------------------------------------------------------

    @Test
    void updateTagChangesLabel() {
        Tag tag = persistTag("update-label", "Ancien Label");
        authenticateWithArticleManage();

        UpdateTagRequest request = new UpdateTagRequest();
        request.setLabel("Nouveau Label");

        TagResponse response = tagService.updateTag(tag.getId(), request);

        assertThat(response.label()).isEqualTo("Nouveau Label");
    }

    @Test
    void updateTagChangesDescription() {
        Tag tag = persistTag("update-description", "Label");
        authenticateWithArticleManage();

        UpdateTagRequest request = new UpdateTagRequest();
        request.setDescription("Nouvelle description");

        TagResponse response = tagService.updateTag(tag.getId(), request);

        assertThat(response.description()).isEqualTo("Nouvelle description");
    }

    @Test
    void updateTagClearsDescriptionWhenPresentNull() {
        Tag tag = persistTag("update-clear-description", "Label");
        tag.setDescription("Description initiale");
        entityManager.flush();
        authenticateWithArticleManage();

        UpdateTagRequest request = new UpdateTagRequest();
        request.setDescription(null);

        TagResponse response = tagService.updateTag(tag.getId(), request);

        assertThat(response.description()).isNull();
    }

    @Test
    void updateTagRejectsBlankLabel() {
        Tag tag = persistTag("update-blank-label", "Label");
        authenticateWithArticleManage();

        UpdateTagRequest request = new UpdateTagRequest();
        request.setLabel("   ");

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> tagService.updateTag(tag.getId(), request))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("TAG_LABEL_MUST_NOT_BE_BLANK"));
    }

    @Test
    void updateTagKeepsCodeUnchanged() {
        Tag tag = persistTag("update-code-immutable", "Label");
        authenticateWithArticleManage();

        UpdateTagRequest request = new UpdateTagRequest();
        request.setLabel("Nouveau Label");

        TagResponse response = tagService.updateTag(tag.getId(), request);

        assertThat(response.code()).isEqualTo("update-code-immutable");
    }

    @Test
    void updateTagWithNoFieldsPresentIsANoop() {
        Tag tag = persistTag("update-noop", "Label Original");
        authenticateWithArticleManage();

        TagResponse response = tagService.updateTag(tag.getId(), new UpdateTagRequest());

        assertThat(response.label()).isEqualTo("Label Original");
    }

    @Test
    void updateTagNonExistentThrowsNotFound() {
        authenticateWithArticleManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> tagService.updateTag(999999999L, new UpdateTagRequest()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("TAG_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // deleteTag — sécurité
    // ---------------------------------------------------------------

    @Test
    void deleteTagWithoutAuthenticationIsDenied() {
        Tag tag = persistTag("delete-security-anon", "Anon");
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> tagService.deleteTag(tag.getId()));
    }

    @Test
    void deleteTagWithoutArticleManageIsDenied() {
        Tag tag = persistTag("delete-security-member", "Member");
        authenticateWithoutArticleManage();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> tagService.deleteTag(tag.getId()));
    }

    // ---------------------------------------------------------------
    // deleteTag — comportement
    // ---------------------------------------------------------------

    @Test
    void deleteTagRemovesUnusedTagPhysically() {
        Tag tag = persistTag("delete-unused", "Unused");
        Long tagId = tag.getId();
        entityManager.flush();
        entityManager.clear();
        authenticateWithArticleManage();

        tagService.deleteTag(tagId);
        entityManager.flush();

        assertThat(entityManager.find(Tag.class, tagId)).isNull();
    }

    @Test
    void deleteTagNonExistentThrowsNotFound() {
        authenticateWithArticleManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> tagService.deleteTag(999999999L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("TAG_NOT_FOUND"));
    }

    /**
     * {@code entityManager.clear()} avant l'appel : force un rechargement
     * réel depuis PostgreSQL (plutôt que de réutiliser les instances Java
     * ayant servi à construire manuellement la fixture {@code ArticleTag}),
     * même précaution que pour toute vérification d'un scénario de
     * concurrence/persistance réel dans ce projet. La persistance du Tag
     * après rejet est vérifiée séparément, au niveau Controller
     * ({@code StaffTagControllerTests}) — une relecture dans la même
     * transaction ici serait invalide : après l'échec de contrainte FK,
     * l'entité reste en état {@code REMOVED} côté Hibernate (jamais
     * revalidée automatiquement), donc {@code entityManager.find} y
     * retournerait {@code null} sans refléter l'état réel en base.
     */
    @Test
    void deleteTagInUseIsRejected() {
        Tag tag = persistTag("delete-in-use", "In Use");
        Long tagId = tag.getId();
        AppUser author = persistUser("delete-in-use-article-author@primatis.test");
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle("Delete Tag In Use");
        article.setContent("Contenu de test");
        article.setSlug("delete-tag-in-use-" + System.nanoTime());
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setPublishedAt(null);
        article.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        entityManager.persist(article);

        ArticleTag articleTag = new ArticleTag();
        articleTag.setId(new ArticleTagId(article.getId(), tagId));
        articleTag.setArticle(article);
        articleTag.setTag(tag);
        entityManager.persist(articleTag);
        entityManager.flush();
        entityManager.clear();

        authenticateWithArticleManage();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> tagService.deleteTag(tagId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("TAG_IN_USE"));
    }

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

    // ---------------------------------------------------------------
    // listTags — comportement
    // ---------------------------------------------------------------

    @Test
    void listTagsReturnsCreatedTag() {
        persistTag("list-behavior", "List Behavior Label");
        entityManager.flush();
        authenticateWithArticleManage();

        Page<TagResponse> page = tagService.listTags(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(TagResponse::code).contains("list-behavior");
    }
}
