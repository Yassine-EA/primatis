package be.primatis.fine.dto;

import be.primatis.loan.Loan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Représentation compacte du {@code Loan} auquel une {@code Fine} est
 * rattachée (DEV-09.5), embarquée dans {@link FineResponse} — identifie
 * exactement le prêt à l'origine de l'amende, sans dupliquer le contrat
 * complet {@code loan.dto.LoanResponse}. Candidat minimal recommandé par
 * la mission DEV-09.5 §4 : {@code id}, {@code loanDate}, {@code dueDate},
 * {@code returnDate}, plus une identification du Copy ({@link
 * FineCopyResponse}, jamais {@code Copy} exposé directement). N'expose ni
 * {@code loanStatus}, ni {@code notes}, ni {@code createdAt}/{@code
 * updatedAt} du Loan : ces détails restent du ressort du contrat
 * {@code LoanResponse} (consultation Loan dédiée), pas d'un résumé Fine.
 * Le borrower du Loan est exposé séparément au niveau de
 * {@link FineResponse} ({@link FineBorrowerResponse}), jamais dupliqué ici.
 */
public record FineLoanResponse(Long id, Instant loanDate, LocalDate dueDate, LocalDate returnDate, FineCopyResponse copy) {

    public static FineLoanResponse from(Loan loan) {
        Objects.requireNonNull(loan, "loan");
        return new FineLoanResponse(
                loan.getId(),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                FineCopyResponse.from(loan.getCopy()));
    }
}
