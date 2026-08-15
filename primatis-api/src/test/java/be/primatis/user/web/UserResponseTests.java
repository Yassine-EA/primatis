package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link UserResponse#from(AppUser)} : transfert des champs
 * exposables, tolérance aux champs Membership absents (un {@code AppUser}
 * n'est pas nécessairement adhérent), et absence structurelle des données
 * sensibles/internes d'authentification (DEV-05.3).
 */
class UserResponseTests {

    @Test
    void mapsAllExposedFieldsFromCompleteAppUser() {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-02-01T10:00:00Z");
        AppUser user = new AppUser();
        user.setEmail("member@primatis.test");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setPhoneNumber("+32 470 00 00 00");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMemberNumber("M-000123");
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.of(2026, 1, 1));
        user.setMemberExpirationDate(LocalDate.of(2027, 1, 1));
        user.setBlockedReason(null);
        user.setFailedLoginCount(0);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(updatedAt);

        UserResponse response = UserResponse.from(user);

        assertThat(response.email()).isEqualTo("member@primatis.test");
        assertThat(response.firstName()).isEqualTo("Prénom");
        assertThat(response.lastName()).isEqualTo("Nom");
        assertThat(response.phoneNumber()).isEqualTo("+32 470 00 00 00");
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.memberNumber()).isEqualTo("M-000123");
        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.registrationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.memberExpirationDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(response.blockedReason()).isNull();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void mapsNonMemberAppUserWithNullMembershipFieldsWithoutFabricatingDefaults() {
        AppUser user = new AppUser();
        user.setEmail("non-member@primatis.test");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMemberNumber(null);
        user.setMemberStatus(null);
        user.setRegistrationDate(null);
        user.setMemberExpirationDate(null);
        user.setBlockedReason(null);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        UserResponse response = UserResponse.from(user);

        assertThat(response.memberNumber()).isNull();
        assertThat(response.memberStatus()).isNull();
        assertThat(response.registrationDate()).isNull();
        assertThat(response.memberExpirationDate()).isNull();
        assertThat(response.blockedReason()).isNull();
    }

    @Test
    void mapsBlockedMemberWithBlockedReasonTransferred() {
        AppUser user = new AppUser();
        user.setEmail("blocked@primatis.test");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMemberNumber("M-000456");
        user.setMemberStatus(MemberStatus.BLOCKED);
        user.setRegistrationDate(LocalDate.of(2025, 6, 1));
        user.setBlockedReason("Retard de restitution répété");
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        UserResponse response = UserResponse.from(user);

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.BLOCKED);
        assertThat(response.blockedReason()).isEqualTo("Retard de restitution répété");
    }

    @Test
    void neverExposesSensitiveOrInternalAuthenticationFields() {
        String[] forbiddenNameFragments = {"password", "failedlogincount", "lockeduntil"};

        String[] recordComponentNamesLowercase = Arrays.stream(UserResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toArray(String[]::new);

        for (String forbidden : forbiddenNameFragments) {
            assertThat(recordComponentNamesLowercase)
                    .as("UserResponse ne doit posséder aucun composant lié à '%s'", forbidden)
                    .noneMatch(name -> name.contains(forbidden));
        }
    }
}
