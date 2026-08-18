package be.primatis.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Contrat REST minimal de création staff d'un {@code Loan} (DEV-07.3),
 * destiné au futur {@code POST /api/v1/loans} ({@code LOAN_MANAGE},
 * DEV-07.5). Validation strictement structurelle : {@code @NotNull}/
 * {@code @Positive} sur les deux identifiants. **Aucune règle métier ici**
 * — ni la limite de 5 Loans actifs, ni {@code MemberStatus}, ni Fine
 * {@code UNPAID}, ni {@code AvailabilityStatus}, ni Reservation : toutes
 * appartiennent exclusivement au futur {@code LoanService} (DEV-07.5).
 */
public record CreateLoanRequest(
        @NotNull @Positive Long borrowerUserId,
        @NotNull @Positive Long copyId) {
}
