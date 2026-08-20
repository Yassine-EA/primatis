package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.reservation.dto.CreateReservationRequest;
import be.primatis.reservation.dto.ReservationResponse;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie {@link ReservationService} (DEV-08.4) contre PostgreSQL réel :
 * application réelle de {@code @PreAuthorize("hasAuthority('RESERVATION_READ')")}
 * sur {@code listReservations} via le proxy Spring (même principe que
 * {@code LoanServiceTests}), pagination/tri de {@code listReservations},
 * ownership structurelle et isolation de {@code listOwnReservations}, et
 * confirmation que le fetch {@code @EntityGraph} (DEV-08.4) évite bien le
 * N+1 sur {@code user}/{@code title}/{@code assignedCopy}. Ne reteste pas
 * le détail du mapping {@code Reservation} → {@link ReservationResponse}
 * (déjà couvert par {@code ReservationDtoTests}, DEV-08.3).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationServiceTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithReservationRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("RESERVATION_READ"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithReservationManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("RESERVATION_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateAsAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
    }

    // ---------------------------------------------------------------
    // listReservations — staff, RESERVATION_READ
    // ---------------------------------------------------------------

    @Test
    void listReservationsDeniedWithoutReservationReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> reservationService.listReservations(PageRequest.of(0, 20)));
    }

    @Test
    void listReservationsReturnsPaginatedAndMappedResultsAcrossMembers() {
        authenticateWithReservationRead();
        AppUser memberOne = persistUser("service-staff-list-1@primatis.test");
        AppUser memberTwo = persistUser("service-staff-list-2@primatis.test");
        Reservation older = persistReservation(
                memberOne, persistTitle(), ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation newer = persistReservation(
                memberTwo, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listReservations(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "reservationDate", "id")));

        assertThat(page.getContent()).extracting(ReservationResponse::id)
                .contains(newer.getId(), older.getId());
        int newerIndex = indexOfReservationId(page.getContent(), newer.getId());
        int olderIndex = indexOfReservationId(page.getContent(), older.getId());
        assertThat(newerIndex).isLessThan(olderIndex);
    }

    @Test
    void listReservationsExposesBothMembersReservationsNotJustOne() {
        authenticateWithReservationRead();
        AppUser memberOne = persistUser("service-staff-multi-1@primatis.test");
        AppUser memberTwo = persistUser("service-staff-multi-2@primatis.test");
        Reservation reservationOne = persistReservation(memberOne, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Reservation reservationTwo = persistReservation(memberTwo, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listReservations(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(ReservationResponse::id)
                .contains(reservationOne.getId(), reservationTwo.getId());
    }

    // ---------------------------------------------------------------
    // listOwnReservations — self, ownership structurelle
    // ---------------------------------------------------------------

    /**
     * Aucun {@code @PreAuthorize} sur {@code listOwnReservations} : fonctionne
     * même sans authentification RBAC (anonyme), l'ownership reposant
     * uniquement sur le {@code selfUserId} transmis par le Controller.
     */
    @Test
    void listOwnReservationsWorksWithoutReservationReadPermission() {
        authenticateAsAnonymous();
        AppUser self = persistUser("service-self-no-permission@primatis.test");
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listOwnReservations(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ReservationResponse::id).containsExactly(reservation.getId());
    }

    @Test
    void listOwnReservationsIsolatesReservationsBetweenUsers() {
        AppUser self = persistUser("service-self-isolation-1@primatis.test");
        AppUser other = persistUser("service-self-isolation-2@primatis.test");
        Reservation selfReservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(other, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listOwnReservations(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ReservationResponse::id).containsExactly(selfReservation.getId());
        assertThat(page.getContent()).allSatisfy(
                response -> assertThat(response.member().id()).isEqualTo(self.getId()));
    }

    @Test
    void listOwnReservationsReturnsEmptyPageRatherThanErrorWhenNoReservation() {
        AppUser self = persistUser("service-self-empty@primatis.test");
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listOwnReservations(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ---------------------------------------------------------------
    // createOwnReservation / createReservation — DEV-08.5, succès
    // ---------------------------------------------------------------

    @Test
    void createOwnReservationCreatesWaitingReservationForTheAuthenticatedUser() {
        AppUser self = persistMember("service-create-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(response.member().id()).isEqualTo(self.getId());
        assertThat(response.title().id()).isEqualTo(title.getId());
        assertThat(response.assignedCopy()).isNull();
        assertThat(response.expirationDate()).isNull();
        assertThat(response.fulfilledByLoanId()).isNull();
    }

    @Test
    void createReservationCreatesWaitingReservationForTheDesignatedMember() {
        authenticateWithReservationManage();
        AppUser member = persistMember("service-create-staff@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response = reservationService.createReservation(
                new CreateReservationRequest(member.getId(), title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(response.member().id()).isEqualTo(member.getId());
        assertThat(response.assignedCopy()).isNull();
        assertThat(response.expirationDate()).isNull();
        assertThat(response.fulfilledByLoanId()).isNull();
    }

    @Test
    void createReservationDeniedWithoutReservationManagePermission() {
        authenticateAsAnonymous();
        AppUser member = persistMember("service-create-noperm@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        assertThrows(AccessDeniedException.class, () -> reservationService.createReservation(
                new CreateReservationRequest(member.getId(), title.getId())));
    }

    @Test
    void createOwnReservationWorksWithoutAnyPermission() {
        authenticateAsAnonymous();
        AppUser self = persistMember("service-create-self-noperm@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationSetsReservationDateCreatedAtAndUpdatedAtFromTheInjectedClock() {
        AppUser self = persistMember("service-create-timestamps@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();
        Instant before = clock.instant();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        Instant after = clock.instant();
        assertThat(response.reservationDate()).isBetween(before, after);
        assertThat(response.createdAt()).isEqualTo(response.reservationDate());
        assertThat(response.updatedAt()).isEqualTo(response.reservationDate());
    }

    // ---------------------------------------------------------------
    // createReservation — DEV-08.5, adhésion active (OD-DEV08-01)
    // ---------------------------------------------------------------

    @Test
    void createReservationRejectsNonMember() {
        AppUser nonMember = persistUser("service-create-nonmember@primatis.test");
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("NOT_A_MEMBER", () ->
                reservationService.createOwnReservation(nonMember.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationRejectsBlockedMember() {
        AppUser blocked = persistMember("service-create-blocked@primatis.test", MemberStatus.BLOCKED, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_BLOCKED", () ->
                reservationService.createOwnReservation(blocked.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationRejectsAlreadyExpiredMember() {
        AppUser expired = persistMember("service-create-expired@primatis.test", MemberStatus.EXPIRED, LocalDate.now(clock).minusDays(1));
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_EXPIRED", () ->
                reservationService.createOwnReservation(expired.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    /**
     * {@code memberStatus} vaut encore {@code ACTIVE} en base mais
     * {@code memberExpirationDate} est dans le passé : {@code
     * MemberExpirationPolicy.syncIfNeeded} doit transitionner vers {@code
     * EXPIRED} avant le refus — même précédent exact que
     * {@code LoanService.requireEligibleBorrower}.
     */
    @Test
    void createReservationSynchronizesExpiredMembershipBeforeRejecting() {
        AppUser staleActive = persistMember(
                "service-create-stale-active@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_EXPIRED", () -> reservationService.createOwnReservation(
                staleActive.getId(), new CreateOwnReservationRequest(title.getId())));

        AppUser reloaded = entityManager.find(AppUser.class, staleActive.getId());
        assertThat(reloaded.getMemberStatus()).isEqualTo(MemberStatus.EXPIRED);
    }

    /**
     * Aucune source Reservation n'impose qu'une Fine {@code UNPAID} bloque
     * une création (DEV-08.1 §9/§19) — ne jamais transposer la règle Loan.
     */
    @Test
    void createReservationAllowedDespiteMemberHavingAnUnpaidFine() {
        AppUser self = persistMember("service-create-unpaid-fine@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title loanTitle = persistTitle();
        Copy loanCopy = persistCopy(loanTitle, "SERVICE-CREATE-FINE-LOAN-COPY");
        loanCopy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(self, loanCopy);
        persistFine(loan, FineStatus.UNPAID);
        Title reservationTitle = persistTitle();
        entityManager.flush();

        ReservationResponse response = reservationService.createOwnReservation(
                self.getId(), new CreateOwnReservationRequest(reservationTitle.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    // ---------------------------------------------------------------
    // createReservation — DEV-08.5, Title
    // ---------------------------------------------------------------

    @Test
    void createReservationRejectsUnknownTitle() {
        AppUser self = persistMember("service-create-unknown-title@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        entityManager.flush();

        ResourceNotFoundException exception = (ResourceNotFoundException) assertThrows(
                ResourceNotFoundException.class, () -> reservationService.createOwnReservation(
                        self.getId(), new CreateOwnReservationRequest(-1L)));
        assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND");
    }

    // ---------------------------------------------------------------
    // createReservation — DEV-08.5, disponibilité Copy (OD-DEV08-02)
    // ---------------------------------------------------------------

    @Test
    void createReservationRejectsWhenAnAvailableCopyExistsForTheTitle() {
        AppUser self = persistMember("service-create-copy-available@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-COPY-AVAILABLE-1");
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_COPY_AVAILABLE", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationAllowedWhenNoCopyIsAvailableForTheTitle() {
        AppUser self = persistMember("service-create-copy-onloan@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-COPY-ONLOAN-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationAllowedWhenTitleHasNoCopyAtAll() {
        AppUser self = persistMember("service-create-copy-none@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    // ---------------------------------------------------------------
    // createReservation — DEV-08.5, doublon actif
    // ---------------------------------------------------------------

    @Test
    void createReservationRejectsWhenAWaitingReservationIsAlreadyActiveForTheSameTitle() {
        AppUser self = persistMember("service-create-dup-waiting@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        persistReservation(self, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_ALREADY_ACTIVE", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationRejectsWhenAReadyReservationIsAlreadyActiveForTheSameTitle() {
        AppUser self = persistMember("service-create-dup-ready@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-DUP-READY-1");
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        persistReadyReservation(self, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_ALREADY_ACTIVE", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationAllowedWhenOnlyAFulfilledReservationExistsForTheSameTitle() {
        AppUser self = persistMember("service-create-terminal-fulfilled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-TERMINAL-FULFILLED-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(self, copy);
        persistFulfilledReservation(self, title, copy, loan, Instant.now());
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationAllowedWhenOnlyACancelledReservationExistsForTheSameTitle() {
        AppUser self = persistMember("service-create-terminal-cancelled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        persistReservation(self, title, ReservationStatus.CANCELLED, Instant.now());
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationAllowedWhenOnlyAnExpiredReservationExistsForTheSameTitle() {
        AppUser self = persistMember("service-create-terminal-expired@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        persistReservation(self, title, ReservationStatus.EXPIRED, Instant.now());
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    /**
     * Un Loan actif du même membre sur le même Title n'interdit pas, à lui
     * seul, la Reservation (business-rules.md §4.3 — ne jamais transposer la
     * règle Loan).
     */
    @Test
    void createReservationAllowedWhenMemberHasAnActiveLoanOnTheSameTitle() {
        AppUser self = persistMember("service-create-active-loan@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-ACTIVE-LOAN-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        persistLoan(self, copy);
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    // ---------------------------------------------------------------
    // createReservation — DEV-08.5, limite active (MAX_ACTIVE_RESERVATIONS_PER_MEMBER)
    // ---------------------------------------------------------------

    @Test
    void createReservationAllowedWhenUnderTheActiveLimit() {
        AppUser self = persistMember("service-create-under-limit@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(2);
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationRejectsWhenTheActiveLimitIsReached() {
        AppUser self = persistMember("service-create-limit-reached@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(2);
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_LIMIT_REACHED", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId())));
    }

    /**
     * Confirme que la limite est réellement lue dynamiquement depuis
     * {@code application_setting} et non codée en dur : la valeur bootstrap
     * (10) est modifiée en base pour ce test, jamais supposée fixe.
     */
    @Test
    void createReservationLimitIsReadDynamicallyFromApplicationSetting() {
        AppUser self = persistMember("service-create-limit-dynamic@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(1);
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_LIMIT_REACHED", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId())));
    }

    @Test
    void createReservationLimitDoesNotCountTerminalReservations() {
        AppUser self = persistMember("service-create-limit-terminal@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(1);
        persistReservation(self, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        persistReservation(self, persistTitle(), ReservationStatus.EXPIRED, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    private static void assertBusinessRuleCode(String expectedCode, Executable executable) {
        BusinessRuleException exception = (BusinessRuleException) assertThrows(BusinessRuleException.class, executable);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
    }

    private void updateMaxActiveReservationsSetting(int value) {
        entityManager.createQuery("UPDATE ApplicationSetting s SET s.settingValue = :value WHERE s.settingKey = :key")
                .setParameter("value", String.valueOf(value))
                .setParameter("key", "MAX_ACTIVE_RESERVATIONS_PER_MEMBER")
                .executeUpdate();
    }

    private void updateReservationReadyHoldHoursSetting(int value) {
        entityManager.createQuery("UPDATE ApplicationSetting s SET s.settingValue = :value WHERE s.settingKey = :key")
                .setParameter("value", String.valueOf(value))
                .setParameter("key", "RESERVATION_READY_HOLD_HOURS")
                .executeUpdate();
    }

    private static void assertResourceNotFoundCode(String expectedCode, Executable executable) {
        ResourceNotFoundException exception =
                (ResourceNotFoundException) assertThrows(ResourceNotFoundException.class, executable);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
    }

    // ---------------------------------------------------------------
    // cancelOwnReservation / cancelReservation — DEV-08.6, WAITING
    // ---------------------------------------------------------------

    @Test
    void cancelOwnReservationCancelsAWaitingReservationOwnedBySelf() {
        AppUser self = persistMember("service-cancel-waiting-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        ReservationResponse response = reservationService.cancelOwnReservation(self.getId(), reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.assignedCopy()).isNull();
    }

    @Test
    void cancelReservationCancelsAWaitingReservationAsStaff() {
        authenticateWithReservationManage();
        AppUser member = persistMember("service-cancel-waiting-staff@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(member, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        ReservationResponse response = reservationService.cancelReservation(reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelOwnReservationRejectsAnotherMembersWaitingReservation() {
        AppUser self = persistMember("service-cancel-other-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser other = persistMember("service-cancel-other-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(other, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertResourceNotFoundCode("RESERVATION_NOT_FOUND",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelOwnReservationOnUnknownReservationReturnsNotFound() {
        AppUser self = persistMember("service-cancel-unknown@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        entityManager.flush();

        assertResourceNotFoundCode("RESERVATION_NOT_FOUND",
                () -> reservationService.cancelOwnReservation(self.getId(), 999999999L));
    }

    @Test
    void cancelOwnReservationRejectsAnAlreadyCancelledReservation() {
        AppUser self = persistMember("service-cancel-already-cancelled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_NOT_CANCELLABLE",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelOwnReservationRejectsAnExpiredReservation() {
        AppUser self = persistMember("service-cancel-expired-reservation@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.EXPIRED, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_NOT_CANCELLABLE",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelOwnReservationRejectsAFulfilledReservation() {
        AppUser self = persistMember("service-cancel-fulfilled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FULFILLED-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(self, copy);
        Reservation reservation = persistFulfilledReservation(self, title, copy, loan, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_NOT_CANCELLABLE",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelReservationDeniedWithoutReservationManagePermission() {
        authenticateAsAnonymous();
        AppUser member = persistMember("service-cancel-staff-noperm@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(member, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertThrows(AccessDeniedException.class, () -> reservationService.cancelReservation(reservation.getId()));
    }

    // ---------------------------------------------------------------
    // cancelOwnReservation / cancelReservation — DEV-08.6, READY
    // ---------------------------------------------------------------

    @Test
    void cancelOwnReservationCancelsAReadyReservationPreservingAssignedCopyAndExpirationDate() {
        AppUser self = persistMember("service-cancel-ready-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-1");
        Instant expirationDate = Instant.now().plusSeconds(3600);
        Reservation reservation = persistReadyReservation(self, title, copy, Instant.now(), expirationDate);
        entityManager.flush();

        ReservationResponse response = reservationService.cancelOwnReservation(self.getId(), reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.assignedCopy()).isNotNull();
        assertThat(response.assignedCopy().id()).isEqualTo(copy.getId());
        assertThat(response.expirationDate()).isEqualTo(expirationDate);
    }

    @Test
    void cancelReservationCancelsAReadyReservationAsStaff() {
        authenticateWithReservationManage();
        AppUser member = persistMember("service-cancel-ready-staff@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-STAFF-1");
        Reservation reservation = persistReadyReservation(member, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        ReservationResponse response = reservationService.cancelReservation(reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelOwnReservationRejectsAnotherMembersReadyReservation() {
        AppUser self = persistMember("service-cancel-ready-other-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser other = persistMember("service-cancel-ready-other-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-OTHER-1");
        Reservation reservation = persistReadyReservation(other, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        assertResourceNotFoundCode("RESERVATION_NOT_FOUND",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelReadyReservationSetsCopyAvailableWhenNoWaitingReservationExists() {
        AppUser self = persistMember("service-cancel-ready-available@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-AVAILABLE-1");
        Reservation reservation = persistReadyReservation(self, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        reservationService.cancelOwnReservation(self.getId(), reservation.getId());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void cancelReadyReservationPromotesNextAdmissibleWaitingReservationAndKeepsCopyReserved() {
        AppUser readyOwner = persistMember("service-cancel-ready-promote-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser nextMember = persistMember("service-cancel-ready-promote-next@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-PROMOTE-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation waitingReservation = persistReservation(nextMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedWaiting.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(reloadedWaiting.getExpirationDate()).isNotNull();
    }

    @Test
    void cancelReadyReservationReadsHoldHoursDynamicallyWhenPromotingNextReservation() {
        AppUser readyOwner = persistMember("service-cancel-ready-hold-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser nextMember = persistMember("service-cancel-ready-hold-next@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-HOLD-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation waitingReservation = persistReservation(nextMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        updateReservationReadyHoldHoursSetting(1);
        entityManager.flush();
        Instant before = clock.instant();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Instant after = clock.instant();
        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getExpirationDate()).isBetween(before.plusSeconds(3600), after.plusSeconds(3600));
    }

    // ---------------------------------------------------------------
    // cancelReadyReservation — DEV-08.6, admissibilité FIFO (DEV-08.1 §11)
    // ---------------------------------------------------------------

    @Test
    void cancelReadyReservationSkipsABlockedFirstCandidateAndPromotesTheNextOne() {
        AppUser readyOwner = persistMember("service-cancel-fifo-blocked-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser blockedMember = persistMember("service-cancel-fifo-blocked-first@primatis.test", MemberStatus.BLOCKED, LocalDate.now(clock).plusYears(1));
        AppUser admissibleMember = persistMember("service-cancel-fifo-blocked-second@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-BLOCKED-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation blockedWaiting = persistReservation(blockedMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedBlocked = entityManager.find(Reservation.class, blockedWaiting.getId());
        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedBlocked.getReservationStatus())
                .as("le candidat non admissible n'est jamais modifié, jamais bloquant")
                .isEqualTo(ReservationStatus.WAITING);
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedAdmissible.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
    }

    @Test
    void cancelReadyReservationSkipsAnExpiredMembershipFirstCandidate() {
        AppUser readyOwner = persistMember("service-cancel-fifo-expired-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser expiredMember = persistMember("service-cancel-fifo-expired-first@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        AppUser admissibleMember = persistMember("service-cancel-fifo-expired-second@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-EXPIRED-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation expiredWaiting = persistReservation(expiredMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedExpired = entityManager.find(Reservation.class, expiredWaiting.getId());
        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedExpired.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
        // Synchronisation paresseuse déclenchée par la revalidation d'admissibilité :
        // le membre expiré est désormais MemberStatus.EXPIRED en base.
        AppUser reloadedExpiredMember = entityManager.find(AppUser.class, expiredMember.getId());
        assertThat(reloadedExpiredMember.getMemberStatus()).isEqualTo(MemberStatus.EXPIRED);
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    @Test
    void cancelReadyReservationSkipsSeveralNonAdmissibleCandidatesBeforeAdmissibleOne() {
        AppUser readyOwner = persistMember("service-cancel-fifo-multi-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser blockedMember = persistMember("service-cancel-fifo-multi-blocked@primatis.test", MemberStatus.BLOCKED, LocalDate.now(clock).plusYears(1));
        AppUser expiredMember = persistMember("service-cancel-fifo-multi-expired@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        AppUser admissibleMember = persistMember("service-cancel-fifo-multi-admissible@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-MULTI-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        persistReservation(blockedMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(180));
        persistReservation(expiredMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    /**
     * Aucune source Reservation n'impose qu'une Fine {@code UNPAID} exclue un
     * candidat WAITING de la promotion FIFO (DEV-08.1 §9/§19, mission
     * DEV-08.6 §11) — ne jamais transposer la règle Loan.
     */
    @Test
    void cancelReadyReservationPromotesACandidateDespiteAnUnpaidFine() {
        AppUser readyOwner = persistMember("service-cancel-fifo-fine-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser candidateWithFine = persistMember("service-cancel-fifo-fine-candidate@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-FINE-1");
        Copy otherCopy = persistCopy(persistTitle(), "SERVICE-CANCEL-FIFO-FINE-LOAN-1");
        otherCopy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(candidateWithFine, otherCopy);
        persistFine(loan, FineStatus.UNPAID);
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation waitingReservation = persistReservation(candidateWithFine, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    // ---------------------------------------------------------------
    // Confirmation N+1 / EntityGraph (DEV-08.4 — décision différée par
    // DEV-08.3). ReservationResponse.from accède inconditionnellement à
    // user/title (LAZY) et conditionnellement à assignedCopy (LAZY) :
    // vérifie que le @EntityGraph de ReservationRepository.findAll/
    // findByUserId charge bien ces relations en une seule requête (déjà
    // initialisées au retour, sans accès supplémentaire déclencheur).
    // ---------------------------------------------------------------

    @Test
    void listReservationsEagerlyInitializesUserTitleAndAssignedCopyButNotFulfilledByLoan() {
        authenticateWithReservationRead();
        AppUser member = persistUser("service-entitygraph-staff@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-ENTITYGRAPH-1");
        persistReadyReservation(member, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();
        entityManager.clear();

        Page<Reservation> page = reservationRepository.findAll(PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
        for (Reservation reservation : page.getContent()) {
            assertThat(Hibernate.isInitialized(reservation.getUser()))
                    .as("user doit être chargé par l'EntityGraph, pas paresseusement")
                    .isTrue();
            assertThat(Hibernate.isInitialized(reservation.getTitle()))
                    .as("title doit être chargé par l'EntityGraph, pas paresseusement")
                    .isTrue();
            if (reservation.getAssignedCopy() != null) {
                assertThat(Hibernate.isInitialized(reservation.getAssignedCopy()))
                        .as("assignedCopy doit être chargé par l'EntityGraph quand non-null")
                        .isTrue();
            }
        }
    }

    @Test
    void listOwnReservationsEagerlyInitializesUserAndTitle() {
        AppUser self = persistUser("service-entitygraph-self@primatis.test");
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();
        entityManager.clear();

        Page<Reservation> page = reservationRepository.findByUserId(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
        for (Reservation reservation : page.getContent()) {
            assertThat(Hibernate.isInitialized(reservation.getUser())).isTrue();
            assertThat(Hibernate.isInitialized(reservation.getTitle())).isTrue();
        }
    }

    // ---------------------------------------------------------------
    // Confirmation structurelle N+1 (même précédent que
    // LoanServiceTests.listLoansMethodBodyNeverReferencesAnAdditionalRepository,
    // DEV-07.4) : ni listReservations ni listOwnReservations n'introduisent
    // de Repository/requête supplémentaire pour compenser un N+1 — la
    // compensation passe exclusivement par le @EntityGraph du Repository
    // (confirmé ci-dessus), jamais par une Repository additionnelle dans
    // le Service.
    // ---------------------------------------------------------------

    @Test
    void listReservationsMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/reservation/ReservationService.java",
                "public Page<ReservationResponse> listReservations(Pageable pageable) {");

        assertThat(methodBody)
                .as("listReservations se limite à reservationRepository.findAll : aucune Repository additionnelle")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("titleRepository")
                .doesNotContain("loanRepository")
                .doesNotContain("applicationSettingService")
                .contains("reservationRepository.findAll(pageable)");
    }

    @Test
    void listOwnReservationsMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/reservation/ReservationService.java",
                "public Page<ReservationResponse> listOwnReservations(Long selfUserId, Pageable pageable) {");

        assertThat(methodBody)
                .as("listOwnReservations se limite à reservationRepository.findByUserId : "
                        + "aucune Repository additionnelle")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("titleRepository")
                .doesNotContain("loanRepository")
                .doesNotContain("applicationSettingService")
                .contains("reservationRepository.findByUserId(selfUserId, pageable)");
    }

    private static String extractMethodBody(String sourceFilePath, String signature) throws IOException {
        String source = Files.readString(Path.of(sourceFilePath));
        int signatureIndex = source.indexOf(signature);
        assertThat(signatureIndex).as("signature attendue introuvable dans " + sourceFilePath).isNotEqualTo(-1);

        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("Corps de méthode non fermé pour la signature : " + signature);
    }

    private static int indexOfReservationId(List<ReservationResponse> content, Long reservationId) {
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).id().equals(reservationId)) {
                return i;
            }
        }
        throw new IllegalStateException("Reservation " + reservationId + " absente du contenu de la page.");
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

    private Title persistTitle() {
        Title title = new Title();
        title.setTitle("Titre de test");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        entityManager.persist(title);
        return title;
    }

    private Copy persistCopy(Title title, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Reservation persistReservation(AppUser user, Title title, ReservationStatus status, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(status);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistReadyReservation(
            AppUser user, Title title, Copy copy, Instant reservationDate, Instant expirationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.READY);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(expirationDate);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistFulfilledReservation(
            AppUser user, Title title, Copy copy, Loan loan, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.FULFILLED);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(reservationDate.plusSeconds(3600));
        reservation.setFulfilledByLoan(loan);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private AppUser persistMember(String email, MemberStatus memberStatus, LocalDate memberExpirationDate) {
        AppUser user = persistUser(email);
        user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
        user.setMemberStatus(memberStatus);
        user.setRegistrationDate(LocalDate.now(clock).minusYears(1));
        user.setMemberExpirationDate(memberExpirationDate);
        return user;
    }

    private Loan persistLoan(AppUser user, Copy copy) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(clock.instant());
        loan.setDueDate(LocalDate.now(clock).plusDays(21));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(clock.instant());
        loan.setUpdatedAt(clock.instant());
        entityManager.persist(loan);
        return loan;
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.TEN);
        fine.setReason("Retard de test");
        fine.setIssuedAt(clock.instant());
        fine.setFineStatus(status);
        entityManager.persist(fine);
        return fine;
    }
}
