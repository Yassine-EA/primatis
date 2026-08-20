package be.primatis.fine.dto;

import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Contrat REST de lecture d'une {@code Fine} (DEV-09.5), destiné aux
 * futures consultations staff ({@code GET /api/v1/fines}, DEV-09.9) et
 * self-service ({@code GET /api/v1/me/fines}) — <b>un seul contrat
 * réutilisé pour les deux contextes</b>, même précédent exact que
 * {@code loan.dto.LoanResponse}/{@code reservation.dto.ReservationResponse}
 * (tous deux systématiquement réutilisés tels quels entre {@code /me/*} et
 * la consultation staff, {@code borrower}/{@code member} toujours inclus
 * même côté self-service — aucun besoin réel constaté ici de séparer un
 * contrat staff enrichi). {@code borrower}/{@code loan} sont des résumés
 * compacts ({@link FineBorrowerResponse}/{@link FineLoanResponse}), jamais
 * les Entities {@code AppUser}/{@code Loan} exposées directement.
 *
 * <p>{@code fineStatus} exposé tel quel (enum {@link FineStatus}, pas de
 * traduction). {@code paidAt}/{@code cancelledAt} reflètent strictement
 * l'état persisté (l'un des deux au plus non {@code null}, garanti par
 * {@code ck_fine_status_consistency}, V001) — aucune réinterprétation,
 * aucun champ calculé/artificiel (pas d'{@code isOverdue}/{@code
 * isPayable}/{@code daysLate} : le DTO reflète l'état persistant, jamais
 * une décision UI, même principe que {@code ReservationResponse}, DEV-08.3
 * §5). Aucun calcul métier ici (montant/motif déjà persistés par le futur
 * {@code FineService}, DEV-09.6 — ce DTO ne fait que les représenter).
 */
public record FineResponse(
        Long id,
        FineBorrowerResponse borrower,
        FineLoanResponse loan,
        BigDecimal amount,
        String reason,
        Instant issuedAt,
        FineStatus fineStatus,
        Instant paidAt,
        Instant cancelledAt) {

    public static FineResponse from(Fine fine) {
        Objects.requireNonNull(fine, "fine");

        Loan loan = fine.getLoan();

        return new FineResponse(
                fine.getId(),
                FineBorrowerResponse.from(loan.getUser()),
                FineLoanResponse.from(loan),
                fine.getAmount(),
                fine.getReason(),
                fine.getIssuedAt(),
                fine.getFineStatus(),
                fine.getPaidAt(),
                fine.getCancelledAt());
    }
}
