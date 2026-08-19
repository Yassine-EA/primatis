package be.primatis.fine;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel la primitive {@link FineRepository}
 * ajoutée en DEV-07.2 : {@code existsByLoanUserIdAndFineStatus} (jointure
 * Fine → Loan → AppUser). Ne teste pas {@code findByLoanId}, déjà couvert
 * par {@code RepositoryQueryTests} (DEV-02.5). Aucun calcul de montant,
 * aucune règle d'éligibilité au prêt testée ici — uniquement l'existence.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FineRepositoryTests {

    @Autowired
    private FineRepository fineRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void detectsAnUnpaidFineOfTheBorrower() {
        AppUser user = persistUser("fine-unpaid@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-UNPAID");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);
        persistFine(loan, FineStatus.UNPAID);
        entityManager.flush();

        assertThat(fineRepository.existsByLoanUserIdAndFineStatus(user.getId(), FineStatus.UNPAID)).isTrue();
    }

    @Test
    void ignoresPaidFinesWhenCheckingForUnpaidOnes() {
        AppUser user = persistUser("fine-paid@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-PAID");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);
        persistFine(loan, FineStatus.PAID);
        entityManager.flush();

        assertThat(fineRepository.existsByLoanUserIdAndFineStatus(user.getId(), FineStatus.UNPAID)).isFalse();
    }

    @Test
    void ignoresCancelledFinesWhenCheckingForUnpaidOnes() {
        AppUser user = persistUser("fine-cancelled@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-CANCELLED");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);
        persistFine(loan, FineStatus.CANCELLED);
        entityManager.flush();

        assertThat(fineRepository.existsByLoanUserIdAndFineStatus(user.getId(), FineStatus.UNPAID)).isFalse();
    }

    @Test
    void isolatesUnpaidFineDetectionBetweenBorrowers() {
        AppUser borrowerWithFine = persistUser("fine-isolation-owner@primatis.test");
        AppUser otherBorrower = persistUser("fine-isolation-other@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-ISOLATION");
        Loan loan = persistLoan(borrowerWithFine, copy, LoanStatus.RETURNED);
        persistFine(loan, FineStatus.UNPAID);
        entityManager.flush();

        assertThat(fineRepository.existsByLoanUserIdAndFineStatus(borrowerWithFine.getId(), FineStatus.UNPAID)).isTrue();
        assertThat(fineRepository.existsByLoanUserIdAndFineStatus(otherBorrower.getId(), FineStatus.UNPAID)).isFalse();
    }

    @Test
    void returnsFalseWhenTheBorrowerHasNoFineAtAll() {
        AppUser user = persistUser("fine-none@primatis.test");
        entityManager.flush();

        assertThat(fineRepository.existsByLoanUserIdAndFineStatus(user.getId(), FineStatus.UNPAID)).isFalse();
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

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.valueOf(5.00));
        fine.setReason("Retard de test");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(status);
        if (status == FineStatus.PAID) {
            fine.setPaidAt(Instant.now());
        }
        if (status == FineStatus.CANCELLED) {
            fine.setCancelledAt(Instant.now());
        }
        entityManager.persist(fine);
        return fine;
    }
}
