package be.primatis.article;

import be.primatis.article.dto.CreateTagRequest;
import be.primatis.article.dto.TagResponse;
import be.primatis.article.dto.UpdateTagRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * CRUD staff de {@code Tag} comme ressource séparée (DEV-11.9, {@code
 * ARTICLE_MANAGE}, DEV-DEC-0060 : aucune nouvelle permission RBAC). Même
 * précédent structurel exact que {@code CatalogueManagementService}
 * (Author/Genre, DEV-06.5.1) : Service dédié séparé de {@link ArticleService}
 * — la gestion du référentiel Tag et l'association Tag↔Article restent deux
 * responsabilités distinctes ({@link ArticleService#associateTags} reste
 * dans {@code ArticleService}, jamais ici).
 *
 * <p>Contrairement à {@code CatalogueManagementService} (Author/Genre,
 * volontairement sans {@code DELETE}, K.1 FIGÉE DEV-06.5.1), {@code Tag}
 * expose un {@code DELETE} explicitement demandé par la mission DEV-11.9 —
 * aucune source PRIMATIS n'interdit la suppression physique d'un {@code Tag}
 * non utilisé ; ce choix diverge donc délibérément du précédent Genre/Author
 * sans le contredire (celui-ci ne fermait le scope que pour DEV-06.5.1, pas
 * pour toute future ressource de référence). Le comportement exact ({@code
 * Tag} utilisé → refus) découle mécaniquement de {@code
 * fk_article_tag_tag_id ON DELETE RESTRICT} (V001), jamais d'une cascade
 * applicative inventée.
 */
@Service
public class TagService {

    private static final String TAG_NOT_FOUND_CODE = "TAG_NOT_FOUND";
    private static final String TAG_CODE_ALREADY_EXISTS_CODE = "TAG_CODE_ALREADY_EXISTS";
    private static final String TAG_LABEL_MUST_NOT_BE_BLANK_CODE = "TAG_LABEL_MUST_NOT_BE_BLANK";
    private static final String TAG_IN_USE_CODE = "TAG_IN_USE";

    private static final Set<String> TAG_CODE_DUPLICATE_CONSTRAINTS = Set.of("uq_tag_code");
    private static final Set<String> TAG_IN_USE_CONSTRAINTS = Set.of("fk_article_tag_tag_id");

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Liste paginée (même contrat exact que {@code
     * CatalogueManagementService.listGenres}) — tri {@code label ASC, id
     * ASC} imposé par le Controller (même précédent {@code
     * StaffGenreController.listGenres}), IMPLEMENTATION FREEDOM, aucune
     * source ne fixe d'ordre.
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<TagResponse> listTags(Pageable pageable) {
        return tagRepository.findAll(pageable).map(TagResponse::from);
    }

    /**
     * {@code code} pré-vérifié ({@link TagRepository#existsByCode}) puis
     * revérifié à l'écriture (course concurrente entre la vérification et
     * l'insertion traduite depuis {@code uq_tag_code} — même précédent exact
     * que {@code ArticleService.createDraftArticle} pour {@code
     * uq_article_slug}, jamais un retry automatique). {@code label} non
     * unique (V001, {@link TagRepository} javadoc) — aucune vérification
     * d'unicité sur {@code label}.
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public TagResponse createTag(CreateTagRequest request) {
        Objects.requireNonNull(request, "request");

        if (tagRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(TAG_CODE_ALREADY_EXISTS_CODE, "Un Tag existe déjà avec ce code.");
        }

        Tag tag = new Tag();
        tag.setCode(request.code());
        tag.setLabel(request.label());
        tag.setDescription(request.description());

        try {
            tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException ex) {
            if (isConstraintViolation(ex, TAG_CODE_DUPLICATE_CONSTRAINTS)) {
                throw new BusinessRuleException(TAG_CODE_ALREADY_EXISTS_CODE, "Un Tag existe déjà avec ce code.");
            }
            throw ex;
        }

        return TagResponse.from(tag);
    }

    /**
     * PATCH sparse (cf. {@link UpdateTagRequest}) — {@code code} jamais
     * modifiable par ce contrat (structurellement absent du DTO, business-
     * rules.md §7.13). {@code label} présent+vide refusé ; {@code
     * description} présent+{@code null} efface.
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public TagResponse updateTag(Long tagId, UpdateTagRequest request) {
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(request, "request");

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TAG_NOT_FOUND_CODE, "Aucun Tag pour l'identifiant " + tagId + "."));

        if (request.isLabelPresent()) {
            if (request.getLabel() == null || request.getLabel().isBlank()) {
                throw new BusinessRuleException(TAG_LABEL_MUST_NOT_BE_BLANK_CODE, "label ne peut pas être vide.");
            }
            tag.setLabel(request.getLabel());
        }
        if (request.isDescriptionPresent()) {
            tag.setDescription(request.getDescription());
        }

        return TagResponse.from(tag);
    }

    /**
     * Hard-delete d'un {@code Tag} non utilisé uniquement. Aucune
     * pré-vérification applicative de non-utilisation (aurait introduit une
     * fenêtre de course entre la vérification et la suppression) — {@code
     * fk_article_tag_tag_id ON DELETE RESTRICT} reste l'autorité finale,
     * traduite en {@code 409 TAG_IN_USE} (jamais une {@code
     * DataIntegrityViolationException} brute).
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public void deleteTag(Long tagId) {
        Objects.requireNonNull(tagId, "tagId");

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TAG_NOT_FOUND_CODE, "Aucun Tag pour l'identifiant " + tagId + "."));

        try {
            tagRepository.delete(tag);
            tagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isConstraintViolation(ex, TAG_IN_USE_CONSTRAINTS)) {
                throw new BusinessRuleException(
                        TAG_IN_USE_CODE, "Ce Tag est encore associé à au moins un Article et ne peut pas être supprimé.");
            }
            throw ex;
        }
    }

    private boolean isConstraintViolation(DataIntegrityViolationException ex, Set<String> constraintNames) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return constraintNames.contains(hibernateException.getConstraintName());
        }
        return false;
    }
}
