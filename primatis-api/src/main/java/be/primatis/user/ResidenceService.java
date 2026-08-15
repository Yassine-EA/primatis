package be.primatis.user;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.ResidenceResponse;
import be.primatis.user.web.UpdateResidenceRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Address/Residence pour tout {@code AppUser} (DEV-05.8), indépendamment de
 * {@code ROLE_MEMBER}/{@code memberNumber}/{@code MemberStatus}
 * (DEV-05.8-DEC-04). Deux familles de méthodes par opération : une variante
 * "own" sans {@code @PreAuthorize} (ownership structurel — {@code userId}
 * toujours dérivé de l'identité authentifiée par le Controller, jamais du
 * client, DEV-05.8-DEC-07) et une variante staff protégée par {@code
 * USER_READ}/{@code USER_PROFILE_MANAGE}. Les deux variantes d'écriture
 * délèguent au même workflow privé pour ne pas dupliquer la logique métier.
 */
@Service
public class ResidenceService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String RESIDENCE_PERIOD_CONFLICT_CODE = "RESIDENCE_PERIOD_CONFLICT";

    /**
     * Noms des contraintes PostgreSQL (V001) dont la violation traduit
     * spécifiquement un conflit de période de résidence. Toute autre
     * {@link DataIntegrityViolationException} continue de remonter telle
     * quelle (traduction volontairement non globale — instruction explicite
     * DEV-05.8, §2).
     */
    private static final Set<String> RESIDENCE_CONFLICT_CONSTRAINTS =
            Set.of("ux_residence_current_per_user", "ck_residence_dates");

    private final AppUserRepository appUserRepository;
    private final CityRepository cityRepository;
    private final AddressRepository addressRepository;
    private final ResidenceRepository residenceRepository;
    private final Clock clock;

    public ResidenceService(
            AppUserRepository appUserRepository,
            CityRepository cityRepository,
            AddressRepository addressRepository,
            ResidenceRepository residenceRepository,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.cityRepository = cityRepository;
        this.addressRepository = addressRepository;
        this.residenceRepository = residenceRepository;
        this.clock = clock;
    }

    /**
     * Résidence courante de l'utilisateur authentifié ({@code GET
     * /api/v1/me/residence}). Aucun {@code @PreAuthorize} : {@code
     * selfUserId} provient exclusivement de l'identité authentifiée
     * (Controller), jamais d'un paramètre client — ownership structurel, pas
     * une permission vérifiable.
     */
    @Transactional(readOnly = true)
    public ResidenceResponse getOwnCurrentResidence(Long selfUserId) {
        return getCurrentResidenceOrThrow(selfUserId);
    }

    /**
     * Résidence courante d'un utilisateur cible ({@code GET
     * /api/v1/users/{id}/residence}, {@code USER_READ}).
     */
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public ResidenceResponse getCurrentResidence(Long userId) {
        return getCurrentResidenceOrThrow(userId);
    }

    /**
     * Historique complet d'un utilisateur cible, {@code startDate} DESC
     * ({@code GET /api/v1/users/{id}/residences}, {@code USER_READ}).
     * Liste vide (jamais 404) en l'absence d'historique (DEV-05.8-DEC-11).
     */
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public List<ResidenceResponse> getResidenceHistory(Long userId) {
        requireUser(userId);
        return residenceRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(ResidenceResponse::from)
                .toList();
    }

    /**
     * Définit/remplace la résidence courante de l'utilisateur authentifié
     * ({@code PUT /api/v1/me/residence}). Aucun {@code @PreAuthorize} : même
     * raisonnement d'ownership structurel que {@link #getOwnCurrentResidence}.
     */
    @Transactional
    public ResidenceResponse replaceOwnResidence(Long selfUserId, UpdateResidenceRequest request) {
        return doReplaceResidence(selfUserId, request);
    }

    /**
     * Définit/remplace la résidence courante d'un utilisateur cible ({@code
     * PUT /api/v1/users/{id}/residence}, {@code USER_PROFILE_MANAGE} —
     * jamais {@code USER_MANAGE}, réservé à l'administration de compte,
     * DEV-05.8-DEC-08).
     */
    @PreAuthorize("hasAuthority('USER_PROFILE_MANAGE')")
    @Transactional
    public ResidenceResponse replaceResidence(Long userId, UpdateResidenceRequest request) {
        return doReplaceResidence(userId, request);
    }

    private ResidenceResponse getCurrentResidenceOrThrow(Long userId) {
        requireUser(userId);
        Residence current = residenceRepository.findByUserIdAndEndDateIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CURRENT_RESIDENCE_NOT_FOUND",
                        "Aucune résidence courante pour l'utilisateur " + userId + "."));
        return ResidenceResponse.from(current);
    }

    /**
     * Workflow métier DEV-05.8-DEC-01/05/12 : première résidence (aucune
     * fermeture) ou remplacement (fermeture de l'ancienne à {@code today - 1
     * jour}, puis création de la nouvelle, dans la même transaction).
     * {@code startDate}/{@code endDate} ne sont jamais fournis par le
     * client (DEV-05.8-DEC-05/DEC-10) : {@code today} vient exclusivement du
     * {@link Clock} injecté.
     *
     * <p>Trois niveaux de protection contre un conflit de période, du moins
     * au plus coûteux : (1) comparaison de dates déterministe — couvre le
     * cas normal (ex. déjà remplacée aujourd'hui) sans toucher la base ; (2)
     * {@link ResidenceRepository#existsOverlappingPeriod} réutilisé tel
     * quel, filet de sécurité applicatif ; (3) traduction ciblée d'une
     * {@link DataIntegrityViolationException} portée par {@code
     * ux_residence_current_per_user}/{@code ck_residence_dates} — dernier
     * rempart contre une vraie course concurrente, sans introduire de lock
     * pessimiste (décision explicite DEV-05.8, §2 : les contraintes
     * PostgreSQL existantes restent la protection structurelle finale).
     */
    private ResidenceResponse doReplaceResidence(Long targetUserId, UpdateResidenceRequest request) {
        AppUser targetUser = requireUser(targetUserId);
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("CITY_NOT_FOUND",
                        "Aucune ville pour l'identifiant " + request.cityId() + "."));

        LocalDate today = LocalDate.now(clock);
        Optional<Residence> currentOpt = residenceRepository.findByUserIdAndEndDateIsNull(targetUserId);

        LocalDate candidateEndDateForCurrent = null;
        if (currentOpt.isPresent()) {
            candidateEndDateForCurrent = today.minusDays(1);
            if (candidateEndDateForCurrent.isBefore(currentOpt.get().getStartDate())) {
                throw new BusinessRuleException(RESIDENCE_PERIOD_CONFLICT_CODE,
                        "La résidence courante a déjà été remplacée aujourd'hui.");
            }
        }

        Address address = new Address();
        address.setCity(city);
        address.setStreet(request.street());
        address.setStreetNumber(request.streetNumber());
        address.setBoxNumber(request.boxNumber());
        address.setAdditionalInfo(request.additionalInfo());

        Residence newResidence = new Residence();
        newResidence.setUser(targetUser);
        newResidence.setAddress(address);
        newResidence.setStartDate(today);
        newResidence.setEndDate(null);

        try {
            if (currentOpt.isPresent()) {
                currentOpt.get().setEndDate(candidateEndDateForCurrent);
            }
            if (residenceRepository.existsOverlappingPeriod(targetUserId, today, null)) {
                throw new BusinessRuleException(RESIDENCE_PERIOD_CONFLICT_CODE,
                        "La nouvelle période de résidence chevauche une résidence existante.");
            }
            addressRepository.save(address);
            residenceRepository.saveAndFlush(newResidence);
        } catch (DataIntegrityViolationException ex) {
            if (isResidenceConstraintViolation(ex)) {
                throw new BusinessRuleException(RESIDENCE_PERIOD_CONFLICT_CODE,
                        "Conflit de période de résidence détecté par la base de données.");
            }
            throw ex;
        }

        return ResidenceResponse.from(newResidence);
    }

    /**
     * {@code getCause()} (le cause direct, {@code org.hibernate.exception.
     * ConstraintViolationException}) — pas {@code getMostSpecificCause()},
     * qui descendrait jusqu'au {@code PSQLException} JDBC brut, lequel ne
     * porte pas {@code getConstraintName()}.
     */
    private boolean isResidenceConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return RESIDENCE_CONFLICT_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }

    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + userId + "."));
    }
}
