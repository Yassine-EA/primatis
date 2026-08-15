package be.primatis.user;

import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.MeProfileResponse;
import be.primatis.user.web.UpdateMeProfileRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie {@link MeProfileService} (DEV-05.9) contre PostgreSQL réel :
 * lecture du profil personnel, synchronisation paresseuse de l'expiration
 * (même règle que {@code UserService}, {@link MemberExpirationPolicy} non
 * dupliquée), sparse PATCH {@code phoneNumber} (no-op strict, DEC-06/DEC-13
 * §2 de l'autorisation), impossibilité de modifier les autres champs via ce
 * DTO. Ne retable pas les cas structurellement garantis par le type de
 * {@link MeProfileResponse}/{@link UpdateMeProfileRequest} (absence de
 * roles/permissions/Residence/timestamps, impossibilité de soumettre
 * firstName/lastName/email/accountStatus/membership) — couverts ici
 * uniquement là où un comportement réel doit être prouvé (aucun champ
 * annexe modifié par effet de bord).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeProfileServiceTests {

    @Autowired
    private MeProfileService meProfileService;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // GET
    // ---------------------------------------------------------------

    @Test
    void getOwnProfileReturnsExactContract() {
        AppUser user = persistUser("meprofile-get-exact@primatis.test");
        user.setPhoneNumber("+32470123456");
        user.setMemberNumber("M000000001");
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.of(2026, 1, 1));
        user.setMemberExpirationDate(LocalDate.now(java.time.ZoneOffset.UTC).plusYears(1));
        entityManager.flush();

        MeProfileResponse response = meProfileService.getOwnProfile(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("meprofile-get-exact@primatis.test");
        assertThat(response.firstName()).isEqualTo("Prénom");
        assertThat(response.lastName()).isEqualTo("Nom");
        assertThat(response.phoneNumber()).isEqualTo("+32470123456");
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.memberNumber()).isEqualTo("M000000001");
        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.registrationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.memberExpirationDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC).plusYears(1));
        assertThat(response.blockedReason()).isNull();
    }

    @Test
    void getOwnProfileForUnknownUserThrowsUserNotFound() {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> meProfileService.getOwnProfile(999_999_999L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void expiredActiveMembershipBecomesExpiredOnGet() {
        AppUser user = persistUser("meprofile-get-expired@primatis.test");
        user.setMemberNumber("M000000002");
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.now(java.time.ZoneOffset.UTC).minusYears(1));
        user.setMemberExpirationDate(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));
        entityManager.flush();

        MeProfileResponse response = meProfileService.getOwnProfile(user.getId());

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.EXPIRED);
    }

    @Test
    void membershipExpiringTodayRemainsActiveOnGet() {
        AppUser user = persistUser("meprofile-get-boundary@primatis.test");
        user.setMemberNumber("M000000003");
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.now(java.time.ZoneOffset.UTC).minusYears(1));
        user.setMemberExpirationDate(LocalDate.now(java.time.ZoneOffset.UTC));
        entityManager.flush();

        MeProfileResponse response = meProfileService.getOwnProfile(user.getId());

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void blockedMembershipNeverSyncedToExpiredOnGet() {
        AppUser user = persistUser("meprofile-get-blocked@primatis.test");
        user.setMemberNumber("M000000004");
        user.setMemberStatus(MemberStatus.BLOCKED);
        user.setRegistrationDate(LocalDate.now(java.time.ZoneOffset.UTC).minusYears(1));
        user.setMemberExpirationDate(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));
        user.setBlockedReason("Retard de paiement");
        entityManager.flush();

        MeProfileResponse response = meProfileService.getOwnProfile(user.getId());

        assertThat(response.memberStatus()).isEqualTo(MemberStatus.BLOCKED);
        assertThat(response.blockedReason()).isEqualTo("Retard de paiement");
    }

    // ---------------------------------------------------------------
    // PATCH — no-op strict (DEC-06/DEC-13, §2 de l'autorisation)
    // ---------------------------------------------------------------

    @Test
    void nullPhoneNumberWithExistingValueIsNoOp() {
        AppUser user = persistUser("meprofile-patch-null-noop@primatis.test");
        user.setPhoneNumber("+32470123456");
        entityManager.flush();
        Instant originalUpdatedAt = user.getUpdatedAt();

        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest(null));

        assertThat(response.phoneNumber()).isEqualTo("+32470123456");
        assertThat(user.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void emptyPhoneNumberIsNoOp() {
        AppUser user = persistUser("meprofile-patch-empty-noop@primatis.test");
        user.setPhoneNumber("+32470123456");
        entityManager.flush();
        Instant originalUpdatedAt = user.getUpdatedAt();

        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest(""));

        assertThat(response.phoneNumber()).isEqualTo("+32470123456");
        assertThat(user.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void whitespacePhoneNumberIsNoOp() {
        AppUser user = persistUser("meprofile-patch-blank-noop@primatis.test");
        user.setPhoneNumber("+32470123456");
        entityManager.flush();
        Instant originalUpdatedAt = user.getUpdatedAt();

        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest("   "));

        assertThat(response.phoneNumber()).isEqualTo("+32470123456");
        assertThat(user.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void normalizedEquivalentOfStoredValueIsNoOp() {
        AppUser user = persistUser("meprofile-patch-equivalent-noop@primatis.test");
        user.setPhoneNumber("+32470123456");
        entityManager.flush();
        Instant originalUpdatedAt = user.getUpdatedAt();

        // Même numéro belge, saisi au format national plutôt que E.164 déjà stocké.
        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest("0470 12 34 56"));

        assertThat(response.phoneNumber()).isEqualTo("+32470123456");
        assertThat(user.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void nullPhoneNumberWithNoExistingValueRemainsNull() {
        AppUser user = persistUser("meprofile-patch-first-null@primatis.test");
        entityManager.flush();

        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest(null));

        assertThat(response.phoneNumber()).isNull();
    }

    // ---------------------------------------------------------------
    // PATCH — modification réelle
    // ---------------------------------------------------------------

    @Test
    void firstValidBelgianNumberIsSetAndNormalizedAndUpdatesTimestamp() {
        AppUser user = persistUser("meprofile-patch-first-belgian@primatis.test");
        entityManager.flush();
        Instant originalUpdatedAt = user.getUpdatedAt();

        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest("0470 12 34 56"));

        assertThat(response.phoneNumber()).isEqualTo("+32470123456");
        assertThat(user.getUpdatedAt()).isNotEqualTo(originalUpdatedAt);
    }

    @Test
    void validInternationalNumberIsAcceptedAndNormalized() {
        AppUser user = persistUser("meprofile-patch-international@primatis.test");
        entityManager.flush();

        MeProfileResponse response = meProfileService.updateOwnProfile(
                user.getId(), new UpdateMeProfileRequest("+33 6 12 34 56 78"));

        assertThat(response.phoneNumber()).isEqualTo("+33612345678");
    }

    @Test
    void normalizedPhoneNumberRoundTripsThroughDatabaseWithinColumnLength() {
        AppUser user = persistUser("meprofile-patch-roundtrip@primatis.test");
        entityManager.flush();

        meProfileService.updateOwnProfile(user.getId(), new UpdateMeProfileRequest("+49 151 23456789"));
        entityManager.flush();
        entityManager.clear();

        AppUser reloaded = entityManager.find(AppUser.class, user.getId());
        assertThat(reloaded.getPhoneNumber()).isEqualTo("+4915123456789");
        assertThat(reloaded.getPhoneNumber()).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void updateOwnProfileForUnknownUserThrowsUserNotFound() {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> meProfileService.updateOwnProfile(
                        999_999_999L, new UpdateMeProfileRequest("+32470123456")))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void patchNeverModifiesFieldsOutsidePhoneNumber() {
        AppUser user = persistUser("meprofile-patch-no-side-effects@primatis.test");
        user.setMemberNumber("M000000005");
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.now(java.time.ZoneOffset.UTC).minusMonths(1));
        user.setMemberExpirationDate(LocalDate.now(java.time.ZoneOffset.UTC).plusYears(1));
        entityManager.flush();

        meProfileService.updateOwnProfile(user.getId(), new UpdateMeProfileRequest("+32470123456"));

        assertThat(user.getFirstName()).isEqualTo("Prénom");
        assertThat(user.getLastName()).isEqualTo("Nom");
        assertThat(user.getEmail()).isEqualTo("meprofile-patch-no-side-effects@primatis.test");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getMemberNumber()).isEqualTo("M000000005");
        assertThat(user.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

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
