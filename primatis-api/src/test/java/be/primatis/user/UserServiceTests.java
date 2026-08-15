package be.primatis.user;

import be.primatis.access.UserRole;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.InvalidCredentialsException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.security.AuthService;
import be.primatis.user.web.BlockMembershipRequest;
import be.primatis.user.web.CreateUserRequest;
import be.primatis.user.web.CreateUserResponse;
import be.primatis.user.web.UpdateAccountStatusRequest;
import be.primatis.user.web.UpdateUserRequest;
import be.primatis.user.web.UserResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie {@link UserService} (DEV-05.4) contre PostgreSQL réel :
 * pagination/mapping de {@code listUsers}, not-found de {@code getUserById},
 * et application réelle de {@code @PreAuthorize("hasAuthority('USER_READ')")}
 * via le proxy Spring (même principe que {@code RbacMethodSecurityTests}).
 * Ne reteste pas le détail du mapping {@code AppUser} → {@code UserResponse}
 * (déjà couvert par {@code UserResponseTests}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTests {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithUserRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("USER_READ"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithUserManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("USER_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateAsAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
    }

    @Test
    void listUsersReturnsPaginatedAndMappedResults() {
        authenticateWithUserRead();
        AppUser first = persistUser("service-list-1@primatis.test");
        AppUser second = persistUser("service-list-2@primatis.test");
        persistUser("service-list-3@primatis.test");
        entityManager.flush();

        Page<UserResponse> page = userService.listUsers(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).id()).isEqualTo(first.getId());
        assertThat(page.getContent().get(1).id()).isEqualTo(second.getId());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void getUserByIdReturnsUserResponseWhenPresent() {
        authenticateWithUserRead();
        AppUser user = persistUser("service-detail-present@primatis.test");
        entityManager.flush();

        UserResponse response = userService.getUserById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("service-detail-present@primatis.test");
    }

    @Test
    void getUserByIdThrowsResourceNotFoundWhenAbsent() {
        authenticateWithUserRead();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> userService.getUserById(-1L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void listUsersDeniedWithoutUserReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> userService.listUsers(PageRequest.of(0, 20)));
    }

    @Test
    void getUserByIdDeniedWithoutUserReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class, () -> userService.getUserById(1L));
    }

    // ---------------------------------------------------------------
    // createUser — DEV-05.5
    // ---------------------------------------------------------------

    @Test
    void createUserWithNonMemberRoleCreatesActiveUserWithoutMembership() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-1@primatis.test").getId();
        entityManager.flush();

        CreateUserResponse response = userService.createUser(
                new CreateUserRequest(
                        "service-create-librarian@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                adminId);

        assertThat(response.user().accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.user().memberNumber()).isNull();
        assertThat(response.user().memberStatus()).isNull();
        assertThat(response.user().registrationDate()).isNull();
        assertThat(response.initialPassword()).isNotBlank();

        AppUser persisted = entityManager.find(AppUser.class, response.user().id());
        assertThat(persisted.getPasswordHash()).isNotEqualTo(response.initialPassword());
        assertThat(passwordEncoder.matches(response.initialPassword(), persisted.getPasswordHash())).isTrue();

        List<UserRole> userRoles = findUserRoles(persisted.getId());
        assertThat(userRoles).hasSize(1);
        assertThat(userRoles.get(0).getRole().getCode()).isEqualTo("ROLE_LIBRARIAN");
        assertThat(userRoles.get(0).getAssignedBy().getId()).isEqualTo(adminId);
    }

    @Test
    void createUserWithMemberRoleGeneratesMemberNumberAndPersistsMembership() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-2@primatis.test").getId();
        entityManager.flush();

        CreateUserResponse response = userService.createUser(
                new CreateUserRequest(
                        "service-create-member@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_MEMBER"), MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, null),
                adminId);

        assertThat(response.user().memberNumber()).matches("^M[0-9]{9}$");
        assertThat(response.user().memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.user().registrationDate()).isEqualTo(LocalDate.of(2026, 1, 1));

        List<UserRole> userRoles = findUserRoles(response.user().id());
        assertThat(userRoles).extracting(ur -> ur.getRole().getCode()).containsExactly("ROLE_MEMBER");
    }

    @Test
    void createUserWithMultipleRolesCreatesOneUserRolePerRoleAndSingleMemberNumber() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-3@primatis.test").getId();
        entityManager.flush();

        CreateUserResponse response = userService.createUser(
                new CreateUserRequest(
                        "service-create-multi@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_MEMBER", "ROLE_LIBRARIAN"), MemberStatus.ACTIVE,
                        LocalDate.of(2026, 1, 1), null, null),
                adminId);

        assertThat(response.user().memberNumber()).matches("^M[0-9]{9}$");
        List<UserRole> userRoles = findUserRoles(response.user().id());
        assertThat(userRoles).hasSize(2);
        assertThat(userRoles).extracting(ur -> ur.getRole().getCode())
                .containsExactlyInAnyOrder("ROLE_MEMBER", "ROLE_LIBRARIAN");
    }

    @Test
    void createUserWithDuplicateRoleCodesCreatesOnlyOneUserRole() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-dup@primatis.test").getId();
        entityManager.flush();

        // Set<String> absorbe déjà le doublon logique avant d'atteindre le Service.
        CreateUserResponse response = userService.createUser(
                new CreateUserRequest(
                        "service-create-dup@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                adminId);

        assertThat(findUserRoles(response.user().id())).hasSize(1);
    }

    @Test
    void createUserMemberRoleWithoutMemberStatusIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-4@primatis.test").getId();
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                "service-invalid-1@primatis.test", "Prénom", "Nom", null,
                                Set.of("ROLE_MEMBER"), null, LocalDate.of(2026, 1, 1), null, null),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBER_STATUS_REQUIRED"));
    }

    @Test
    void createUserMemberRoleWithoutRegistrationDateIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-5@primatis.test").getId();
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                "service-invalid-2@primatis.test", "Prénom", "Nom", null,
                                Set.of("ROLE_MEMBER"), MemberStatus.ACTIVE, null, null, null),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("REGISTRATION_DATE_REQUIRED"));
    }

    @Test
    void createUserMembershipDataWithoutMemberRoleIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-6@primatis.test").getId();
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                "service-invalid-3@primatis.test", "Prénom", "Nom", null,
                                Set.of("ROLE_LIBRARIAN"), MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, null),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBERSHIP_DATA_REQUIRES_MEMBER_ROLE"));
    }

    @Test
    void createUserBlockedReasonWithoutBlockedStatusIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-7@primatis.test").getId();
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                "service-invalid-4@primatis.test", "Prénom", "Nom", null,
                                Set.of("ROLE_MEMBER"), MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1),
                                null, "Motif de blocage"),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("BLOCKED_REASON_REQUIRES_BLOCKED_STATUS"));
    }

    @Test
    void createUserExpirationBeforeRegistrationIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-8@primatis.test").getId();
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                "service-invalid-5@primatis.test", "Prénom", "Nom", null,
                                Set.of("ROLE_MEMBER"), MemberStatus.ACTIVE,
                                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBER_EXPIRATION_BEFORE_REGISTRATION"));
    }

    @Test
    void createUserWithExistingEmailIsRejectedAndDoesNotPersistSecondUser() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-9@primatis.test").getId();
        String existingEmail = persistUser("service-existing@primatis.test").getEmail();
        entityManager.flush();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                existingEmail, "Prénom", "Nom", null,
                                Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_EMAIL_ALREADY_EXISTS"));

        Long countWithEmail = entityManager
                .createQuery("SELECT COUNT(u) FROM AppUser u WHERE u.email = :email", Long.class)
                .setParameter("email", existingEmail)
                .getSingleResult();
        assertThat(countWithEmail).isEqualTo(1L);
    }

    @Test
    void createUserWithUnknownRoleCodeIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-10@primatis.test").getId();
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.createUser(
                        new CreateUserRequest(
                                "service-invalid-6@primatis.test", "Prénom", "Nom", null,
                                Set.of("ROLE_NOT_A_REAL_ROLE"), null, null, null, null),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("UNKNOWN_ROLE_CODE"));
    }

    @Test
    void createUserDeniedWithoutUserManagePermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class, () -> userService.createUser(
                new CreateUserRequest(
                        "service-denied@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                1L));
    }

    // ---------------------------------------------------------------
    // updateUser — DEV-05.6
    // ---------------------------------------------------------------

    @Test
    void updateUserOnlyFirstNamePresentLeavesLastNameAndPhoneUnchanged() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-1@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-simple@primatis.test").id();
        userService.updateUser(userId, requestWith(r -> {
            r.setLastName("Nom");
            r.setPhoneNumber("+32 470 11 11 11");
        }), adminId);

        UserResponse response = userService.updateUser(userId, requestWith(r -> r.setFirstName("Seul Prénom modifié")), adminId);

        assertThat(response.firstName()).isEqualTo("Seul Prénom modifié");
        assertThat(response.lastName()).as("lastName absent = inchangé").isEqualTo("Nom");
        assertThat(response.phoneNumber()).as("phoneNumber absent = inchangé").isEqualTo("+32 470 11 11 11");
    }

    @Test
    void updateUserOnlyRolesPresentLeavesFirstNameAndLastNameUnchanged() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-1b@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = userService.createUser(
                new CreateUserRequest(
                        "service-update-roles-only@primatis.test", "Prénom original", "Nom original", null,
                        Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                adminId);

        UserResponse response = userService.updateUser(
                created.user().id(), requestWith(r -> r.setRoles(Set.of("ROLE_ADMIN"))), adminId);

        assertThat(response.firstName()).as("firstName absent = inchangé").isEqualTo("Prénom original");
        assertThat(response.lastName()).as("lastName absent = inchangé").isEqualTo("Nom original");
        assertThat(findUserRoles(created.user().id()))
                .extracting(ur -> ur.getRole().getCode()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void updateUserPhoneNumberAbsentIsPreserved() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-2@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-phone-absent@primatis.test").id();
        userService.updateUser(userId, requestWith(r -> r.setPhoneNumber("+32 470 22 22 22")), adminId);

        UserResponse response = userService.updateUser(userId, requestWith(r -> r.setLastName("Autre Nom")), adminId);

        assertThat(response.phoneNumber()).isEqualTo("+32 470 22 22 22");
    }

    @Test
    void updateUserPhoneNumberExplicitNullClearsIt() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-3@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-phone-clear@primatis.test").id();
        userService.updateUser(userId, requestWith(r -> r.setPhoneNumber("+32 470 33 33 33")), adminId);

        UserResponse response = userService.updateUser(userId, requestWith(r -> r.setPhoneNumber(null)), adminId);

        assertThat(response.phoneNumber()).isNull();
    }

    @Test
    void updateUserMemberExpirationDateAbsentIsPreserved() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-4@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-expiration-absent@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));
        userService.updateUser(created.user().id(),
                requestWith(r -> r.setMemberExpirationDate(LocalDate.of(2027, 1, 1))), adminId);

        UserResponse response = userService.updateUser(
                created.user().id(), requestWith(r -> r.setLastName("Autre Nom")), adminId);

        assertThat(response.memberExpirationDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void updateUserMemberExpirationDateExplicitNullClearsIt() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-5@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-expiration-clear@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));
        userService.updateUser(created.user().id(),
                requestWith(r -> r.setMemberExpirationDate(LocalDate.of(2027, 1, 1))), adminId);

        UserResponse response = userService.updateUser(
                created.user().id(), requestWith(r -> r.setMemberExpirationDate(null)), adminId);

        assertThat(response.memberExpirationDate()).isNull();
    }

    @Test
    void updateUserBlockedReasonAbsentIsPreserved() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-6@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-blocked-absent@primatis.test", MemberStatus.BLOCKED, LocalDate.of(2026, 1, 1));
        userService.updateUser(created.user().id(), requestWith(r -> r.setBlockedReason("Motif initial")), adminId);

        UserResponse response = userService.updateUser(
                created.user().id(), requestWith(r -> r.setLastName("Autre Nom")), adminId);

        assertThat(response.blockedReason()).isEqualTo("Motif initial");
    }

    @Test
    void updateUserBlockedReasonExplicitNullClearsItWithoutChangingMemberStatus() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-7@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-blocked-clear@primatis.test", MemberStatus.BLOCKED, LocalDate.of(2026, 1, 1));
        userService.updateUser(created.user().id(), requestWith(r -> r.setBlockedReason("Motif initial")), adminId);

        UserResponse response = userService.updateUser(
                created.user().id(), requestWith(r -> r.setBlockedReason(null)), adminId);

        assertThat(response.blockedReason()).isNull();
        assertThat(response.memberStatus())
                .as("effacer blockedReason ne change jamais memberStatus (DEV-05.7)")
                .isEqualTo(MemberStatus.BLOCKED);
    }

    @Test
    void updateUserRegistrationDateAbsentIsPreserved() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-8@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-registration-absent@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        UserResponse response = userService.updateUser(
                created.user().id(), requestWith(r -> r.setLastName("Autre Nom")), adminId);

        assertThat(response.registrationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void updateUserRegistrationDateExplicitNullForExistingMemberIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-9@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-registration-clear@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(
                        created.user().id(), requestWith(r -> r.setRegistrationDate(null)), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("REGISTRATION_DATE_REQUIRED"));
    }

    @Test
    void updateUserFirstNameExplicitNullIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-10@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-firstname-null@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(userId, requestWith(r -> r.setFirstName(null)), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("FIRST_NAME_MUST_NOT_BE_BLANK"));
    }

    @Test
    void updateUserFirstNameBlankIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-11@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-firstname-blank@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(userId, requestWith(r -> r.setFirstName("   ")), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("FIRST_NAME_MUST_NOT_BE_BLANK"));
    }

    @Test
    void updateUserMembershipFieldsOnNonMemberIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-12@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-nonmember@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(
                        userId, requestWith(r -> r.setRegistrationDate(LocalDate.of(2026, 1, 1))), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBERSHIP_DATA_REQUIRES_EXISTING_MEMBERSHIP"));
    }

    @Test
    void updateUserBlockedReasonWithoutBlockedStatusIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-13@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-not-blocked@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(
                        created.user().id(), requestWith(r -> r.setBlockedReason("Motif")), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("BLOCKED_REASON_REQUIRES_BLOCKED_STATUS"));
    }

    @Test
    void updateUserExpirationBeforeRegistrationIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-14@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-update-bad-dates@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 6, 1));

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(
                        created.user().id(),
                        requestWith(r -> r.setMemberExpirationDate(LocalDate.of(2026, 1, 1))),
                        adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBER_EXPIRATION_BEFORE_REGISTRATION"));
    }

    @Test
    void updateUserRolesAddsAndRemovesPreservingUntouchedAssignment() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-15@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = userService.createUser(
                new CreateUserRequest(
                        "service-update-roles@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_LIBRARIAN", "ROLE_ADMIN"), null, null, null, null),
                adminId);
        Long userId = created.user().id();
        UserRole originalLibrarianAssignment = findUserRoles(userId).stream()
                .filter(ur -> ur.getRole().getCode().equals("ROLE_LIBRARIAN"))
                .findFirst().orElseThrow();
        entityManager.flush();

        userService.updateUser(userId, requestWith(r -> r.setRoles(Set.of("ROLE_LIBRARIAN"))), adminId);

        List<UserRole> afterUpdate = findUserRoles(userId);
        assertThat(afterUpdate).extracting(ur -> ur.getRole().getCode()).containsExactly("ROLE_LIBRARIAN");
        UserRole preserved = afterUpdate.get(0);
        assertThat(preserved.getAssignedAt()).isEqualTo(originalLibrarianAssignment.getAssignedAt());
        assertThat(preserved.getAssignedBy().getId()).isEqualTo(originalLibrarianAssignment.getAssignedBy().getId());
    }

    @Test
    void updateUserRolesAbsentLeavesRolesUntouched() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-16@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = userService.createUser(
                new CreateUserRequest(
                        "service-update-roles-untouched@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                adminId);

        userService.updateUser(created.user().id(), requestWith(r -> r.setFirstName("Prénom modifié")), adminId);

        assertThat(findUserRoles(created.user().id()))
                .extracting(ur -> ur.getRole().getCode()).containsExactly("ROLE_LIBRARIAN");
    }

    @Test
    void updateUserRolesEmptyIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-17@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-empty-roles@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(userId, requestWith(r -> r.setRoles(Set.of())), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ROLES_MUST_NOT_BE_EMPTY"));
    }

    @Test
    void updateUserUnknownRoleCodeIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-18@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-unknown-role@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(
                        userId, requestWith(r -> r.setRoles(Set.of("NOT_A_REAL_ROLE"))), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("UNKNOWN_ROLE_CODE"));
    }

    @Test
    void updateUserAddingMemberRoleToNonMemberIsRejectedAndMemberNumberNeverGenerated() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-update-19@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-update-add-member-role@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.updateUser(
                        userId, requestWith(r -> r.setRoles(Set.of("ROLE_MEMBER"))), adminId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("NEW_MEMBERSHIP_NOT_SUPPORTED_VIA_UPDATE"));

        AppUser reloaded = entityManager.find(AppUser.class, userId);
        assertThat(reloaded.getMemberNumber()).isNull();
    }

    @Test
    void updateUserThrowsResourceNotFoundWhenAbsent() {
        authenticateWithUserManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> userService.updateUser(-1L, requestWith(r -> r.setLastName("Nom")), 1L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void updateUserDeniedWithoutUserManagePermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> userService.updateUser(1L, requestWith(r -> r.setLastName("Nom")), 1L));
    }

    // ---------------------------------------------------------------
    // AccountStatus — DEV-05.7
    // ---------------------------------------------------------------

    @Test
    void updateAccountStatusActiveToDisabledTransitions() {
        authenticateWithUserManage();
        AppUser user = persistUser("service-disable-1@primatis.test");
        entityManager.flush();

        UserResponse response = userService.updateAccountStatus(
                user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    void updateAccountStatusDisabledToActiveTransitions() {
        authenticateWithUserManage();
        AppUser user = persistUser("service-enable-1@primatis.test");
        entityManager.flush();
        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));

        UserResponse response = userService.updateAccountStatus(
                user.getId(), new UpdateAccountStatusRequest(AccountStatus.ACTIVE));

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void updateAccountStatusActiveToActiveIsIdempotentWithoutSideEffect() {
        authenticateWithUserManage();
        AppUser user = persistUser("service-idempotent-active@primatis.test");
        entityManager.flush();
        Instant updatedAtBefore = user.getUpdatedAt();

        UserResponse response = userService.updateAccountStatus(
                user.getId(), new UpdateAccountStatusRequest(AccountStatus.ACTIVE));

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        AppUser reloaded = entityManager.find(AppUser.class, user.getId());
        assertThat(reloaded.getUpdatedAt())
                .as("aucun effet de bord : updatedAt inchangé si le statut demandé est déjà le statut courant")
                .isEqualTo(updatedAtBefore);
    }

    @Test
    void updateAccountStatusDisabledToDisabledIsIdempotentWithoutSideEffect() {
        authenticateWithUserManage();
        AppUser user = persistUser("service-idempotent-disabled@primatis.test");
        entityManager.flush();
        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));
        Instant updatedAtAfterFirstDisable = entityManager.find(AppUser.class, user.getId()).getUpdatedAt();

        UserResponse response = userService.updateAccountStatus(
                user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.DISABLED);
        AppUser reloaded = entityManager.find(AppUser.class, user.getId());
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtAfterFirstDisable);
    }

    @Test
    void updateAccountStatusDeniedWithoutUserManagePermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class, () -> userService.updateAccountStatus(
                1L, new UpdateAccountStatusRequest(AccountStatus.DISABLED)));
    }

    @Test
    void updateAccountStatusThrowsResourceNotFoundWhenAbsent() {
        authenticateWithUserManage();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> userService.updateAccountStatus(
                        -1L, new UpdateAccountStatusRequest(AccountStatus.DISABLED)))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Auth impact — DEV-05.7 (réutilise AuthService réel, DEV-03.6)
    // ---------------------------------------------------------------

    @Test
    void disabledAccountCannotLogin() {
        authenticateWithUserManage();
        String email = "service-auth-disable@primatis.test";
        String rawPassword = "Correct-Password-2026!";
        AppUser user = persistUserWithPassword(email, rawPassword);
        entityManager.flush();
        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));
        entityManager.flush();

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(email, rawPassword));
    }

    @Test
    void reenabledAccountCanLoginAgain() {
        authenticateWithUserManage();
        String email = "service-auth-enable@primatis.test";
        String rawPassword = "Correct-Password-2026!";
        AppUser user = persistUserWithPassword(email, rawPassword);
        entityManager.flush();
        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));
        entityManager.flush();
        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.ACTIVE));
        entityManager.flush();

        Authentication authentication = authService.login(email, rawPassword);

        // authService.login(...) retourne une Authentication dont le principal est
        // PrimatisUserPrincipal : getName() == UserDetails.getUsername() == email
        // (distinct du claim JWT "sub" == userId, cf. JwtService.generateAccessToken
        // et RbacMethodSecuritySampleService — deux Authentication différentes).
        assertThat(authentication.getName()).isEqualTo(email);
    }

    // ---------------------------------------------------------------
    // Temporary lock — confirmation de séparation (DEV-03.6, aucun changement)
    // ---------------------------------------------------------------

    @Test
    void accountStatusAndMembershipActionsNeverTouchTemporaryLockFields() {
        authenticateWithUserManage();
        AppUser user = persistUser("service-lock-separation@primatis.test");
        user.setFailedLoginCount(2);
        Instant lockedUntil = Instant.now().plusSeconds(600);
        user.setLockedUntil(lockedUntil);
        entityManager.flush();

        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.DISABLED));
        userService.updateAccountStatus(user.getId(), new UpdateAccountStatusRequest(AccountStatus.ACTIVE));

        AppUser reloaded = entityManager.find(AppUser.class, user.getId());
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(2);
        assertThat(reloaded.getLockedUntil()).isEqualTo(lockedUntil);
    }

    // ---------------------------------------------------------------
    // MemberStatus — Block / Unblock — DEV-05.7
    // ---------------------------------------------------------------

    @Test
    void blockMemberTransitionsActiveToBlockedWithReason() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-block-1@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-block-active@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        UserResponse response = userService.blockMember(
                created.user().id(), new BlockMembershipRequest("Retard répété"));

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.BLOCKED);
        assertThat(response.blockedReason()).isEqualTo("Retard répété");
    }

    @Test
    void blockMemberOnNonMemberIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-block-2@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-block-nonmember@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.blockMember(userId, new BlockMembershipRequest("Motif")))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("NOT_A_MEMBER"));
    }

    /**
     * Contrat DEV-05.7 §6 : {@code BLOCKED → BLOCKED} est idempotent sur le
     * statut mais remplace {@code blockedReason} — pas une erreur.
     */
    @Test
    void blockMemberOnAlreadyBlockedUpdatesReasonAndSucceeds() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-block-3@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-block-twice@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));
        userService.blockMember(created.user().id(), new BlockMembershipRequest("Premier motif"));

        UserResponse response = userService.blockMember(
                created.user().id(), new BlockMembershipRequest("Second motif"));

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.BLOCKED);
        assertThat(response.blockedReason()).isEqualTo("Second motif");
    }

    @Test
    void blockMemberFromExpiredIsAllowed() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-block-4@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-block-expired@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        // synchronise ACTIVE -> EXPIRED avant le blocage (comme un GET détail le ferait) :
        // getUserById exige USER_READ, distinct de USER_MANAGE requis par blockMember.
        authenticateWithUserRead();
        userService.getUserById(created.user().id());
        authenticateWithUserManage();

        UserResponse response = userService.blockMember(
                created.user().id(), new BlockMembershipRequest("Motif sur adhésion expirée"));

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.BLOCKED);
    }

    @Test
    void unblockMemberNotExpiredTransitionsToActive() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-unblock-1@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-unblock-active@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));
        userService.blockMember(created.user().id(), new BlockMembershipRequest("Motif"));

        UserResponse response = userService.unblockMember(created.user().id());

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.blockedReason()).isNull();
    }

    @Test
    void unblockMemberExpiredTransitionsToExpiredNotActive() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-unblock-2@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-unblock-expired@primatis.test", MemberStatus.BLOCKED,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        entityManager.find(AppUser.class, created.user().id()).setBlockedReason("Motif initial");
        entityManager.flush();

        UserResponse response = userService.unblockMember(created.user().id());

        assertThat(response.memberStatus())
                .as("le déblocage ne rend jamais actif un membre dont l'adhésion a par ailleurs expiré")
                .isEqualTo(MemberStatus.EXPIRED);
        assertThat(response.blockedReason()).isNull();
    }

    @Test
    void unblockMemberOnNotBlockedIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-unblock-3@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-unblock-not-blocked@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.unblockMember(created.user().id()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBER_NOT_BLOCKED"));
    }

    @Test
    void unblockMemberOnNonMemberIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-unblock-4@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-unblock-nonmember@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.unblockMember(userId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("NOT_A_MEMBER"));
    }

    @Test
    void blockAndUnblockNeverTouchUserRoles() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-block-roles@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = userService.createUser(
                new CreateUserRequest(
                        "service-block-roles-untouched@primatis.test", "Prénom", "Nom", null,
                        Set.of("ROLE_MEMBER", "ROLE_LIBRARIAN"), MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1),
                        null, null),
                adminId);

        userService.blockMember(created.user().id(), new BlockMembershipRequest("Motif"));
        userService.unblockMember(created.user().id());

        assertThat(findUserRoles(created.user().id()))
                .extracting(ur -> ur.getRole().getCode())
                .containsExactlyInAnyOrder("ROLE_MEMBER", "ROLE_LIBRARIAN");
    }

    @Test
    void blockMemberDeniedWithoutUserManagePermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> userService.blockMember(1L, new BlockMembershipRequest("Motif")));
    }

    @Test
    void unblockMemberDeniedWithoutUserManagePermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class, () -> userService.unblockMember(1L));
    }

    // ---------------------------------------------------------------
    // Expiration — synchronisation paresseuse — DEV-05.7
    // ---------------------------------------------------------------

    @Test
    void getUserByIdSyncsExpiredActiveMemberToExpired() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-expire-1@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-expire-past@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));

        authenticateWithUserRead();
        UserResponse response = userService.getUserById(created.user().id());

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.EXPIRED);
        AppUser reloaded = entityManager.find(AppUser.class, created.user().id());
        assertThat(reloaded.getMemberStatus())
                .as("la synchronisation doit être réellement persistée, pas seulement reflétée dans la réponse")
                .isEqualTo(MemberStatus.EXPIRED);
    }

    @Test
    void getUserByIdOnExpirationDateBoundaryStillActive() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-expire-2@primatis.test").getId();
        entityManager.flush();
        LocalDate today = LocalDate.now();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-expire-boundary@primatis.test", MemberStatus.ACTIVE,
                today.minusYears(1), today);

        authenticateWithUserRead();
        UserResponse response = userService.getUserById(created.user().id());

        assertThat(response.memberStatus())
                .as("memberExpirationDate == today reste le dernier jour valide")
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void getUserByIdNeverSyncsBlockedMemberEvenIfExpirationPassed() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-expire-3@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-expire-blocked@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        userService.blockMember(created.user().id(), new BlockMembershipRequest("Motif"));

        authenticateWithUserRead();
        UserResponse response = userService.getUserById(created.user().id());

        assertThat(response.memberStatus())
                .as("BLOCKED reste prioritaire, aucune synchronisation d'expiration ne l'écrase")
                .isEqualTo(MemberStatus.BLOCKED);
    }

    @Test
    void getUserByIdDoesNotExpireMemberWithoutExpirationDate() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-expire-4@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-expire-null-date@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2020, 1, 1));

        authenticateWithUserRead();
        UserResponse response = userService.getUserById(created.user().id());

        assertThat(response.memberStatus())
                .as("memberExpirationDate absente = jamais expiré automatiquement")
                .isEqualTo(MemberStatus.ACTIVE);
    }

    // ---------------------------------------------------------------
    // Expiration — cohérence liste/détail — DEV-05.7 gate conformité
    // ---------------------------------------------------------------

    /**
     * Contrat DEV-05.7 §9 : {@code listUsers} et {@code getUserById} doivent
     * présenter la même vérité pour un même utilisateur — la synchronisation
     * paresseuse est étendue à {@code listUsers} (mutation des entités déjà
     * chargées par la page, sans requête supplémentaire).
     */
    @Test
    void listUsersSyncsExpiredActiveMemberToExpired() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-list-expire-1@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-list-expire-past@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));

        authenticateWithUserRead();
        Page<UserResponse> page = userService.listUsers(PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "id")));

        UserResponse inList = page.getContent().stream()
                .filter(u -> u.id().equals(created.user().id()))
                .findFirst()
                .orElseThrow();
        assertThat(inList.memberStatus()).isEqualTo(MemberStatus.EXPIRED);
        AppUser reloaded = entityManager.find(AppUser.class, created.user().id());
        assertThat(reloaded.getMemberStatus())
                .as("la synchronisation déclenchée par listUsers doit être réellement persistée")
                .isEqualTo(MemberStatus.EXPIRED);
    }

    @Test
    void listUsersOnExpirationDateBoundaryStillActive() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-list-expire-2@primatis.test").getId();
        entityManager.flush();
        LocalDate today = LocalDate.now();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-list-expire-boundary@primatis.test", MemberStatus.ACTIVE,
                today.minusYears(1), today);

        authenticateWithUserRead();
        Page<UserResponse> page = userService.listUsers(PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "id")));

        UserResponse inList = page.getContent().stream()
                .filter(u -> u.id().equals(created.user().id()))
                .findFirst()
                .orElseThrow();
        assertThat(inList.memberStatus())
                .as("memberExpirationDate == today reste le dernier jour valide")
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void listUsersNeverSyncsBlockedMemberEvenIfExpirationPassed() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-list-expire-3@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-list-expire-blocked@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        userService.blockMember(created.user().id(), new BlockMembershipRequest("Motif"));

        authenticateWithUserRead();
        Page<UserResponse> page = userService.listUsers(PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "id")));

        UserResponse inList = page.getContent().stream()
                .filter(u -> u.id().equals(created.user().id()))
                .findFirst()
                .orElseThrow();
        assertThat(inList.memberStatus())
                .as("BLOCKED reste prioritaire dans la liste, jamais écrasé par la synchronisation d'expiration")
                .isEqualTo(MemberStatus.BLOCKED);
    }

    /**
     * Cœur du gate DEV-05.7 §9 : la liste et le détail doivent raconter la
     * même histoire pour le même utilisateur — pas ACTIVE dans un endpoint
     * et EXPIRED dans l'autre.
     */
    @Test
    void listUsersAndGetUserByIdAgreeOnExpiredStatusForSameUser() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-list-expire-4@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-list-detail-coherence@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));

        authenticateWithUserRead();
        Page<UserResponse> page = userService.listUsers(PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "id")));
        UserResponse inList = page.getContent().stream()
                .filter(u -> u.id().equals(created.user().id()))
                .findFirst()
                .orElseThrow();
        UserResponse detail = userService.getUserById(created.user().id());

        assertThat(inList.memberStatus())
                .as("liste et détail doivent présenter la même vérité pour le même utilisateur")
                .isEqualTo(detail.memberStatus())
                .isEqualTo(MemberStatus.EXPIRED);
    }

    // ---------------------------------------------------------------
    // Reactivate (EXPIRED -> ACTIVE) — DEV-05.7
    // ---------------------------------------------------------------

    @Test
    void reactivateMembershipFromExpiredWithFutureDateSucceeds() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-reactivate-1@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-reactivate-ok@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        // getUserById exige USER_READ, distinct de USER_MANAGE requis par reactivateMembership.
        authenticateWithUserRead();
        userService.getUserById(created.user().id());
        authenticateWithUserManage();
        entityManager.find(AppUser.class, created.user().id())
                .setMemberExpirationDate(LocalDate.now().plusYears(1));
        entityManager.flush();

        UserResponse response = userService.reactivateMembership(created.user().id());

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void reactivateMembershipRefusedIfStillExpired() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-reactivate-2@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMemberWithDates(
                adminId, "service-reactivate-still-expired@primatis.test", MemberStatus.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        // getUserById exige USER_READ, distinct de USER_MANAGE requis par reactivateMembership.
        authenticateWithUserRead();
        userService.getUserById(created.user().id());
        authenticateWithUserManage();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.reactivateMembership(created.user().id()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("CANNOT_REACTIVATE_EXPIRED_MEMBERSHIP"));
    }

    @Test
    void reactivateMembershipOnNotExpiredIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-reactivate-3@primatis.test").getId();
        entityManager.flush();
        CreateUserResponse created = createFixtureMember(
                adminId, "service-reactivate-not-expired@primatis.test", MemberStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.reactivateMembership(created.user().id()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("MEMBER_NOT_EXPIRED"));
    }

    @Test
    void reactivateMembershipOnNonMemberIsRejected() {
        authenticateWithUserManage();
        Long adminId = persistUser("service-admin-reactivate-4@primatis.test").getId();
        entityManager.flush();
        Long userId = createFixtureNonMember(adminId, "service-reactivate-nonmember@primatis.test").id();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> userService.reactivateMembership(userId))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("NOT_A_MEMBER"));
    }

    @Test
    void reactivateMembershipDeniedWithoutUserManagePermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class, () -> userService.reactivateMembership(1L));
    }

    private UpdateUserRequest requestWith(Consumer<UpdateUserRequest> mutator) {
        UpdateUserRequest request = new UpdateUserRequest();
        mutator.accept(request);
        return request;
    }

    private UserResponse createFixtureNonMember(Long adminId, String email) {
        return userService.createUser(
                new CreateUserRequest(email, "Prénom", "Nom", null, Set.of("ROLE_LIBRARIAN"), null, null, null, null),
                adminId).user();
    }

    private CreateUserResponse createFixtureMember(
            Long adminId, String email, MemberStatus memberStatus, LocalDate registrationDate) {
        return userService.createUser(
                new CreateUserRequest(email, "Prénom", "Nom", null, Set.of("ROLE_MEMBER"),
                        memberStatus, registrationDate, null, null),
                adminId);
    }

    private CreateUserResponse createFixtureMemberWithDates(
            Long adminId, String email, MemberStatus memberStatus,
            LocalDate registrationDate, LocalDate memberExpirationDate) {
        return userService.createUser(
                new CreateUserRequest(email, "Prénom", "Nom", null, Set.of("ROLE_MEMBER"),
                        memberStatus, registrationDate, memberExpirationDate, null),
                adminId);
    }

    private AppUser persistUserWithPassword(String email, String rawPassword) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private List<UserRole> findUserRoles(Long userId) {
        return entityManager
                .createQuery("SELECT ur FROM UserRole ur WHERE ur.id.userId = :userId", UserRole.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    private AppUser persistUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }
}
