package be.primatis.reservation.dto;

import be.primatis.user.AppUser;

import java.util.Objects;

/**
 * Représentation compacte du membre demandeur d'une {@code Reservation}
 * (DEV-08.3), embarquée dans {@link ReservationResponse}. Même principe
 * exact que {@code loan.dto.LoanBorrowerResponse} (identification seule :
 * {@code id}, {@code memberNumber}, {@code firstName}, {@code lastName}) —
 * délibérément non réutilisé tel quel depuis le domaine {@code loan} pour
 * garder chaque domaine propriétaire de son propre contrat (même principe
 * déjà appliqué par {@code LoanCopyResponse}, qui n'expose pas non plus le
 * contrat {@code catalogue.dto.CopyResponse}). N'expose ni {@code email},
 * ni {@code phoneNumber}, ni {@code AccountStatus}, ni {@code MemberStatus},
 * ni aucune date d'adhésion, ni {@code blockedReason}, ni rôles/permissions
 * — ces données appartiennent au contrat {@code UserResponse} (DEV-05.4),
 * pas à un résumé Reservation. Jamais l'Entity {@code AppUser} exposée
 * directement.
 */
public record ReservationMemberResponse(Long id, String memberNumber, String firstName, String lastName) {

    public static ReservationMemberResponse from(AppUser appUser) {
        Objects.requireNonNull(appUser, "appUser");
        return new ReservationMemberResponse(
                appUser.getId(), appUser.getMemberNumber(), appUser.getFirstName(), appUser.getLastName());
    }
}
