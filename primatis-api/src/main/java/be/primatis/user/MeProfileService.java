package be.primatis.user;

import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.MeProfileResponse;
import be.primatis.user.web.UpdateMeProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Profil personnel de l'utilisateur authentifié (DEV-05.9). Aucun {@code
 * @PreAuthorize} : {@code selfUserId} provient exclusivement de l'identité
 * authentifiée ({@code MeProfileController}), jamais du client — ownership
 * structurel, aucune permission {@code *_SELF} (DEV-05.9-DEC-10). Ne modifie
 * jamais {@code firstName}/{@code lastName}/{@code email}/{@code
 * accountStatus}/données Membership (DEV-05.9-DEC-02).
 */
@Service
public class MeProfileService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";

    private final AppUserRepository appUserRepository;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final Clock clock;

    public MeProfileService(
            AppUserRepository appUserRepository,
            MemberExpirationPolicy memberExpirationPolicy,
            PhoneNumberNormalizer phoneNumberNormalizer,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.clock = clock;
    }

    /**
     * Synchronise paresseusement {@code memberStatus} avant de répondre,
     * même principe que {@code UserService.getUserById} (DEV-05.9-DEC-03) :
     * lecture non {@code readOnly} car cette méthode peut légitimement
     * écrire (transition {@code ACTIVE → EXPIRED}).
     */
    @Transactional
    public MeProfileResponse getOwnProfile(Long selfUserId) {
        AppUser user = getRequiredUser(selfUserId);
        memberExpirationPolicy.syncIfNeeded(user);
        return MeProfileResponse.from(user);
    }

    /**
     * Sparse PATCH sur {@code phoneNumber} uniquement (DEV-05.9-DEC-06/
     * DEC-13) : absent, {@code null}, vide, blanc, ou normalisé identique à
     * la valeur déjà stockée sont tous des no-op — {@code AppUser} n'est
     * alors ni muté ni {@code updatedAt} rafraîchi. {@code phoneNumber} est
     * déjà garanti valide à ce stade par {@code @ValidPhoneNumber}
     * (Controller) : {@link PhoneNumberNormalizer#normalizeToE164} ne
     * devrait jamais retourner {@link java.util.Optional#empty()} ici.
     */
    @Transactional
    public MeProfileResponse updateOwnProfile(Long selfUserId, UpdateMeProfileRequest request) {
        AppUser user = getRequiredUser(selfUserId);

        String rawPhoneNumber = request.phoneNumber();
        if (rawPhoneNumber != null && !rawPhoneNumber.isBlank()) {
            String normalized = phoneNumberNormalizer.normalizeToE164(rawPhoneNumber)
                    .orElseThrow(() -> new IllegalStateException(
                            "phoneNumber invalide malgré la validation @ValidPhoneNumber préalable."));
            if (!normalized.equals(user.getPhoneNumber())) {
                user.setPhoneNumber(normalized);
                user.setUpdatedAt(clock.instant());
            }
        }

        memberExpirationPolicy.syncIfNeeded(user);
        return MeProfileResponse.from(user);
    }

    private AppUser getRequiredUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + userId + "."));
    }
}
