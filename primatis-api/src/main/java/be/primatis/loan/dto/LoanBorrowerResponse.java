package be.primatis.loan.dto;

import be.primatis.user.AppUser;

import java.util.Objects;

/**
 * Représentation compacte de l'emprunteur d'un {@code Loan} (DEV-07.3,
 * complétée en gate de conformité), embarquée dans {@link LoanResponse}.
 * Identification seule : {@code id}, {@code memberNumber} (identifiant
 * fonctionnel de l'adhérent — permet de distinguer correctement des
 * homonymes dans les futurs écrans Staff, contrairement à
 * {@code firstName}/{@code lastName} seuls), {@code firstName},
 * {@code lastName}. N'expose ni {@code email}, ni {@code phoneNumber}, ni
 * {@code AccountStatus}, ni {@code MemberStatus}, ni aucune date
 * d'adhésion, ni {@code blockedReason}, ni rôles/permissions — ces
 * données appartiennent au contrat {@code UserResponse} (DEV-05.4), pas à
 * un résumé Loan. Jamais l'Entity {@code AppUser} exposée directement.
 */
public record LoanBorrowerResponse(Long id, String memberNumber, String firstName, String lastName) {

    public static LoanBorrowerResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new LoanBorrowerResponse(
                appUser.getId(), appUser.getMemberNumber(), appUser.getFirstName(), appUser.getLastName());
    }
}
