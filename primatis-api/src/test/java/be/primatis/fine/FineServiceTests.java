package be.primatis.fine;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.dto.FineResponse;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.setting.ApplicationSetting;
import be.primatis.setting.ApplicationSettingRepository;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link FineService#createForLateReturnIfApplicable} (DEV-09.6,
 * DEV-DEC-0046/0047/0048/0049) et {@link FineService#confirmExternalPayment}
 * (DEV-09.7, DEV-DEC-0050) contre PostgreSQL réel : calcul du retard, des
 * semaines entamées, du montant (settings réellement consommés via {@code
 * ApplicationSettingService.getDecimal}, jamais codés en dur), plafond,
 * motif généré, anti-doublon, transition {@code UNPAID → PAID}, refus des
 * statuts terminaux. Appelle directement {@link FineService}, jamais via
 * {@code LoanService.registerReturn}/{@code FineController} — l'intégration
 * complète (câblage transactionnel, ordre de locks, rollback, concurrence)
 * est vérifiée séparément par {@code LoanServiceTests}/{@code
 * LoanServiceConcurrencyTests}/{@code FineServiceConcurrencyTests}/{@code
 * FineControllerTests}, pour ne pas dupliquer la préparation complète de
 * chaque variante. Classe {@code @Transactional} (rollback de test) :
 * n'atteste jamais un état réellement committé — voir {@code
 * FineServiceConcurrencyTests} pour la preuve de persistance après commit
 * réel (mission §12 point 10).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FineServiceTests {

    @Autowired
    private FineService fineService;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithFineManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("FINE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithFineRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("FINE_READ"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithoutFineRead() {
        Authentication authentication = new TestingAuthenticationToken("1", null, List.of());
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // Retour non tardif
    // ---------------------------------------------------------------

    @Test
    void createsNoFineWhenReturnedBeforeDueDate() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 18));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-18T09:00:00Z"));

        assertThat(fineRepository.findByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void createsNoFineWhenReturnedExactlyOnDueDate() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        assertThat(fineRepository.findByLoanId(loan.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // Semaines entamées (DEV-DEC-0046 : startedWeeks = ceil(overdueDays / 7))
    // ---------------------------------------------------------------

    @Test
    void chargesOneWeekForOneDayLate() {
        assertAmountForOverdueDays(1, new BigDecimal("0.80"));
    }

    @Test
    void chargesOneWeekForSevenDaysLate() {
        assertAmountForOverdueDays(7, new BigDecimal("0.80"));
    }

    @Test
    void chargesTwoWeeksForEightDaysLate() {
        assertAmountForOverdueDays(8, new BigDecimal("1.60"));
    }

    @Test
    void chargesTwoWeeksForFourteenDaysLate() {
        assertAmountForOverdueDays(14, new BigDecimal("1.60"));
    }

    @Test
    void chargesThreeWeeksForFifteenDaysLate() {
        assertAmountForOverdueDays(15, new BigDecimal("2.40"));
    }

    // ---------------------------------------------------------------
    // Plafond (FINE_MAX_AMOUNT = 25.00)
    // ---------------------------------------------------------------

    @Test
    void staysJustUnderTheCapAt217DaysLate() {
        assertAmountForOverdueDays(217, new BigDecimal("24.80"));
    }

    @Test
    void capsAmountExactlyAt218DaysLate() {
        assertAmountForOverdueDays(218, new BigDecimal("25.00"));
    }

    @Test
    void capsAmountFarBeyondTheCapAt300DaysLate() {
        assertAmountForOverdueDays(300, new BigDecimal("25.00"));
    }

    // ---------------------------------------------------------------
    // Fine créée — statut, timestamps, motif, Loan
    // ---------------------------------------------------------------

    @Test
    void createsAnUnpaidFineWithNullPaidAtAndCancelledAt() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getFineStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(fine.getPaidAt()).isNull();
        assertThat(fine.getCancelledAt()).isNull();
    }

    @Test
    void generatesTheExpectedReasonText() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getReason()).isEqualTo("Retour tardif — 5 jour(s) de retard, 1 semaine(s) entamée(s).");
    }

    @Test
    void linksTheFineToTheCorrectLoan() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getLoan().getId()).isEqualTo(loan.getId());
    }

    @Test
    void usesTheProvidedInstantAsIssuedAt() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Instant now = Instant.parse("2026-08-20T14:32:07Z");

        fineService.createForLateReturnIfApplicable(loan, now);

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getIssuedAt()).isEqualTo(now);
    }

    // ---------------------------------------------------------------
    // Settings réellement consommés (aucune valeur codée en dur)
    // ---------------------------------------------------------------

    @Test
    void usesTheConfiguredWeeklyRateRatherThanAHardcodedValue() {
        setSettingValue("FINE_WEEKLY_RATE", "2.50");
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        // 5 jours de retard -> 1 semaine entamée x 2.50 (valeur reconfigurée) = 2.50
        assertThat(fine.getAmount()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void usesTheConfiguredMaxAmountRatherThanAHardcodedValue() {
        setSettingValue("FINE_MAX_AMOUNT", "1.00");
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        // 19 jours de retard -> 3 semaines entamées x 0.80 = 2.40, plafonné à 1.00 (valeur reconfigurée)
        assertThat(fine.getAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    // ---------------------------------------------------------------
    // Anti-doublon (DEV-DEC-0049)
    // ---------------------------------------------------------------

    @Test
    void throwsWhenAFineAlreadyExistsForTheLoan() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        persistExistingFine(loan);

        assertThatThrownBy(() -> fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(loan.getId()));
    }

    // ---------------------------------------------------------------
    // confirmExternalPayment (DEV-09.7, DEV-DEC-0050)
    // ---------------------------------------------------------------

    @Test
    void confirmExternalPaymentTransitionsUnpaidToPaid() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        assertThat(response.fineStatus()).isEqualTo(FineStatus.PAID);
    }

    @Test
    void confirmExternalPaymentSetsPaidAtCloseToNow() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        Instant before = Instant.now();

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        Instant after = Instant.now();
        assertThat(response.paidAt()).isBetween(before.minusSeconds(1), after.plusSeconds(5));
    }

    @Test
    void confirmExternalPaymentNeverChangesAmountReasonOrIssuedAt() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        BigDecimal originalAmount = fine.getAmount();
        String originalReason = fine.getReason();
        Instant originalIssuedAt = fine.getIssuedAt();

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        assertThat(response.amount()).isEqualByComparingTo(originalAmount);
        assertThat(response.reason()).isEqualTo(originalReason);
        assertThat(response.issuedAt()).isEqualTo(originalIssuedAt);
        assertThat(response.loan().id()).isEqualTo(loan.getId());
    }

    @Test
    void confirmExternalPaymentKeepsCancelledAtNull() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void confirmExternalPaymentOnUnknownFineThrowsResourceNotFound() {
        authenticateWithFineManage();

        assertThatThrownBy(() -> fineService.confirmExternalPayment(999999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getCode()).isEqualTo("FINE_NOT_FOUND"));
    }

    @Test
    void confirmExternalPaymentOnAlreadyPaidFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.PAID);

        assertThatThrownBy(() -> fineService.confirmExternalPayment(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_PAYABLE"));
    }

    @Test
    void confirmExternalPaymentOnCancelledFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.CANCELLED);

        assertThatThrownBy(() -> fineService.confirmExternalPayment(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_PAYABLE"));
    }

    // ---------------------------------------------------------------
    // cancelFine (DEV-09.8)
    // ---------------------------------------------------------------

    @Test
    void cancelFineTransitionsUnpaidToCancelled() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.cancelFine(fine.getId());

        assertThat(response.fineStatus()).isEqualTo(FineStatus.CANCELLED);
    }

    @Test
    void cancelFineSetsCancelledAtCloseToNow() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        Instant before = Instant.now();

        FineResponse response = fineService.cancelFine(fine.getId());

        Instant after = Instant.now();
        assertThat(response.cancelledAt()).isBetween(before.minusSeconds(1), after.plusSeconds(5));
    }

    @Test
    void cancelFineKeepsPaidAtNull() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.cancelFine(fine.getId());

        assertThat(response.paidAt()).isNull();
    }

    @Test
    void cancelFineNeverChangesAmountReasonOrIssuedAt() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        BigDecimal originalAmount = fine.getAmount();
        String originalReason = fine.getReason();
        Instant originalIssuedAt = fine.getIssuedAt();

        FineResponse response = fineService.cancelFine(fine.getId());

        assertThat(response.amount()).isEqualByComparingTo(originalAmount);
        assertThat(response.reason()).isEqualTo(originalReason);
        assertThat(response.issuedAt()).isEqualTo(originalIssuedAt);
        assertThat(response.loan().id()).isEqualTo(loan.getId());
    }

    @Test
    void cancelFineOnUnknownFineThrowsResourceNotFound() {
        authenticateWithFineManage();

        assertThatThrownBy(() -> fineService.cancelFine(999999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getCode()).isEqualTo("FINE_NOT_FOUND"));
    }

    @Test
    void cancelFineOnAlreadyCancelledFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.CANCELLED);

        assertThatThrownBy(() -> fineService.cancelFine(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_CANCELLABLE"));
    }

    @Test
    void cancelFineOnPaidFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.PAID);

        assertThatThrownBy(() -> fineService.cancelFine(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_CANCELLABLE"));
    }

    // ---------------------------------------------------------------
    // listOwnFines (DEV-09.9)
    // ---------------------------------------------------------------

    @Test
    void listOwnFinesReturnsOnlyFinesOfTheCurrentMemberAcrossAllStatuses() {
        AppUser member = persistUser("fine-own-list-" + System.nanoTime() + "@primatis.test");
        Loan loanUnpaid = persistLoanForUser(member);
        Loan loanPaid = persistLoanForUser(member);
        Loan loanCancelled = persistLoanForUser(member);
        Fine fineUnpaid = persistFine(loanUnpaid, FineStatus.UNPAID);
        Fine finePaid = persistFine(loanPaid, FineStatus.PAID);
        Fine fineCancelled = persistFine(loanCancelled, FineStatus.CANCELLED);

        Page<FineResponse> page = fineService.listOwnFines(member.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(FineResponse::fineStatus)
                .containsExactlyInAnyOrder(FineStatus.UNPAID, FineStatus.PAID, FineStatus.CANCELLED);
        assertThat(page.getContent()).extracting(FineResponse::id)
                .containsExactlyInAnyOrder(fineUnpaid.getId(), finePaid.getId(), fineCancelled.getId());
    }

    @Test
    void listOwnFinesNeverLeaksAnotherMembersFine() {
        AppUser member = persistUser("fine-own-isolation-1-" + System.nanoTime() + "@primatis.test");
        AppUser other = persistUser("fine-own-isolation-2-" + System.nanoTime() + "@primatis.test");
        persistFine(persistLoanForUser(member), FineStatus.UNPAID);
        persistFine(persistLoanForUser(other), FineStatus.UNPAID);

        Page<FineResponse> page = fineService.listOwnFines(member.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).borrower().id()).isEqualTo(member.getId());
    }

    @Test
    void listOwnFinesRespectsPageSize() {
        AppUser member = persistUser("fine-own-pagination-" + System.nanoTime() + "@primatis.test");
        for (int i = 0; i < 3; i++) {
            persistFine(persistLoanForUser(member), FineStatus.UNPAID);
        }

        Page<FineResponse> firstPage = fineService.listOwnFines(member.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void listOwnFinesReturnsEmptyPageForAMemberWithNoFine() {
        AppUser member = persistUser("fine-own-empty-" + System.nanoTime() + "@primatis.test");

        Page<FineResponse> page = fineService.listOwnFines(member.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    // ---------------------------------------------------------------
    // listFines (DEV-09.9, FINE_READ)
    // ---------------------------------------------------------------

    @Test
    void listFinesReturnsAllFinesAcrossMembersAndStatuses() {
        authenticateWithFineRead();
        AppUser memberOne = persistUser("fine-staff-list-1-" + System.nanoTime() + "@primatis.test");
        AppUser memberTwo = persistUser("fine-staff-list-2-" + System.nanoTime() + "@primatis.test");
        Fine fineOne = persistFine(persistLoanForUser(memberOne), FineStatus.UNPAID);
        Fine fineTwo = persistFine(persistLoanForUser(memberTwo), FineStatus.PAID);
        Fine fineThree = persistFine(persistLoanForUser(memberTwo), FineStatus.CANCELLED);

        Page<FineResponse> page = fineService.listFines(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(FineResponse::id)
                .contains(fineOne.getId(), fineTwo.getId(), fineThree.getId());
        assertThat(page.getContent()).extracting(fine -> fine.borrower().id())
                .contains(memberOne.getId(), memberTwo.getId());
        assertThat(page.getContent()).extracting(FineResponse::fineStatus)
                .contains(FineStatus.UNPAID, FineStatus.PAID, FineStatus.CANCELLED);
    }

    @Test
    void listFinesRespectsPageSize() {
        authenticateWithFineRead();
        for (int i = 0; i < 3; i++) {
            persistFine(persistLoanForUser(
                    persistUser("fine-staff-pagination-" + i + "-" + System.nanoTime() + "@primatis.test")),
                    FineStatus.UNPAID);
        }

        Page<FineResponse> firstPage = fineService.listFines(PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void listFinesWithoutFineReadIsDenied() {
        authenticateWithoutFineRead();

        assertThatThrownBy(() -> fineService.listFines(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void assertAmountForOverdueDays(int overdueDays, BigDecimal expectedAmount) {
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        LocalDate returnDate = dueDate.plusDays(overdueDays);
        Loan loan = persistReturnedLoan(dueDate, returnDate);

        fineService.createForLateReturnIfApplicable(loan, returnDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getAmount()).isEqualByComparingTo(expectedAmount);
    }

    private void setSettingValue(String key, String value) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(key).orElseThrow();
        setting.setSettingValue(value);
    }

    private void persistExistingFine(Loan loan) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.TEN);
        fine.setReason("Fine préexistante (fixture anti-doublon)");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(FineStatus.PAID);
        fine.setPaidAt(Instant.now());
        entityManager.persist(fine);
        entityManager.flush();
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(new BigDecimal("5.00"));
        fine.setReason("Motif de test");
        fine.setIssuedAt(Instant.parse("2026-08-15T09:00:00Z"));
        fine.setFineStatus(status);
        if (status == FineStatus.PAID) {
            fine.setPaidAt(Instant.now());
        }
        if (status == FineStatus.CANCELLED) {
            fine.setCancelledAt(Instant.now());
        }
        entityManager.persist(fine);
        entityManager.flush();
        return fine;
    }

    /**
     * DEV-09.9 : contrairement à {@link #persistReturnedLoan}, réutilise un
     * {@code AppUser} fourni plutôt que d'en créer un nouveau — nécessaire
     * pour construire plusieurs Fines appartenant au même membre
     * (consultation membre) sans dupliquer la fixture utilisateur.
     */
    private Loan persistLoanForUser(AppUser user) {
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-SERVICE-" + System.nanoTime());
        LocalDate dueDate = LocalDate.of(2026, 8, 15);
        LocalDate returnDate = LocalDate.of(2026, 8, 20);

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(dueDate.minusDays(21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(dueDate);
        loan.setReturnDate(returnDate);
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        entityManager.flush();
        return loan;
    }

    private Loan persistReturnedLoan(LocalDate dueDate, LocalDate returnDate) {
        AppUser user = persistUser("fine-service-" + System.nanoTime() + "@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-SERVICE-" + System.nanoTime());

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(dueDate.minusDays(21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(dueDate);
        loan.setReturnDate(returnDate);
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        entityManager.flush();
        return loan;
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
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }
}
