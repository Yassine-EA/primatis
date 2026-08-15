package be.primatis.user;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.access.UserRoleRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.CreateUserRequest;
import be.primatis.user.web.CreateUserResponse;
import be.primatis.user.web.UpdateUserRequest;
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
 * Lectures {@code USER_READ}, création administrative et modification
 * {@code USER_MANAGE} sur {@code AppUser} (DEV-05.4/DEV-05.5/DEV-05.6).
 * Orchestration Repository + mapping uniquement, aucune décision métier
 * déplacée hors de ce Service : ne calcule aucune expiration automatique,
 * n'implémente aucune transition {@code AccountStatus}/{@code
 * MemberStatus} (DEV-05.7), ne modifie jamais {@code email}/{@code
 * memberNumber} (immuables via ce Service).
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
    private final Clock clock;

    public UserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            MemberNumberGenerator memberNumberGenerator,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberNumberGenerator = memberNumberGenerator;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return appUserRepository.findAll(pageable).map(UserResponse::from);
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + id + "."));
        return UserResponse.from(user);
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
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + id + "."));

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
