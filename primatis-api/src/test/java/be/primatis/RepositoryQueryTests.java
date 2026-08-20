package be.primatis;

import be.primatis.article.Article;
import be.primatis.article.ArticleRepository;
import be.primatis.article.ArticleStatus;
import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.fine.Fine;
import be.primatis.fine.FineRepository;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanRepository;
import be.primatis.loan.LoanStatus;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationRepository;
import be.primatis.reservation.ReservationStatus;
import be.primatis.setting.ApplicationSetting;
import be.primatis.setting.ApplicationSettingRepository;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les méthodes dérivées ajoutées en DEV-02.5,
 * chacune directement liée à un identifiant fonctionnel ou un invariant
 * structurel déjà fixé par V001, complétées en DEV-05.2 par les primitives
 * {@code existsByEmail}/{@code existsByMemberNumber} d'{@link AppUserRepository}.
 * Ne teste pas les méthodes JpaRepository standard (save/findById/...), déjà
 * couvertes par Spring Data lui-même.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryQueryTests {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findsAppUserByEmail() {
        AppUser user = persistUser("repo-check@primatis.test");
        entityManager.flush();

        assertThat(appUserRepository.findByEmail("repo-check@primatis.test"))
                .isPresent()
                .get()
                .extracting(AppUser::getId)
                .isEqualTo(user.getId());
        assertThat(appUserRepository.findByEmail("absent@primatis.test")).isEmpty();
    }

    @Test
    void existsByEmailReflectsPresenceAndAbsence() {
        persistUser("repo-exists-email@primatis.test");
        entityManager.flush();

        assertThat(appUserRepository.existsByEmail("repo-exists-email@primatis.test")).isTrue();
        assertThat(appUserRepository.existsByEmail("repo-exists-email-absent@primatis.test")).isFalse();
    }

    @Test
    void existsByMemberNumberReflectsPresenceAndAbsence() {
        AppUser user = persistUser("repo-exists-member-number@primatis.test");
        user.setMemberNumber("M900000001");
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.now());
        entityManager.flush();

        assertThat(appUserRepository.existsByMemberNumber("M900000001")).isTrue();
        assertThat(appUserRepository.existsByMemberNumber("M900000002")).isFalse();
    }

    @Test
    void findsCopyByInventoryCode() {
        Title title = persistTitle();
        Copy copy = persistCopy(title, "REPO-CHECK-INV-1");
        entityManager.flush();

        assertThat(copyRepository.findByInventoryCode("REPO-CHECK-INV-1"))
                .isPresent()
                .get()
                .extracting(Copy::getId)
                .isEqualTo(copy.getId());
    }

    @Test
    void findsArticleBySlug() {
        AppUser author = persistUser("repo-article-author@primatis.test");
        Article article = persistArticle(author, "repo-check-slug");
        entityManager.flush();

        assertThat(articleRepository.findBySlug("repo-check-slug"))
                .isPresent()
                .get()
                .extracting(Article::getId)
                .isEqualTo(article.getId());
    }

    @Test
    void findsApplicationSettingByKey() {
        Optional<ApplicationSetting> found = applicationSettingRepository.findBySettingKey("LOAN_DURATION_DAYS");

        assertThat(found).isPresent();
        assertThat(found.get().getSettingValue()).isEqualTo("21");
    }

    @Test
    void allFourMandatoryApplicationSettingsAreBootstrappedWithCorrectValues() {
        assertThat(applicationSettingRepository.findBySettingKey("LOAN_DURATION_DAYS"))
                .isPresent().get().extracting(ApplicationSetting::getSettingValue).isEqualTo("21");
        assertThat(applicationSettingRepository.findBySettingKey("MAX_ACTIVE_RESERVATIONS_PER_MEMBER"))
                .isPresent().get().extracting(ApplicationSetting::getSettingValue).isEqualTo("10");
        assertThat(applicationSettingRepository.findBySettingKey("RESERVATION_READY_HOLD_HOURS"))
                .isPresent().get().extracting(ApplicationSetting::getSettingValue).isEqualTo("48");
        assertThat(applicationSettingRepository.findBySettingKey("LOAN_DUE_SOON_DAYS"))
                .isPresent().get().extracting(ApplicationSetting::getSettingValue).isEqualTo("3");
    }

    @Test
    void bothFineApplicationSettingsAreBootstrappedWithCorrectValues() {
        assertThat(applicationSettingRepository.findBySettingKey("FINE_WEEKLY_RATE"))
                .isPresent().get()
                .extracting(ApplicationSetting::getSettingValue, ApplicationSetting::getValueType)
                .containsExactly("0.80", "DECIMAL");
        assertThat(applicationSettingRepository.findBySettingKey("FINE_MAX_AMOUNT"))
                .isPresent().get()
                .extracting(ApplicationSetting::getSettingValue, ApplicationSetting::getValueType)
                .containsExactly("25.00", "DECIMAL");
    }

    @Test
    void findsFineByLoanId() {
        AppUser user = persistUser("repo-fine-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "REPO-CHECK-INV-2");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);
        Fine fine = persistFine(loan);
        entityManager.flush();

        assertThat(fineRepository.findByLoanId(loan.getId()))
                .isPresent()
                .get()
                .extracting(Fine::getId)
                .isEqualTo(fine.getId());
    }

    @Test
    void findsOpenLoanByCopy() {
        AppUser user = persistUser("repo-loan-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "REPO-CHECK-INV-3");
        Loan openLoan = persistLoan(user, copy, LoanStatus.ACTIVE);
        entityManager.flush();

        List<Loan> found = loanRepository.findByCopyIdAndLoanStatusIn(
                copy.getId(), List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE));

        assertThat(found).extracting(Loan::getId).containsExactly(openLoan.getId());
    }

    @Test
    void findsActiveReservationByUserAndTitle() {
        AppUser user = persistUser("repo-reservation-user@primatis.test");
        Title title = persistTitle();
        Reservation reservation = persistReservation(user, title, ReservationStatus.WAITING, null, null);
        entityManager.flush();

        List<Reservation> found = reservationRepository.findByUserIdAndTitleIdAndReservationStatusIn(
                user.getId(), title.getId(), List.of(ReservationStatus.WAITING, ReservationStatus.READY));

        assertThat(found).extracting(Reservation::getId).containsExactly(reservation.getId());
    }

    @Test
    void findsReadyReservationByAssignedCopy() {
        AppUser user = persistUser("repo-reservation-ready-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "REPO-CHECK-INV-4");
        Reservation reservation = persistReservation(
                user, title, ReservationStatus.READY, copy, Instant.now().plusSeconds(3600));
        entityManager.flush();

        assertThat(reservationRepository.findByAssignedCopyIdAndReservationStatus(copy.getId(), ReservationStatus.READY))
                .isPresent()
                .get()
                .extracting(Reservation::getId)
                .isEqualTo(reservation.getId());
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

    private Article persistArticle(AppUser authorUser, String slug) {
        Article article = new Article();
        article.setAuthorUser(authorUser);
        article.setTitle("Article de test");
        article.setContent("Contenu de test");
        article.setSlug(slug);
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setCreatedAt(Instant.now());
        article.setUpdatedAt(Instant.now());
        entityManager.persist(article);
        return article;
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

    private Fine persistFine(Loan loan) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.valueOf(5.00));
        fine.setReason("Retard de test");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(FineStatus.UNPAID);
        entityManager.persist(fine);
        return fine;
    }

    private Reservation persistReservation(
            AppUser user, Title title, ReservationStatus status, Copy assignedCopy, Instant expirationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(Instant.now());
        reservation.setReservationStatus(status);
        reservation.setAssignedCopy(assignedCopy);
        reservation.setExpirationDate(expirationDate);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }
}
