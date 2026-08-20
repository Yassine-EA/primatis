package be.primatis.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Contrat REST minimal de création staff d'une {@code Reservation}
 * (DEV-08.5, DEV-DEC-0037), destiné à {@code POST /api/v1/reservations}
 * ({@code RESERVATION_MANAGE}). Distinct de {@link CreateOwnReservationRequest}
 * : {@code userId} est fourni explicitement par le staff, jamais déduit du
 * JWT de l'appelant. Validation strictement structurelle :
 * {@code @NotNull}/{@code @Positive} sur les deux identifiants — **aucune
 * règle métier ici** : ni l'adhésion active, ni la disponibilité d'un Copy,
 * ni le doublon actif, ni la limite de réservations actives, toutes
 * appartenant exclusivement au futur {@code ReservationService} (DEV-08.5).
 */
public record CreateReservationRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long titleId) {
}
