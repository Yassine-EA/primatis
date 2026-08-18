package be.primatis.loan.dto;

import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code Loan} (DEV-07.3), destiné aux futures
 * consultations staff ({@code LOAN_READ}, DEV-07.4) et self-service
 * ({@code GET /api/v1/me/loans}). {@code borrower}/{@code copy} sont des
 * résumés compacts ({@link LoanBorrowerResponse}/{@link LoanCopyResponse}),
 * jamais les Entities {@code AppUser}/{@code Copy} exposées directement.
 * {@code loanStatus} exposé tel quel (enum {@link LoanStatus}, pas de
 * traduction). {@code returnDate} reste {@code null} tant que le Loan est
 * ouvert (ACTIVE/OVERDUE) — reflète strictement l'état persisté, aucune
 * dérivation.
 */
public record LoanResponse(
        Long id,
        LoanBorrowerResponse borrower,
        LoanCopyResponse copy,
        Instant loanDate,
        LocalDate dueDate,
        LocalDate returnDate,
        LoanStatus loanStatus,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static LoanResponse from(Loan loan) {
        Objects.requireNonNull(loan, "loan");
        return new LoanResponse(
                loan.getId(),
                LoanBorrowerResponse.from(loan.getUser()),
                LoanCopyResponse.from(loan.getCopy()),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                loan.getLoanStatus(),
                loan.getNotes(),
                loan.getCreatedAt(),
                loan.getUpdatedAt());
    }
}
