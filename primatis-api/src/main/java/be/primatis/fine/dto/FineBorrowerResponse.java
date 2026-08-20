package be.primatis.fine.dto;

import be.primatis.user.AppUser;

import java.util.Objects;

/**
 * Représentation compacte de l'emprunteur du {@code Loan} auquel une
 * {@code Fine} est rattachée (DEV-09.5), embarquée dans
 * {@link FineResponse}. Nommage {@code Borrower} aligné sur
 * {@code business-rules.md} §6.5 (« the recipient is the borrower of the
 * Fine's Loan »), pas {@code Member} — même principe exact que
 * {@code loan.dto.LoanBorrowerResponse} (identification seule : {@code id},
 * {@code memberNumber}, {@code firstName}, {@code lastName}), délibérément
 * non réutilisé tel quel depuis le domaine {@code loan} pour garder chaque
 * domaine propriétaire de son propre contrat (même principe déjà appliqué
 * par {@code reservation.dto.ReservationMemberResponse}). N'expose ni
 * {@code email}, ni {@code phoneNumber}, ni {@code AccountStatus}, ni
 * {@code MemberStatus}, ni aucune date d'adhésion, ni {@code blockedReason},
 * ni rôles/permissions — ces données appartiennent au contrat
 * {@code UserResponse} (DEV-05.4), pas à un résumé Fine. Jamais l'Entity
 * {@code AppUser} exposée directement.
 */
public record FineBorrowerResponse(Long id, String memberNumber, String firstName, String lastName) {

    public static FineBorrowerResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new FineBorrowerResponse(
                appUser.getId(), appUser.getMemberNumber(), appUser.getFirstName(), appUser.getLastName());
    }
}
