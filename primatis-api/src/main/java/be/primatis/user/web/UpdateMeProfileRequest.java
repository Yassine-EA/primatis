package be.primatis.user.web;

/**
 * Contrat REST de modification du profil personnel (DEV-05.9, {@code PATCH
 * /api/v1/me/profile}). Seul {@code phoneNumber} est modifiable
 * (DEV-05.9-DEC-02/DEC-08) — {@code firstName}/{@code lastName}/{@code
 * email}/{@code accountStatus}/données Membership restent en lecture seule
 * via cet endpoint.
 *
 * Absent et {@code null} explicite produisent la même sémantique (aucune
 * modification, DEV-05.9-DEC-06) : un {@code record} classique suffit,
 * aucun pattern à indicateur de présence n'est nécessaire ici (contrairement
 * à {@code UpdateUserRequest}, qui distingue les deux cas pour d'autres
 * champs).
 */
public record UpdateMeProfileRequest(@ValidPhoneNumber String phoneNumber) {
}
