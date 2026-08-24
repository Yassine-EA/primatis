package be.primatis.article.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Contrat REST de modification éditoriale staff d'un {@code Article}
 * (préparation DEV-11.3, exposition HTTP différée à DEV-11.6+, {@code
 * ARTICLE_MANAGE}). Applicable à un Article {@code DRAFT} ou {@code
 * PUBLISHED} (business-rules.md §7.4/§7.12) : la modification d'un Article
 * {@code PUBLISHED} ne le fait jamais repasser en {@code DRAFT} — ce
 * contrat ne porte d'ailleurs aucun champ de statut. Volontairement absent
 * : {@code slug} (stable, jamais régénéré par une simple édition,
 * business-rules.md §7.8), {@code articleStatus} (actions dédiées {@code
 * publish}/{@code archive}, DEV-11.7/DEV-11.8 — jamais un moyen détourné
 * de changer le cycle de vie via ce contrat), {@code authorUser} (immuable,
 * DEV-DEC-0059), {@code lastModifiedByUser} (dérivé de l'utilisateur
 * authentifié par le futur Service, jamais fourni par le client), {@code
 * publishedAt}/{@code createdAt}/{@code updatedAt}.
 *
 * <p><b>Sémantique PATCH sparse à trois états</b> — {@code ABSENT} /
 * {@code PRESENT(valeur)} / {@code PRESENT(null)} — même mécanisme exact
 * que {@link be.primatis.catalogue.dto.UpdateTitleRequest} (DEV-06.5) et
 * {@code user.web.UpdateUserRequest} (DEV-05.6) : classe JavaBean, un
 * indicateur de présence dédié par champ, positionné uniquement par le
 * setter Jackson (jamais appelé pour une clé JSON absente). Aucune
 * annotation Bean Validation ici — même précédent que {@code
 * UpdateTitleRequest} : {@code title}/{@code content} n'ont pas de
 * sémantique de {@code clear} (non-nullables en base) et un présent+{@code
 * null} sera refusé par le futur Service, pas ici (contrainte
 * conditionnelle sur la présence, pas une contrainte de forme). {@code
 * summary} (nullable en base) : présent+{@code null} efface le résumé.
 *
 * <p>Non testé unitairement dans {@code ArticleDtoTests} — même précédent
 * que {@code UpdateTitleRequest}/{@code UpdateCopyRequest}/{@code
 * UpdateGenreRequest}/{@code UpdateAuthorRequest}, jamais couverts par un
 * test DTO isolé dans ce projet : la sémantique de présence n'a de valeur
 * observable qu'une fois consommée par un Service (couverture prévue en
 * DEV-11.6+, cf. {@code CatalogueServiceTests} pour le précédent exact).
 */
public class UpdateArticleRequest {

    private String title;
    private boolean titlePresent;

    private String content;
    private boolean contentPresent;

    private String summary;
    private boolean summaryPresent;

    public UpdateArticleRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    @JsonIgnore
    public boolean isTitlePresent() {
        return titlePresent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.contentPresent = true;
    }

    @JsonIgnore
    public boolean isContentPresent() {
        return contentPresent;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        this.summaryPresent = true;
    }

    @JsonIgnore
    public boolean isSummaryPresent() {
        return summaryPresent;
    }
}
