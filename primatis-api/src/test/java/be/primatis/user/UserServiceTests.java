package be.primatis.user;

import be.primatis.access.UserRole;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.CreateUserRequest;
import be.primatis.user.web.CreateUserResponse;
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
