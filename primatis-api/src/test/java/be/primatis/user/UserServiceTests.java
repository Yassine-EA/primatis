package be.primatis.user;

import be.primatis.exception.ResourceNotFoundException;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
