package be.primatis.catalogue.dto;

import be.primatis.catalogue.CopyCondition;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Contrat REST de modification staff d'un {@code Copy} (DEV-06.6,
 * {@code COPY_MANAGE}). Même mécanisme exact de PATCH sparse que
 * {@link UpdateGenreRequest}/{@link UpdateAuthorRequest}/{@link
 * UpdateTitleRequest} : classe JavaBean, indicateur de présence par champ.
 *
 * <p>Volontairement absent : {@code availabilityStatus} (action dédiée
 * {@link UpdateCopyAvailabilityRequest}, {@code PATCH .../availability}) et
 * {@code titleId} (immuable dans DEV-06.6 — un Copy ne change pas de Title,
 * aucun transfert d'exemplaire implémenté).
 *
 * <p>{@code inventoryCode} : présent+{@code null}/blanc refusé (colonne
 * {@code NOT NULL}/{@code UNIQUE}). {@code location} (nullable) :
 * présent+{@code null} efface. {@code copyCondition} : présent+{@code null}
 * refusé (colonne {@code NOT NULL}) ; si la nouvelle valeur est {@code LOST}
 * ou {@code OUT_OF_SERVICE}, le Service impose {@code UNAVAILABLE} dans la
 * même transaction (invariant physique déjà FIGÉ) — sinon
 * {@code availabilityStatus} n'est jamais modifié par ce endpoint, y compris
 * s'il vaut déjà {@code ON_LOAN}/{@code RESERVED} (DEV-06.6 §30 : un PATCH
 * {@code inventoryCode}/{@code location}/{@code copyCondition=GOOD|DAMAGED}
 * ne touche jamais un {@code ON_LOAN}/{@code RESERVED} existant).
 */
public class UpdateCopyRequest {

    private String inventoryCode;
    private boolean inventoryCodePresent;

    private String location;
    private boolean locationPresent;

    private CopyCondition copyCondition;
    private boolean copyConditionPresent;

    public UpdateCopyRequest() {
    }

    public String getInventoryCode() {
        return inventoryCode;
    }

    public void setInventoryCode(String inventoryCode) {
        this.inventoryCode = inventoryCode;
        this.inventoryCodePresent = true;
    }

    @JsonIgnore
    public boolean isInventoryCodePresent() {
        return inventoryCodePresent;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
        this.locationPresent = true;
    }

    @JsonIgnore
    public boolean isLocationPresent() {
        return locationPresent;
    }

    public CopyCondition getCopyCondition() {
        return copyCondition;
    }

    public void setCopyCondition(CopyCondition copyCondition) {
        this.copyCondition = copyCondition;
        this.copyConditionPresent = true;
    }

    @JsonIgnore
    public boolean isCopyConditionPresent() {
        return copyConditionPresent;
    }
}
