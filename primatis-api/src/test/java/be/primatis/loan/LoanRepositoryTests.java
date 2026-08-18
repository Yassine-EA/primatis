package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link LoanRepository}
 * ajoutées en DEV-07.2 : {@code countByUserIdAndLoanStatusIn} et
 * {@code findByUserId}. Ne teste pas {@code findByCopyIdAndLoanStatusIn},
 * déjà couvert par {@code RepositoryQueryTests} (DEV-02.5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanRepositoryTests {

    @Autowired
    private LoanRepository loanRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // countByUserIdAndLoanStatusIn
    // ---------------------------------------------------------------

    @Test
    void countsOnlyOpenLoansOfTheBorrower() {
        AppUser user = persistUser("borrower-count@primatis.test");
        Title title = persistTitle();
        Copy copyA = persistCopy(title, "COUNT-A");
        Copy copyB = persistCopy(title, "COUNT-B");
        Copy copyC = persistCopy(title, "COUNT-C");
        persistLoan(user, copyA, LoanStatus.ACTIVE);
        persistLoan(user, copyB, LoanStatus.OVERDUE);
        persistLoan(user, copyC, LoanStatus.RETURNED);
        entityManager.flush();

        long openCount = loanRepository.countByUserIdAndLoanStatusIn(
                user.getId(), List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));

        assertThat(openCount).isEqualTo(2);
    }

    @Test
    void excludesReturnedLoansFromTheOpenCount() {
        AppUser user = persistUser("borrower-returned-only@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "RETURNED-ONLY");
        persistLoan(user, copy, LoanStatus.RETURNED);
        entityManager.flush();

        long openCount = loanRepository.countByUserIdAndLoanStatusIn(
                user.getId(), List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));

        assertThat(openCount).isZero();
    }

    @Test
    void isolatesTheOpenLoanCountBetweenBorrowers() {
        AppUser borrowerOne = persistUser("borrower-isolation-1@primatis.test");
        AppUser borrowerTwo = persistUser("borrower-isolation-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "ISOLATION-1");
        Copy copyTwo = persistCopy(title, "ISOLATION-2");
        persistLoan(borrowerOne, copyOne, LoanStatus.ACTIVE);
        persistLoan(borrowerTwo, copyTwo, LoanStatus.ACTIVE);
        entityManager.flush();

        long borrowerOneCount = loanRepository.countByUserIdAndLoanStatusIn(
                borrowerOne.getId(), List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));
        long borrowerTwoCount = loanRepository.countByUserIdAndLoanStatusIn(
                borrowerTwo.getId(), List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));

        assertThat(borrowerOneCount).isEqualTo(1);
        assertThat(borrowerTwoCount).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // findByUserId
    // ---------------------------------------------------------------

    @Test
    void findsAllLoansOfABorrowerRegardlessOfStatus() {
        AppUser user = persistUser("borrower-history@primatis.test");
        Title title = persistTitle();
        Copy copyA = persistCopy(title, "HISTORY-A");
        Copy copyB = persistCopy(title, "HISTORY-B");
        Loan active = persistLoan(user, copyA, LoanStatus.ACTIVE);
        Loan returned = persistLoan(user, copyB, LoanStatus.RETURNED);
        entityManager.flush();

        Page<Loan> page = loanRepository.findByUserId(user.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Loan::getId)
                .containsExactlyInAnyOrder(active.getId(), returned.getId());
    }

    @Test
    void isolatesLoanHistoryBetweenBorrowers() {
        AppUser borrowerOne = persistUser("history-isolation-1@primatis.test");
        AppUser borrowerTwo = persistUser("history-isolation-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "HISTORY-ISOLATION-1");
        Copy copyTwo = persistCopy(title, "HISTORY-ISOLATION-2");
        persistLoan(borrowerOne, copyOne, LoanStatus.ACTIVE);
        persistLoan(borrowerTwo, copyTwo, LoanStatus.ACTIVE);
        entityManager.flush();

        Page<Loan> borrowerOnePage = loanRepository.findByUserId(borrowerOne.getId(), PageRequest.of(0, 20));

        assertThat(borrowerOnePage.getContent()).hasSize(1);
        assertThat(borrowerOnePage.getContent().get(0).getUser().getId()).isEqualTo(borrowerOne.getId());
    }

    @Test
    void findByUserIdReturnsEmptyPageForABorrowerWithNoLoan() {
        AppUser user = persistUser("borrower-no-loan@primatis.test");
        entityManager.flush();

        Page<Loan> page = loanRepository.findByUserId(user.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
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
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Loan persistLoan(AppUser user, Copy copy, LoanStatus status) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(Instant.now());
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }
}
