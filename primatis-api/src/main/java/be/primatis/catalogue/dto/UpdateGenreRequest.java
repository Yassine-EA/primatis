package be.primatis.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Contrat REST de modification staff d'un {@code Genre} (DEV-06.5.1,
 * {@code CATALOGUE_MANAGE}). Même mécanisme exact de PATCH sparse que
 * {@link UpdateAuthorRequest}/{@link UpdateTitleRequest}.
 *
 * <p>{@code code}/{@code label} : présent+{@code null}/blanc refusé
 * (colonnes {@code NOT NULL} et {@code UNIQUE}). En cas de changement réel
 * de valeur, l'unicité est revérifiée dans le Service (hors l'entité
 * courante) — un {@code code}/{@code label} resoumis identique à la valeur
 * actuelle n'est jamais traité comme un conflit avec lui-même.
 * {@code description} (nullable) : présent+{@code null} efface.
 */
public class UpdateGenreRequest {

    private String code;
    private boolean codePresent;

    private String label;
    private boolean labelPresent;

    private String description;
    private boolean descriptionPresent;

    public UpdateGenreRequest() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
        this.codePresent = true;
    }

    @JsonIgnore
    public boolean isCodePresent() {
        return codePresent;
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
