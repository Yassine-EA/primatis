package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.loan.dto.LoanResponse;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie {@link LoanService} (DEV-07.4) contre PostgreSQL réel :
 * application réelle de {@code @PreAuthorize("hasAuthority('LOAN_READ')")}
 * sur {@code listLoans} via le proxy Spring (même principe que {@code
 * UserServiceTests}), pagination/tri de {@code listLoans}, ownership
 * structurelle et isolation de {@code listOwnLoans}. Ne reteste pas le
 * détail du mapping {@code Loan} → {@link LoanResponse} (déjà couvert par
 * {@code LoanDtoTests}, DEV-07.3).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanServiceTests {

    @Autowired
    private LoanService loanService;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithLoanRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("LOAN_READ"));
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
    // listLoans — staff, LOAN_READ
    // ---------------------------------------------------------------

    @Test
    void listLoansDeniedWithoutLoanReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> loanService.listLoans(PageRequest.of(0, 20)));
    }

    @Test
    void listLoansReturnsPaginatedAndMappedResultsAcrossBorrowers() {
        authenticateWithLoanRead();
        AppUser borrowerOne = persistUser("service-staff-list-1@primatis.test");
        AppUser borrowerTwo = persistUser("service-staff-list-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "SERVICE-STAFF-1");
        Copy copyTwo = persistCopy(title, "SERVICE-STAFF-2");
        Loan older = persistLoan(borrowerOne, copyOne, LoanStatus.ACTIVE, Instant.now().minusSeconds(120));
        Loan newer = persistLoan(borrowerTwo, copyTwo, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listLoans(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "loanDate", "id")));

        assertThat(page.getContent()).extracting(LoanResponse::id)
                .contains(newer.getId(), older.getId());
        int newerIndex = indexOfLoanId(page.getContent(), newer.getId());
        int olderIndex = indexOfLoanId(page.getContent(), older.getId());
        assertThat(newerIndex).isLessThan(olderIndex);
    }

    @Test
    void listLoansExposesBothBorrowersLoansNotJustOne() {
        authenticateWithLoanRead();
        AppUser borrowerOne = persistUser("service-staff-multi-1@primatis.test");
        AppUser borrowerTwo = persistUser("service-staff-multi-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "SERVICE-STAFF-MULTI-1");
        Copy copyTwo = persistCopy(title, "SERVICE-STAFF-MULTI-2");
        Loan loanOne = persistLoan(borrowerOne, copyOne, LoanStatus.ACTIVE, Instant.now());
        Loan loanTwo = persistLoan(borrowerTwo, copyTwo, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listLoans(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(LoanResponse::id)
                .contains(loanOne.getId(), loanTwo.getId());
    }

    // ---------------------------------------------------------------
    // listOwnLoans — self, ownership structurelle
    // ---------------------------------------------------------------

    /**
     * Aucun {@code @PreAuthorize} sur {@code listOwnLoans} : fonctionne même
     * sans authentification RBAC (anonyme), l'ownership reposant uniquement
     * sur le {@code selfUserId} transmis par le Controller.
     */
    @Test
    void listOwnLoansWorksWithoutLoanReadPermission() {
        authenticateAsAnonymous();
        AppUser self = persistUser("service-self-no-permission@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-SELF-NO-PERM");
        Loan loan = persistLoan(self, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listOwnLoans(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(LoanResponse::id).containsExactly(loan.getId());
    }

    @Test
    void listOwnLoansIsolatesLoansBetweenUsers() {
        AppUser self = persistUser("service-self-isolation-1@primatis.test");
        AppUser other = persistUser("service-self-isolation-2@primatis.test");
        Title title = persistTitle();
        Copy selfCopy = persistCopy(title, "SERVICE-SELF-ISOLATION-1");
        Copy otherCopy = persistCopy(title, "SERVICE-SELF-ISOLATION-2");
        Loan selfLoan = persistLoan(self, selfCopy, LoanStatus.ACTIVE, Instant.now());
        persistLoan(other, otherCopy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listOwnLoans(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(LoanResponse::id).containsExactly(selfLoan.getId());
        assertThat(page.getContent()).allSatisfy(
                response -> assertThat(response.borrower().id()).isEqualTo(self.getId()));
    }

    @Test
    void listOwnLoansReturnsEmptyPageRatherThanErrorWhenNoLoan() {
        AppUser self = persistUser("service-self-empty@primatis.test");
        entityManager.flush();

        Page<LoanResponse> page = loanService.listOwnLoans(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ---------------------------------------------------------------
    // Confirmation structurelle N+1 (DEV-07.3 a identifié le risque
    // LoanResponse.from → loan.getUser()/loan.getCopy()/copy.getTitle()).
    // Ni listLoans ni listOwnLoans n'introduisent de Repository/requête
    // supplémentaire pour compenser : mapping direct sur le résultat de la
    // requête paginée, borné par la pagination (contrat DEV-07.4).
    // ---------------------------------------------------------------

    @Test
    void listLoansMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/loan/LoanService.java",
                "public Page<LoanResponse> listLoans(Pageable pageable) {");

        assertThat(methodBody)
                .as("listLoans se limite à loanRepository.findAll : aucune Repository additionnelle introduite")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("fineRepository")
                .doesNotContain("reservationRepository")
                .contains("loanRepository.findAll(pageable)");
    }

    @Test
    void listOwnLoansMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/loan/LoanService.java",
                "public Page<LoanResponse> listOwnLoans(Long selfUserId, Pageable pageable) {");

        assertThat(methodBody)
                .as("listOwnLoans se limite à loanRepository.findByUserId : aucune Repository additionnelle")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("fineRepository")
                .doesNotContain("reservationRepository")
                .contains("loanRepository.findByUserId(selfUserId, pageable)");
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

    private static int indexOfLoanId(List<LoanResponse> content, Long loanId) {
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).id().equals(loanId)) {
                return i;
            }
        }
        throw new IllegalStateException("Loan " + loanId + " absent du contenu de la page.");
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

    private Loan persistLoan(AppUser user, Copy copy, LoanStatus status, Instant loanDate) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(loanDate);
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }
}
