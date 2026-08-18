package be.primatis.loan.dto;

import be.primatis.catalogue.Copy;

import java.util.Objects;

/**
 * Représentation compacte du {@code Copy} d'un {@code Loan} (DEV-07.3),
 * embarquée dans {@link LoanResponse}. Même principe que
 * {@code catalogue.dto.CopyResponse} : {@code titleId} seul, jamais un
 * {@code Title}/{@code TitleResponse} imbriqué — pas de graphe de DTO
 * récursif. N'expose ni {@code location}, ni {@code copyCondition}, ni
 * {@code availabilityStatus} : ces informations restent du ressort du
 * contrat {@code CopyResponse} (staff, {@code COPY_READ}/{@code COPY_MANAGE}),
 * pas d'un résumé Loan accessible potentiellement à un contexte
 * {@code LOAN_READ}/self-service distinct.
 */
public record LoanCopyResponse(Long id, String inventoryCode, Long titleId) {

    public static LoanCopyResponse from(Copy copy) {
        Objects.requireNonNull(copy, "copy");
        return new LoanCopyResponse(copy.getId(), copy.getInventoryCode(), copy.getTitle().getId());
    }
}
