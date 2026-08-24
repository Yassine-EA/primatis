package be.primatis.article.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Contrat REST de modification staff d'un {@code Tag} (DEV-11.9, {@code
 * ARTICLE_MANAGE}). Même mécanisme exact de PATCH sparse que {@link
 * be.primatis.catalogue.dto.UpdateGenreRequest}/{@link UpdateArticleRequest}.
 *
 * <p>Volontairement absent : {@code code} — contrairement à {@code
 * UpdateGenreRequest} (qui autorise un changement de {@code code} revérifié
 * en unicité), {@code Tag.code} est structurellement immuable après création
 * (business-rules.md §7.13 : « stable »). Il ne s'agit pas d'un refus
 * applicatif d'un {@code code} fourni identique, mais d'une impossibilité
 * structurelle d'en fournir un via ce contrat.
 *
 * <p>{@code label} : présent+{@code null}/blanc refusé (colonne {@code NOT
 * NULL}). {@code description} (nullable) : présent+{@code null} efface.
 */
public class UpdateTagRequest {

    private String label;
    private boolean labelPresent;

    private String description;
    private boolean descriptionPresent;

    public UpdateTagRequest() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
        this.labelPresent = true;
    }

    @JsonIgnore
    public boolean isLabelPresent() {
        return labelPresent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonIgnore
    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }
}
