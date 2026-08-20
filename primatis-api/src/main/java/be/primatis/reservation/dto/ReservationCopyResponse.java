package be.primatis.reservation.dto;

import be.primatis.catalogue.Copy;

import java.util.Objects;

/**
 * Représentation compacte du {@code Copy} affecté à une {@code Reservation}
 * {@code READY} (ou conservé après {@code FULFILLED}, DEV-08.1 §8) —
 * DEV-08.3, embarquée dans {@link ReservationResponse} lorsque
 * {@code assignedCopy} n'est pas {@code null}. Même forme exacte que
 * {@code loan.dto.LoanCopyResponse} ({@code id}, {@code inventoryCode},
 * {@code titleId} seul — jamais un {@code Title}/{@code ReservationTitleResponse}
 * imbriqué, pas de graphe de DTO récursif), délibérément non réutilisée
 * telle quelle depuis le domaine {@code loan} pour garder chaque domaine
 * propriétaire de son propre contrat. N'expose ni {@code location}, ni
 * {@code copyCondition}, ni {@code availabilityStatus} : ces informations
 * restent du ressort du contrat {@code CopyResponse} (staff,
 * {@code COPY_READ}/{@code COPY_MANAGE}), pas d'un résumé Reservation.
 */
public record ReservationCopyResponse(Long id, String inventoryCode, Long titleId) {

    public static ReservationCopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new ReservationCopyResponse(copy.getId(), copy.getInventoryCode(), copy.getTitle().getId());
    }
}
