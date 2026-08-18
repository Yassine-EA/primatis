package be.primatis.catalogue.dto;

import be.primatis.catalogue.TitleStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Contrat REST de transition {@code TitleStatus} (DEV-06.5,
 * {@code PATCH /api/v1/staff/titles/{titleId}/status}, {@code
 * CATALOGUE_MANAGE}). Seules {@code ACTIVE}/{@code WITHDRAWN} existent
 * (enum fermé) — aucune valeur supplémentaire n'est possible. Une valeur
 * textuelle hors enum échoue à la désérialisation Jackson elle-même (400
 * {@code MALFORMED_REQUEST_BODY}, infrastructure existante), avant que
 * {@code @NotNull} n'ait la moindre chance de s'appliquer.
 */
public record UpdateTitleStatusRequest(@NotNull TitleStatus status) {
}
