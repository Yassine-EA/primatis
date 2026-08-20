package be.primatis.fine.dto;

import be.primatis.catalogue.Copy;

import java.util.Objects;

/**
 * Représentation compacte du {@code Copy} du {@code Loan} auquel une
 * {@code Fine} est rattachée (DEV-09.5), embarquée dans
 * {@link FineLoanResponse}. Même forme exacte que
 * {@code loan.dto.LoanCopyResponse}/{@code reservation.dto.ReservationCopyResponse}
 * ({@code id}, {@code inventoryCode}, {@code titleId} seul — jamais un
 * {@code Title}/résumé Title imbriqué, pas de graphe de DTO récursif),
 * délibérément non réutilisée telle quelle depuis un autre domaine pour
 * garder {@code fine} propriétaire de son propre contrat. N'expose ni
 * {@code location}, ni {@code copyCondition}, ni {@code availabilityStatus} :
 * ces informations restent du ressort du contrat {@code CopyResponse}
 * (staff, {@code COPY_READ}/{@code COPY_MANAGE}), pas d'un résumé Fine.
 */
public record FineCopyResponse(Long id, String inventoryCode, Long titleId) {

    public static FineCopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new FineCopyResponse(copy.getId(), copy.getInventoryCode(), copy.getTitle().getId());
    }
}
