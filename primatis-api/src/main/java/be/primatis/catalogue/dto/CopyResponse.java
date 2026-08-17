package be.primatis.catalogue.dto;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;

import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code Copy} (DEV-06.3, futur DEV-06.6 —
 * inventaire staff, {@code COPY_READ}/{@code COPY_MANAGE}). N'embarque ni
 * {@code TitleResponse}, ni l'Entity {@code Title}, ni aucune donnée
 * Loan/Reservation : {@code titleId} suffit à conserver l'association
 * contractuelle sans construire un graphe de DTO récursif. Reflète
 * uniquement l'état persisté — aucune transition {@code ON_LOAN}/
 * {@code RESERVED}/{@code AVAILABLE}/{@code UNAVAILABLE} n'est décidée ici.
 */
public record CopyResponse(
        Long id,
        Long titleId,
        String inventoryCode,
        String location,
        CopyCondition copyCondition,
        AvailabilityStatus availabilityStatus) {

    public static CopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new CopyResponse(
                copy.getId(),
                copy.getTitle().getId(),
                copy.getInventoryCode(),
                copy.getLocation(),
                copy.getCopyCondition(),
                copy.getAvailabilityStatus());
    }
}
