package be.primatis.user;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.access.UserRoleRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.BlockMembershipRequest;
import be.primatis.user.web.CreateUserRequest;
import be.primatis.user.web.CreateUserResponse;
import be.primatis.user.web.UpdateAccountStatusRequest;
import be.primatis.user.web.UpdateUserRequest;
import be.primatis.user.web.UserDetailResponse;
import be.primatis.user.web.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lectures {@code USER_READ}, création/modification/statuts administratifs
 * {@code USER_MANAGE} sur {@code AppUser} (DEV-05.4→DEV-05.7). Orchestration
 * Repository + mapping uniquement. Ne modifie jamais {@code email}/{@code
 * memberNumber} (immuables), n'implémente ni renouvellement complet ni
 * forgot-password (hors scope V1).
 *
 * {@code @PreAuthorize} au niveau Service, jamais au niveau Controller,
 * conformément à la convention établie par
 * {@code RbacMethodSecuritySampleService} (DEV-03.9) pour les futurs
 * Services métier réels.
 */
@Service
public class UserService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String ROLE_MEMBER_CODE = "ROLE_MEMBER";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberNumberGenerator memberNumberGenerator;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final Clock clock;

    public UserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            MemberNumberGenerator memberNumberGenerator,
            MemberExpirationPolicy memberExpirationPolicy,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberNumberGenerator = memberNumberGenerator;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.clock = clock;
    }

    /**
     * Synchronise paresseusement {@code memberStatus} (DEV-05.7, {@link
     * MemberExpirationPolicy}) pour chaque {@code AppUser} de la page avant
     * de répondre : {@code listUsers} et {@link #getUserById} doivent
     * exposer la même vérité métier (contrat DEV-05.7 §9) — un
     * {@code ACTIVE} expiré ne doit jamais apparaître dans la liste puis
     * {@code EXPIRED} au détail. Lecture non {@code readOnly} car cette
     * méthode peut légitimement écrire. Aucune requête supplémentaire :
     * {@code syncIfNeeded} mute des Entities déjà chargées par {@code
     * findAll(Pageable)}, la synchronisation se limite au flush du
     * persistance-context déjà géré par la transaction — pas de N+1, une
     * page contenant au maximum 100 utilisateurs (contrat pagination).
     */
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    public Page<UserResponse> listUsers(Pageable pageable) {
        Page<AppUser> page = appUserRepository.findAll(pageable);
        page.forEach(memberExpirationPolicy::syncIfNeeded);
        return page.map(UserResponse::from);
    }

    /**
     * Synchronise paresseusement {@code memberStatus} avant de répondre :
     * lecture non {@code readOnly} car cette méthode peut légitimement
     * écrire (transition {@code ACTIVE → EXPIRED}).
     *
     * Compose {@code roles} (DEV-05.12 Décision 13) via {@link
     * UserRoleRepository#findByIdUserId(Long)}, triés pour une sortie
     * déterministe — requête bornée aux rôles du seul utilisateur demandé,
     * jamais appliquée à {@link #listUsers} (aucun N+1 introduit sur la
     * liste paginée).
     */
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    public UserDetailResponse getUserById(Long id) {
        AppUser user = getRequiredUser(id);
        memberExpirationPolicy.syncIfNeeded(user);
        List<String> roles = userRoleRepository.findByIdUserId(user.getId()).stream()
                .map(userRole -> userRole.getRole().getCode())
                .sorted()
                .toList();
        return new UserDetailResponse(UserResponse.from(user), roles);
    }

    /**
     * Création administrative (DEV-05.5). {@code adminUserId} provient
     * exclusivement de l'identité authentifiée (JWT {@code sub}), jamais du
     * corps de la requête — voir {@code UserController}. Une seule
     * transaction couvre validation, {@code AppUser} et {@code UserRole} :
     * en cas d'échec, aucun {@code AppUser} partiellement créé (un numéro de
     * séquence {@code memberNumber} déjà consommé peut rester perdu, ce qui
     * est accepté explicitement — cf. {@link MemberNumberGenerator}).
     */
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request, Long adminUserId) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    "USER_EMAIL_ALREADY_EXISTS", "Un utilisateur existe déjà avec cet email.");
        }

        boolean memberRoleSelected = request.roles().contains(ROLE_MEMBER_CODE);
        validateMembershipCoherence(memberRoleSelected, request);

        Set<Role> roles = new LinkedHashSet<>();
        for (String code : request.roles()) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException(
                            "UNKNOWN_ROLE_CODE", "Le rôle demandé n'existe pas : " + code + "."));
            roles.add(role);
        }

        String initialPassword = PasswordGenerator.generate();
        String passwordHash = passwordEncoder.encode(initialPassword);
        String memberNumber = memberRoleSelected ? memberNumberGenerator.generateNext() : null;

        Instant now = clock.instant();
        AppUser user = new AppUser();
        user.setEmail(request.email());
        user.setPasswordHash(passwordHash);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setMemberNumber(memberNumber);
        user.setMemberStatus(memberRoleSelected ? request.memberStatus() : null);
        user.setRegistrationDate(memberRoleSelected ? request.registrationDate() : null);
        user.setMemberExpirationDate(memberRoleSelected ? request.memberExpirationDate() : null);
        user.setBlockedReason(memberRoleSelected ? request.blockedReason() : null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserRepository.save(user);

        AppUser adminReference = appUserRepository.getReferenceById(adminUserId);
        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setId(new UserRoleId(user.getId(), role.getId()));
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setAssignedAt(now);
            userRole.setAssignedBy(adminReference);
            userRoleRepository.save(userRole);
        }

        return new CreateUserResponse(UserResponse.from(user), initialPassword);
    }

    /**
     * Modification administrative (DEV-05.6). Ne touche jamais {@code
     * email}, {@code accountStatus}, {@code memberStatus} ni {@code
     * memberNumber} — voir {@link UpdateUserRequest}. Sémantique PATCH
     * sparse à trois états (absent / présent+valeur / présent+{@code null})
     * pour {@code firstName}/{@code lastName}/{@code phoneNumber}/{@code
     * registrationDate}/{@code memberExpirationDate}/{@code blockedReason} :
     * absent laisse la valeur actuelle inchangée, présent remplace (y
     * compris par {@code null} lorsque le domaine l'autorise). {@code
     * roles}, lorsqu'il est fourni (non {@code null}), remplace
     * intégralement l'ensemble des rôles (jamais un delta implicite côté
     * client) ; absent ou {@code null} laisse les rôles inchangés.
     */
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, Long adminUserId) {
        AppUser user = getRequiredUser(id);

        if (request.isFirstNamePresent()) {
            if (request.getFirstName() == null || request.getFirstName().isBlank()) {
                throw new BusinessRuleException(
                        "FIRST_NAME_MUST_NOT_BE_BLANK", "firstName ne peut pas être vide.");
            }
            user.setFirstName(request.getFirstName());
        }
        if (request.isLastNamePresent()) {
            if (request.getLastName() == null || request.getLastName().isBlank()) {
                throw new BusinessRuleException(
                        "LAST_NAME_MUST_NOT_BE_BLANK", "lastName ne peut pas être vide.");
            }
            user.setLastName(request.getLastName());
        }
        if (request.isPhoneNumberPresent()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        applyMembershipUpdate(user, request);

        if (request.getRoles() != null) {
            updateRoles(user, request.getRoles(), adminUserId);
        }

        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }

    /**
     * Change {@code AccountStatus} ({@code PATCH /api/v1/users/{id}/account-status},
     * contrat DEV-05.7) : {@code ACTIVE ⇄ DISABLED}. N'affecte jamais
     * {@code MemberStatus} (dimensions strictement distinctes, DO NOT
     * CONTRADICT). Un JWT déjà émis avant la désactivation reste valide
     * jusqu'à son expiration naturelle : aucune révocation/blacklist JWT
     * n'existe dans PRIMATIS (backend.md « JWT », FIGÉ) — limitation
     * architecturale connue, pas un défaut de cette méthode.
     *
     * <b>Idempotent</b> (contrat DEV-05.7 §3) : appliquer le statut déjà
     * courant est un succès sans effet de bord (200, aucune mutation, aucun
     * {@code updatedAt} rafraîchi) — distinct de {@link #blockMember}/
     * {@link #unblockMember}/{@link #reactivateMembership}, qui restent des
     * transitions strictes.
     */
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse updateAccountStatus(Long id, UpdateAccountStatusRequest request) {
        AppUser user = getRequiredUser(id);
        if (user.getAccountStatus() != request.status()) {
            user.setAccountStatus(request.status());
            user.setUpdatedAt(clock.instant());
        }
        return UserResponse.from(user);
    }

    /**
     * Bloque un adhérent existant ({@code MemberStatus → BLOCKED}), depuis
     * {@code ACTIVE} ou {@code EXPIRED} (un membre déjà expiré peut aussi
     * être bloqué). Synchronise d'abord l'expiration ({@link
     * MemberExpirationPolicy}) pour évaluer correctement la transition même
     * si le statut stocké était encore {@code ACTIVE} de façon obsolète.
     *
     * <b>Idempotent sur le statut, pas sur le motif</b> (contrat DEV-05.7
     * §6) : si l'adhérent est déjà {@code BLOCKED}, cet appel ne lève pas
     * d'erreur — il remplace simplement {@code blockedReason} par la
     * nouvelle valeur fournie (200). Ne modifie jamais {@code AccountStatus},
     * {@code UserRole} ni {@code memberNumber}.
     */
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse blockMember(Long id, BlockMembershipRequest request) {
        AppUser user = getRequiredUser(id);
        requireExistingMembership(user);
        memberExpirationPolicy.syncIfNeeded(user);
        user.setMemberStatus(MemberStatus.BLOCKED);
        user.setBlockedReason(request.blockedReason());
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }

    /**
     * Débloque un adhérent ({@code BLOCKED → ACTIVE} ou {@code EXPIRED}
     * selon l'expiration effective — §8 DEV-05.7). Le blocage étant levé,
     * {@code blockedReason} est systématiquement effacé (invariant {@code
     * blockedReason != null ⇒ memberStatus == BLOCKED}, déjà FIGÉ) ; le
     * statut résultant dépend de {@code memberExpirationDate} : la levée du
     * blocage ne rend jamais actif un adhérent dont l'adhésion a par
     * ailleurs expiré. Toujours autorisé (jamais refusé pour cause
     * d'expiration) — priorité inverse de {@link #reactivateMembership}.
     */
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse unblockMember(Long id) {
        AppUser user = getRequiredUser(id);
        requireExistingMembership(user);
        if (user.getMemberStatus() != MemberStatus.BLOCKED) {
            throw new BusinessRuleException("MEMBER_NOT_BLOCKED", "Cet adhérent n'est pas bloqué.");
        }
        user.setBlockedReason(null);
        user.setMemberStatus(
                memberExpirationPolicy.isExpired(user) ? MemberStatus.EXPIRED : MemberStatus.ACTIVE);
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }

    /**
     * Réactive un adhérent {@code EXPIRED} ({@code EXPIRED → ACTIVE}),
     * uniquement si {@code memberExpirationDate} n'est plus dans le passé
     * (ou {@code null}). Ne renouvelle jamais {@code memberExpirationDate}
     * elle-même : un renouvellement complet (calcul automatique d'une
     * nouvelle échéance) reste hors scope V1 (DEV-05.1 §10, toujours OPEN).
     * Pour réactiver un membre réellement expiré, l'admin doit d'abord
     * prolonger {@code memberExpirationDate} via {@code PATCH
     * /api/v1/users/{id}} (DEV-05.6, qui ne touche jamais {@code
     * memberStatus}), puis appeler cette méthode — deux étapes explicites
     * et auditées séparément plutôt qu'un renouvellement automatique
     * implicite.
     */
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse reactivateMembership(Long id) {
        AppUser user = getRequiredUser(id);
        requireExistingMembership(user);
        if (user.getMemberStatus() != MemberStatus.EXPIRED) {
            throw new BusinessRuleException("MEMBER_NOT_EXPIRED", "Cet adhérent n'est pas expiré.");
        }
        if (memberExpirationPolicy.isExpired(user)) {
            throw new BusinessRuleException("CANNOT_REACTIVATE_EXPIRED_MEMBERSHIP",
                    "memberExpirationDate est toujours dans le passé : prolonger la date avant réactivation.");
        }
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }

    private AppUser getRequiredUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + id + "."));
    }

    private void requireExistingMembership(AppUser user) {
        if (user.getMemberNumber() == null) {
            throw new BusinessRuleException("NOT_A_MEMBER", "Cet utilisateur n'a jamais été adhérent.");
        }
    }

    /**
     * {@code memberNumber} et {@code memberStatus} restent en lecture
     * seule ici (immuable / réservé à DEV-05.7). Un utilisateur qui n'a
     * jamais été adhérent ({@code memberNumber == null}) ne peut pas se
     * voir attribuer de données Membership par cet endpoint : le devenir
     * (génération du numéro, initialisation de {@code memberStatus})
     * appartient à une décision métier distincte, non traitée ici — voir
     * {@link #updateRoles} pour le refus symétrique côté rôles.
     *
     * {@code registrationDate} n'a jamais de sémantique de {@code null}
     * explicite pour un adhérent existant (l'invariant {@code memberNumber
     * défini ⇒ registrationDate définie} l'interdit) : présent+{@code null}
     * est donc refusé, contrairement à {@code memberExpirationDate}/
     * {@code blockedReason} qui peuvent être explicitement effacés.
     */
    private void applyMembershipUpdate(AppUser user, UpdateUserRequest request) {
        boolean hasMembership = user.getMemberNumber() != null;
        boolean membershipFieldsProvided = request.isRegistrationDatePresent()
                || request.isMemberExpirationDatePresent()
                || request.isBlockedReasonPresent();

        if (!hasMembership) {
            if (membershipFieldsProvided) {
                throw new BusinessRuleException("MEMBERSHIP_DATA_REQUIRES_EXISTING_MEMBERSHIP",
                        "Les données d'adhésion ne peuvent être modifiées que pour un utilisateur "
                                + "ayant déjà été adhérent.");
            }
            return;
        }

        if (request.isRegistrationDatePresent() && request.getRegistrationDate() == null) {
            throw new BusinessRuleException("REGISTRATION_DATE_REQUIRED",
                    "registrationDate ne peut pas être effacée pour un adhérent existant.");
        }

        LocalDate effectiveRegistrationDate = request.isRegistrationDatePresent()
                ? request.getRegistrationDate() : user.getRegistrationDate();
        LocalDate effectiveExpirationDate = request.isMemberExpirationDatePresent()
                ? request.getMemberExpirationDate() : user.getMemberExpirationDate();
        String effectiveBlockedReason = request.isBlockedReasonPresent()
                ? request.getBlockedReason() : user.getBlockedReason();

        if (effectiveBlockedReason != null && user.getMemberStatus() != MemberStatus.BLOCKED) {
            throw new BusinessRuleException("BLOCKED_REASON_REQUIRES_BLOCKED_STATUS",
                    "blockedReason ne peut être renseigné que si memberStatus vaut BLOCKED.");
        }
        if (effectiveExpirationDate != null && effectiveExpirationDate.isBefore(effectiveRegistrationDate)) {
            throw new BusinessRuleException("MEMBER_EXPIRATION_BEFORE_REGISTRATION",
                    "memberExpirationDate ne peut pas être antérieure à registrationDate.");
        }

        user.setRegistrationDate(effectiveRegistrationDate);
        user.setMemberExpirationDate(effectiveExpirationDate);
        user.setBlockedReason(effectiveBlockedReason);
    }

    /**
     * Remplace intégralement l'ensemble des rôles par {@code
     * requestedRoleCodes} : supprime les {@code UserRole} absents du
     * nouvel ensemble, ajoute ceux qui manquent ({@code assignedBy} =
     * l'admin courant), laisse strictement intactes les associations déjà
     * présentes dans les deux ensembles ({@code assignedAt}/{@code
     * assignedBy} d'origine préservés).
     */
    private void updateRoles(AppUser user, Set<String> requestedRoleCodes, Long adminUserId) {
        if (requestedRoleCodes.isEmpty()) {
            throw new BusinessRuleException("ROLES_MUST_NOT_BE_EMPTY",
                    "roles ne peut pas être vide : au moins un rôle est requis.");
        }
        if (requestedRoleCodes.contains(ROLE_MEMBER_CODE) && user.getMemberNumber() == null) {
            throw new BusinessRuleException("NEW_MEMBERSHIP_NOT_SUPPORTED_VIA_UPDATE",
                    "L'attribution de ROLE_MEMBER à un utilisateur n'ayant jamais été adhérent "
                            + "n'est pas prise en charge par cet endpoint.");
        }

        Set<Role> targetRoles = new LinkedHashSet<>();
        for (String code : requestedRoleCodes) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException(
                            "UNKNOWN_ROLE_CODE", "Le rôle demandé n'existe pas : " + code + "."));
            targetRoles.add(role);
        }

        List<UserRole> currentUserRoles = userRoleRepository.findByIdUserId(user.getId());
        Set<Long> currentRoleIds = currentUserRoles.stream()
                .map(userRole -> userRole.getRole().getId()).collect(Collectors.toSet());
        Set<Long> targetRoleIds = targetRoles.stream().map(Role::getId).collect(Collectors.toSet());

        for (UserRole currentUserRole : currentUserRoles) {
            if (!targetRoleIds.contains(currentUserRole.getRole().getId())) {
                userRoleRepository.delete(currentUserRole);
            }
        }

        Instant now = clock.instant();
        AppUser adminReference = appUserRepository.getReferenceById(adminUserId);
        for (Role role : targetRoles) {
            if (!currentRoleIds.contains(role.getId())) {
                UserRole newUserRole = new UserRole();
                newUserRole.setId(new UserRoleId(user.getId(), role.getId()));
                newUserRole.setUser(user);
                newUserRole.setRole(role);
                newUserRole.setAssignedAt(now);
                newUserRole.setAssignedBy(adminReference);
                userRoleRepository.save(newUserRole);
            }
        }
    }

    /**
     * Invariants Membership du workflow de création (§10/§17 DEV-05.5) :
     * {@code ROLE_MEMBER} sélectionné ⇒ {@code memberStatus}/{@code
     * registrationDate} obligatoires ; sinon, aucune donnée Membership
     * n'est acceptée. {@code blockedReason} n'a de sens qu'avec {@code
     * BLOCKED}. Ne traite ni l'expiration automatique ni les transitions de
     * statut (DEV-05.7).
     */
    private void validateMembershipCoherence(boolean memberRoleSelected, CreateUserRequest request) {
        boolean hasMembershipData = request.memberStatus() != null
                || request.registrationDate() != null
                || request.memberExpirationDate() != null
                || request.blockedReason() != null;

        if (memberRoleSelected) {
            if (request.memberStatus() == null) {
                throw new BusinessRuleException("MEMBER_STATUS_REQUIRED",
                        "memberStatus est obligatoire lorsque ROLE_MEMBER est sélectionné.");
            }
            if (request.registrationDate() == null) {
                throw new BusinessRuleException("REGISTRATION_DATE_REQUIRED",
                        "registrationDate est obligatoire lorsque ROLE_MEMBER est sélectionné.");
            }
        } else if (hasMembershipData) {
            throw new BusinessRuleException("MEMBERSHIP_DATA_REQUIRES_MEMBER_ROLE",
                    "Les données d'adhésion ne peuvent être fournies que si ROLE_MEMBER est sélectionné.");
        }

        if (request.blockedReason() != null && request.memberStatus() != MemberStatus.BLOCKED) {
            throw new BusinessRuleException("BLOCKED_REASON_REQUIRES_BLOCKED_STATUS",
                    "blockedReason ne peut être renseigné que si memberStatus vaut BLOCKED.");
        }

        if (request.memberExpirationDate() != null && request.registrationDate() != null
                && request.memberExpirationDate().isBefore(request.registrationDate())) {
            throw new BusinessRuleException("MEMBER_EXPIRATION_BEFORE_REGISTRATION",
                    "memberExpirationDate ne peut pas être antérieure à registrationDate.");
        }
    }
}
