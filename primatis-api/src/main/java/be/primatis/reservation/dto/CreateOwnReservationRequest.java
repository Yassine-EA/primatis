package be.primatis.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Contrat REST minimal de création self-service d'une {@code Reservation}
 * (DEV-08.5, DEV-DEC-0037), destiné à {@code POST /api/v1/me/reservations}.
 * Aucun identifiant utilisateur ici — l'identité du membre est dérivée
 * exclusivement du JWT par le Controller, jamais du corps de la requête
 * (ownership backend). Validation strictement structurelle :
 * {@code @NotNull}/{@code @Positive} sur {@code titleId} — **aucune règle
 * métier ici** : ni l'adhésion active, ni la disponibilité d'un Copy, ni le
 * doublon actif, ni la limite de réservations actives, toutes appartenant
 * exclusivement au futur {@code ReservationService} (DEV-08.5).
 */
public record CreateOwnReservationRequest(@NotNull @Positive Long titleId) {
}
