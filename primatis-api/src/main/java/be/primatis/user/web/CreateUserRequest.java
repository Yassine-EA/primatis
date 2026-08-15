package be.primatis.user.web;

import be.primatis.user.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.Set;

/**
 * Contrat REST de création administrative d'un {@code AppUser} (DEV-05.5,
 * {@code USER_MANAGE}). Volontairement absent : {@code id}, {@code
 * accountStatus} (toujours {@code ACTIVE} à la création), {@code
 * memberNumber} (généré backend), {@code password}/{@code passwordHash}
 * (généré backend), {@code createdAt}/{@code updatedAt}, {@code assignedBy}
 * (dérivé de l'identité authentifiée, jamais du client).
 *
 * {@code roles} : au moins un code de rôle choisi explicitement par
 * l'administrateur — aucun rôle automatique/implicite. Les codes sont
 * résolus contre le RBAC réel ({@code RoleRepository.findByCode}), jamais
 * contre un enum Java local.
 *
 * {@code memberStatus}/{@code registrationDate}/{@code memberExpirationDate}/
 * {@code blockedReason} : pertinents uniquement si {@code ROLE_MEMBER} fait
 * partie de {@code roles}. La cohérence exacte (requis si {@code
 * ROLE_MEMBER}, refusé sinon, {@code blockedReason} seulement si {@code
 * BLOCKED}, {@code memberExpirationDate >= registrationDate}) est validée
 * dans le Service, pas ici : ce sont des invariants conditionnels, pas des
 * contraintes de forme simples.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phoneNumber,
        @NotEmpty Set<String> roles,
        MemberStatus memberStatus,
        LocalDate registrationDate,
        LocalDate memberExpirationDate,
        String blockedReason) {
}
