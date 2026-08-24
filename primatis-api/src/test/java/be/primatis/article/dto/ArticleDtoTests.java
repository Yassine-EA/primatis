package be.primatis.article.dto;

import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;
import be.primatis.article.Tag;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie les mappings {@code Entity → DTO} et la validation structurelle
 * du domaine Article (DEV-11.3) : {@link ArticleResponse#from},
 * {@link ArticleUserResponse#from}, {@link TagResponse#from},
 * {@link CreateArticleRequest}. Tests unitaires purs — aucun Spring, aucun
 * PostgreSQL, aucun {@code ArticleRepository}/{@code TagRepository}. Même
 * précédent exact que {@code catalogue.dto.CatalogueDtoTests} (DEV-06.3) et
 * {@code loan.dto.LoanDtoTests} (DEV-07.3) : constructeur statique {@code
 * from(...)} directement sur chaque Response record, pas de mapper dédié.
 *
 * <p>{@code UpdateArticleRequest} n'est volontairement pas couvert ici —
 * même précédent que {@code catalogue.dto.UpdateTitleRequest}/{@code
 * UpdateCopyRequest}/{@code UpdateGenreRequest}/{@code UpdateAuthorRequest},
 * jamais testés en isolation dans ce projet (Javadoc de la classe pour le
 * détail).
 */
class ArticleDtoTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    // ---------------------------------------------------------------
    // ArticleResponse — mapping par statut / auteur / dernier modificateur
    // ---------------------------------------------------------------

    @Test
    void articleResponseMapsADraftArticleWithNullPublishedAtAndNullLastModifiedBy() {
        Article article = baseArticle(ArticleStatus.DRAFT);

        ArticleResponse response = ArticleResponse.from(article, List.of());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
        assertThat(response.lastModifiedBy()).isNull();
        assertThat(response.author()).isNotNull();
    }

    @Test
    void articleResponseMapsAPublishedArticleWithPublishedAtAndLastModifiedBy() {
        Article article = baseArticle(ArticleStatus.PUBLISHED);
        Instant publishedAt = Instant.parse("2026-08-21T10:00:00Z");
        AppUser publisher = baseUser("publisher@primatis.test", "Éditeur", "Staff");
        article.setPublishedAt(publishedAt);
        article.setLastModifiedByUser(publisher);

        ArticleResponse response = ArticleResponse.from(article, List.of());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(publishedAt);
        assertThat(response.lastModifiedBy()).isNotNull();
        assertThat(response.lastModifiedBy().firstName()).isEqualTo("Éditeur");
    }

    @Test
    void articleResponseMapsAnArchivedArticlePreservingPublishedAt() {
        Article article = baseArticle(ArticleStatus.ARCHIVED);
        Instant publishedAt = Instant.parse("2026-08-15T09:00:00Z");
        article.setPublishedAt(publishedAt);

        ArticleResponse response = ArticleResponse.from(article, List.of());

        assertThat(response.articleStatus()).isEqualTo(ArticleStatus.ARCHIVED);
        assertThat(response.publishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void articleResponseMapsBaseFieldsAndTimestamps() {
        Instant createdAt = Instant.parse("2026-08-21T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-21T09:00:01Z");
        Article article = baseArticle(ArticleStatus.DRAFT);
        article.setTitle("Nouveaux horaires d'ouverture");
        article.setContent("<p>Contenu sanitisé</p>");
        article.setSummary("Résumé court");
        article.setSlug("nouveaux-horaires-douverture");
        article.setCreatedAt(createdAt);
        article.setUpdatedAt(updatedAt);

        ArticleResponse response = ArticleResponse.from(article, List.of());

        assertThat(response.id()).isEqualTo(article.getId());
        assertThat(response.title()).isEqualTo("Nouveaux horaires d'ouverture");
        assertThat(response.content()).isEqualTo("<p>Contenu sanitisé</p>");
        assertThat(response.summary()).isEqualTo("Résumé court");
        assertThat(response.slug()).isEqualTo("nouveaux-horaires-douverture");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void articleResponsePreservesNullSummary() {
        Article article = baseArticle(ArticleStatus.DRAFT);

        ArticleResponse response = ArticleResponse.from(article, List.of());

        assertThat(response.summary()).isNull();
    }

    @Test
    void articleResponseFromNullArticleThrowsExplicitly() {
        assertThatThrownBy(() -> ArticleResponse.from(null, List.of())).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // ArticleResponse — mapping des Tags
    // ---------------------------------------------------------------

    @Test
    void articleResponseMapsMultipleTagsPreservingOrder() {
        Article article = baseArticle(ArticleStatus.DRAFT);
        Tag first = baseTag("EVENEMENT", "Événement");
        Tag second = baseTag("TRAVAUX", "Travaux");

        ArticleResponse response = ArticleResponse.from(article, List.of(first, second));

        assertThat(response.tags()).extracting(TagResponse::code).containsExactly("EVENEMENT", "TRAVAUX");
    }

    @Test
    void articleResponseMapsNullTagsListToEmptyList() {
        Article article = baseArticle(ArticleStatus.DRAFT);

        ArticleResponse response = ArticleResponse.from(article, null);

        assertThat(response.tags()).isEmpty();
    }

    @Test
    void articleResponseRejectsNullElementInTagsListExplicitly() {
        Article article = baseArticle(ArticleStatus.DRAFT);
        Tag validTag = baseTag("EVENEMENT", "Événement");

        assertThatThrownBy(() -> ArticleResponse.from(article, Arrays.asList(validTag, null)))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // ArticleUserResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void articleUserResponseMapsIdentificationFieldsOnly() {
        AppUser user = baseUser("author@primatis.test", "Prénom", "Nom");

        ArticleUserResponse response = ArticleUserResponse.from(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.firstName()).isEqualTo("Prénom");
        assertThat(response.lastName()).isEqualTo("Nom");
    }

    @Test
    void articleUserResponseFromNullAppUserThrowsExplicitly() {
        assertThatThrownBy(() -> ArticleUserResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void articleUserResponseNeverExposesEmailMembershipOrAccountData() {
        // memberNumber volontairement exclu ici aussi (Javadoc
        // ArticleUserResponse) : un auteur/éditeur Article est typiquement
        // staff, sans adhésion associée.
        Set<String> forbidden = Set.of(
                "email", "phonenumber", "accountstatus", "membernumber",
                "memberstatus", "registrationdate", "memberexpirationdate", "blockedreason");

        assertThat(componentNamesLowercase(ArticleUserResponse.class))
                .as("ArticleUserResponse doit rester un résumé d'identification, jamais un profil complet")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // TagResponse — mapping complet
    // ---------------------------------------------------------------

    @Test
    void tagResponseMapsAllFields() {
        Tag tag = baseTag("EVENEMENT", "Événement");
        tag.setDescription("Annonces d'événements de la bibliothèque");

        TagResponse response = TagResponse.from(tag);

        assertThat(response.id()).isEqualTo(tag.getId());
        assertThat(response.code()).isEqualTo("EVENEMENT");
        assertThat(response.label()).isEqualTo("Événement");
        assertThat(response.description()).isEqualTo("Annonces d'événements de la bibliothèque");
    }

    @Test
    void tagResponsePreservesNullDescription() {
        Tag tag = baseTag("EVENEMENT", "Événement");

        TagResponse response = TagResponse.from(tag);

        assertThat(response.description()).isNull();
    }

    @Test
    void tagResponseFromNullTagThrowsExplicitly() {
        assertThatThrownBy(() -> TagResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // Anti-fuite structurelle
    // ---------------------------------------------------------------

    @Test
    void noArticleDtoComponentExposesAnEntityDirectlyOrThroughAGenericType() {
        Set<Class<?>> forbiddenEntityTypes = Set.of(Article.class, AppUser.class, Tag.class);
        List<Class<?>> articleDtos = List.of(ArticleResponse.class, ArticleUserResponse.class, TagResponse.class);

        for (Class<?> dto : articleDtos) {
            for (RecordComponent component : dto.getRecordComponents()) {
                assertThat(forbiddenEntityTypes)
                        .as("%s.%s ne doit pas exposer une Entity directement", dto.getSimpleName(), component.getName())
                        .doesNotContain(component.getType());

                for (Class<?> typeArgument : genericTypeArgumentsOf(component)) {
                    assertThat(forbiddenEntityTypes)
                            .as("%s.%s ne doit pas exposer une Entity via un type générique",
                                    dto.getSimpleName(), component.getName())
                            .doesNotContain(typeArgument);
                }
            }
        }
    }

    @Test
    void articleResponseNeverExposesArtificialComputedFlags() {
        // Aucun indicateur artificiel isPublished/canPublish/canArchive
        // calculé dans le mapper : le DTO reflète l'état persistant, jamais
        // une décision UI ou une règle métier précalculée.
        Set<String> forbidden = Set.of(
                "ispublished", "isdraft", "isarchived", "canpublish", "canarchive", "candelete", "haslock");

        assertThat(componentNamesLowercase(ArticleResponse.class))
                .as("ArticleResponse reflète l'état persistant, jamais une décision UI recalculée")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // CreateArticleRequest — validation structurelle
    // ---------------------------------------------------------------

    @Test
    void createArticleRequestAcceptsTitleAndContentWithoutSummary() {
        CreateArticleRequest request = new CreateArticleRequest("Titre valide", "Contenu valide", null);

        Set<ConstraintViolation<CreateArticleRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void createArticleRequestAcceptsOptionalSummary() {
        CreateArticleRequest request = new CreateArticleRequest("Titre valide", "Contenu valide", "Résumé");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createArticleRequestRejectsBlankTitle() {
        CreateArticleRequest request = new CreateArticleRequest(" ", "Contenu valide", null);

        Set<ConstraintViolation<CreateArticleRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("title");
    }

    @Test
    void createArticleRequestRejectsNullTitle() {
        CreateArticleRequest request = new CreateArticleRequest(null, "Contenu valide", null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("title");
    }

    @Test
    void createArticleRequestRejectsTitleLongerThan255Characters() {
        CreateArticleRequest request = new CreateArticleRequest("a".repeat(256), "Contenu valide", null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("title");
    }

    @Test
    void createArticleRequestAcceptsTitleOfExactly255Characters() {
        CreateArticleRequest request = new CreateArticleRequest("a".repeat(255), "Contenu valide", null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createArticleRequestRejectsBlankContent() {
        CreateArticleRequest request = new CreateArticleRequest("Titre valide", " ", null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
    }

    @Test
    void createArticleRequestRejectsNullContent() {
        CreateArticleRequest request = new CreateArticleRequest("Titre valide", null, null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
    }

    @Test
    void createArticleRequestNeverCarriesSlugStatusOrUserFields() {
        // Confirme structurellement qu'aucun champ hors périmètre (slug,
        // articleStatus, authorUser, lastModifiedByUser, publishedAt,
        // tagIds) n'a été ajouté à ce contrat — seuls title/content/summary
        // existent (business-rules.md §7.8/§7.12, DEV-DEC-0059/0060).
        assertThat(componentNamesLowercase(CreateArticleRequest.class))
                .containsExactlyInAnyOrder("title", "content", "summary");
    }

    private static List<Class<?>> genericTypeArgumentsOf(RecordComponent component) {
        Type genericType = component.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return List.of();
        }
        return Arrays.stream(parameterizedType.getActualTypeArguments())
                .filter(Class.class::isInstance)
                .<Class<?>>map(typeArgument -> (Class<?>) typeArgument)
                .toList();
    }

    private static Set<String> componentNamesLowercase(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private static AppUser baseUser(String email, String firstName, String lastName) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        return user;
    }

    private static Tag baseTag(String code, String label) {
        Tag tag = new Tag();
        tag.setCode(code);
        tag.setLabel(label);
        return tag;
    }

    private static Article baseArticle(ArticleStatus status) {
        AppUser author = baseUser("article-dto@primatis.test", "Prénom", "Nom");

        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle("Titre de test");
        article.setContent("Contenu de test");
        article.setSlug("titre-de-test");
        article.setArticleStatus(status);
        article.setCreatedAt(Instant.parse("2026-08-21T08:00:00Z"));
        article.setUpdatedAt(Instant.parse("2026-08-21T08:00:00Z"));
        return article;
    }
}
