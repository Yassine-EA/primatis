package be.primatis.catalogue.dto;

import be.primatis.catalogue.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Contrat REST de l'action dédiée de disponibilité manuelle d'un
 * {@code Copy} (DEV-06.6, {@code PATCH .../availability},
 * {@code COPY_MANAGE}). Champ nommé {@code status}, même convention que
 * {@link UpdateTitleStatusRequest} (action dédiée équivalente côté Title).
 * Seules {@code AVAILABLE}/{@code UNAVAILABLE} sont des cibles d'écriture
 * manuelle valides ({@code ON_LOAN}/{@code RESERVED} — 409
 * {@code COPY_AVAILABILITY_WORKFLOW_MANAGED}, exclusivement gérés par les
 * futurs workflows Loan/Reservation, DEV-07/08) ; {@code AVAILABLE} est en
 * outre refusé si {@code copyCondition} vaut {@code LOST}/
 * {@code OUT_OF_SERVICE} (409 {@code COPY_CONDITION_REQUIRES_UNAVAILABLE}).
 */
public record UpdateCopyAvailabilityRequest(@NotNull AvailabilityStatus status) {
}
