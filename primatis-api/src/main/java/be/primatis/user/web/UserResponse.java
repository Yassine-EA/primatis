package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Contrat REST de lecture d'un {@code AppUser} (DEV-05.4 USER_READ,
 * DEV-05.9 {@code /me/profile}). Ne reproduit pas automatiquement toutes les
 * propriétés de l'Entity : exclut volontairement les données internes
 * d'authentification ({@code passwordHash}, {@code failedLoginCount},
 * {@code lockedUntil}) et {@code lastLoginAt}, aucun besoin API ne
 * justifiant encore son exposition.
 *
 * {@code memberNumber}/{@code memberStatus}/{@code registrationDate}/
 * {@code memberExpirationDate}/{@code blockedReason} sont fonctionnellement
 * liés à l'adhésion (Membership), pas à la sécurité login, et peuvent donc
 * être {@code null} : un {@code AppUser} n'est pas nécessairement adhérent
 * (database-model.md §8.3).
 *
 * N'expose ni rôles ni permissions : aucune source ne fixe encore ce besoin
 * pour la liste/détail USER_READ (distinct de l'administration RBAC), et
 * les reconstruire ici risquerait de dupliquer la matrice rôle → permission
 * déjà autoritaire côté JWT/{@code PrimatisUserDetailsService}.
 */
public record UserResponse(
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
        String blockedReason,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(AppUser appUser) {
        return new UserResponse(
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
                appUser.getBlockedReason(),
                appUser.getCreatedAt(),
                appUser.getUpdatedAt());
    }
}
