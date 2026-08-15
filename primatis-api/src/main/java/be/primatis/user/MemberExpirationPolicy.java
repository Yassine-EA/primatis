package be.primatis.user;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Détermine si l'adhésion d'un {@code AppUser} est expirée et synchronise
 * {@code memberStatus} en conséquence (DEV-05.7). Frontière de validité :
 * {@code memberExpirationDate} est le dernier jour valide inclus —
 * {@code memberExpirationDate == today} reste {@code ACTIVE},
 * {@code memberExpirationDate < today} devient {@code EXPIRED}
 * (Implementation Freedom, aucune source ne fixant cette frontière
 * exacte — cf. decision-log.md).
 *
 * Ne synchronise jamais un membre {@code BLOCKED} : le blocage reste
 * prioritaire sur l'expiration jusqu'à un déblocage explicite ({@code
 * UserService.unblockMember}, qui réévalue alors l'expiration lui-même).
 */
@Component
public class MemberExpirationPolicy {

    private final Clock clock;

    public MemberExpirationPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean isExpired(AppUser user) {
        LocalDate expirationDate = user.getMemberExpirationDate();
        return expirationDate != null && expirationDate.isBefore(LocalDate.now(clock));
    }

    /**
     * Ne transitionne que depuis {@code ACTIVE} : un membre {@code BLOCKED}
     * n'est jamais touché ici (priorité du blocage), un membre déjà {@code
     * EXPIRED} ou sans Membership ({@code memberStatus == null}) n'a rien à
     * synchroniser.
     *
     * @return {@code true} si {@code memberStatus} a été transitionné vers
     * {@code EXPIRED} par cet appel.
     */
    public boolean syncIfNeeded(AppUser user) {
        if (user.getMemberStatus() == MemberStatus.ACTIVE && isExpired(user)) {
            user.setMemberStatus(MemberStatus.EXPIRED);
            return true;
        }
        return false;
    }
}
