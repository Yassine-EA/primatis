package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;

import java.time.LocalDate;

/**
 * Contrat REST du profil personnel de l'utilisateur authentifié (DEV-05.9,
 * {@code GET}/{@code PATCH /api/v1/me/profile}). Exclut volontairement
 * {@code roles}, {@code permissions} (déjà disponibles côté client via le
 * JWT, DEV-05.9 audit §5/§9 — ne pas dupliquer), {@code createdAt}/{@code
 * updatedAt}/{@code lastLoginAt} (aucun besoin identifié pour un profil
 * personnel) et {@code Residence} (DEV-05.9-DEC-01, reste sur {@code
 * /api/v1/me/residence}). Ne réutilise pas {@link
 * be.primatis.user.web.UserResponse} : contrat distinct, volontairement pas
 * automatiquement synchronisé avec la vue staff.
 */
public record MeProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        AccountStatus accountStatus,
        String memberNumber,
        MemberStatus memberStatus,
        LocalDate registrationDate,
        LocalDate memberExpirationDate,
        String blockedReason) {

    public static MeProfileResponse from(AppUser appUser) {
        return new MeProfileResponse(
                appUser.getId(),
                appUser.getEmail(),
                appUser.getFirstName(),
                appUser.getLastName(),
                appUser.getPhoneNumber(),
                appUser.getAccountStatus(),
                appUser.getMemberNumber(),
                appUser.getMemberStatus(),
                appUser.getRegistrationDate(),
                appUser.getMemberExpirationDate(),
                appUser.getBlockedReason());
    }
}
