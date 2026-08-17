package be.primatis.catalogue.dto;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.CopyCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Contrat REST de création staff d'un {@code Copy} (DEV-06.6,
 * {@code COPY_MANAGE}) sous
 * {@code POST /api/v1/staff/titles/{titleId}/copies}. {@code titleId} vient
 * exclusivement du path, jamais du body. Aucun défaut métier caché :
 * {@code copyCondition}/{@code availabilityStatus} sont tous deux
 * obligatoires — le Service valide leur combinaison (LOST/OUT_OF_SERVICE
 * exigent UNAVAILABLE) et refuse {@code ON_LOAN}/{@code RESERVED} à la
 * création (exclusivement gérés par les futurs workflows Loan/Reservation,
 * DEV-07/08).
 */
public record CreateCopyRequest(
        @NotBlank String inventoryCode,
        String location,
        @NotNull CopyCondition copyCondition,
        @NotNull AvailabilityStatus availabilityStatus) {
}
