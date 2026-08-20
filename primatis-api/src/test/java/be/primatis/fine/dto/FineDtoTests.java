package be.primatis.fine.dto;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie les mappings {@code Entity → DTO} du domaine Fine (DEV-09.5) :
 * {@link FineResponse#from}, {@link FineBorrowerResponse#from},
 * {@link FineLoanResponse#from}, {@link FineCopyResponse#from}. Tests
 * unitaires purs — aucun Spring, aucun PostgreSQL, aucune dépendance à
 * {@code FineRepositoryTests} (DEV-09.4). Même précédent exact que
 * {@code loan.dto.LoanDtoTests}/{@code reservation.dto.ReservationDtoTests}
 * : constructeur statique {@code from(...)} directement sur chaque Response
 * record, pas de mapper dédié. Aucune logique métier testée ici (pas de
 * calcul {@code overdueDays}/{@code startedWeeks}/{@code amount}, pas de
 * transition de statut) — uniquement la représentation de l'état déjà
 * persisté.
 */
class FineDtoTests {

    // ---------------------------------------------------------------
    // FineResponse — mapping par statut
    // ---------------------------------------------------------------

    @Test
    void fineResponseMapsAnUnpaidFineWithoutPaidAtOrCancelledAt() {
        Fine fine = baseFine(FineStatus.UNPAID);

        FineResponse response = FineResponse.from(fine);

        assertThat(response.fineStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(response.paidAt()).isNull();
        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void fineResponseMapsAPaidFineWithPaidAtOnly() {
        Fine fine = baseFine(FineStatus.PAID);
        Instant paidAt = Instant.parse("2026-08-22T10:00:00Z");
        fine.setPaidAt(paidAt);

        FineResponse response = FineResponse.from(fine);

        assertThat(response.fineStatus()).isEqualTo(FineStatus.PAID);
        assertThat(response.paidAt()).isEqualTo(paidAt);
        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void fineResponseMapsACancelledFineWithCancelledAtOnly() {
        Fine fine = baseFine(FineStatus.CANCELLED);
        Instant cancelledAt = Instant.parse("2026-08-22T10:00:00Z");
        fine.setCancelledAt(cancelledAt);

        FineResponse response = FineResponse.from(fine);

        assertThat(response.fineStatus()).isEqualTo(FineStatus.CANCELLED);
        assertThat(response.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(response.paidAt()).isNull();
    }

    @Test
    void fineResponsePreservesTheExactBigDecimalAmount() {
        Fine fine = baseFine(FineStatus.UNPAID);
        fine.setAmount(new BigDecimal("3.20"));

        FineResponse response = FineResponse.from(fine);

        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("3.20"));
    }

    @Test
    void fineResponseMapsBaseDataAndReason() {
        Instant issuedAt = Instant.parse("2026-08-21T09:00:00Z");
        Fine fine = baseFine(FineStatus.UNPAID);
        fine.setReason("Retour tardif — 5 jour(s) de retard, 1 semaine(s) entamée(s).");
        fine.setIssuedAt(issuedAt);

        FineResponse response = FineResponse.from(fine);

        assertThat(response.id()).isEqualTo(fine.getId());
        assertThat(response.reason()).isEqualTo("Retour tardif — 5 jour(s) de retard, 1 semaine(s) entamée(s).");
        assertThat(response.issuedAt()).isEqualTo(issuedAt);
        assertThat(response.borrower().memberNumber()).isEqualTo("M000012345");
        assertThat(response.loan().dueDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void fineResponseIdentifiesTheCorrectLoanAndCopy() {
        Fine fine = baseFine(FineStatus.UNPAID);
        Loan loan = fine.getLoan();

        FineResponse response = FineResponse.from(fine);

        assertThat(response.loan().id()).isEqualTo(loan.getId());
        assertThat(response.loan().loanDate()).isEqualTo(loan.getLoanDate());
        assertThat(response.loan().dueDate()).isEqualTo(loan.getDueDate());
        assertThat(response.loan().returnDate()).isEqualTo(loan.getReturnDate());
        assertThat(response.loan().copy().inventoryCode()).isEqualTo("FINE-DTO-COPY");
        assertThat(response.loan().copy().titleId()).isEqualTo(loan.getCopy().getTitle().getId());
    }

    @Test
    void fineResponseFromNullFineThrowsExplicitly() {
        assertThatThrownBy(() -> FineResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // FineBorrowerResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void fineBorrowerResponseMapsIdentificationFieldsOnly() {
        AppUser user = baseUser("borrower@primatis.test", "M000099999");

        FineBorrowerResponse response = FineBorrowerResponse.from(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.memberNumber()).isEqualTo("M000099999");
        assertThat(response.firstName()).isEqualTo("Prénom");
        assertThat(response.lastName()).isEqualTo("Nom");
    }

    @Test
    void fineBorrowerResponseFromNullAppUserThrowsExplicitly() {
        assertThatThrownBy(() -> FineBorrowerResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fineBorrowerResponseNeverExposesEmailOrMembershipStatusData() {
        Set<String> forbidden = Set.of(
                "email", "phonenumber", "accountstatus",
                "memberstatus", "registrationdate", "memberexpirationdate", "blockedreason");

        assertThat(componentNamesLowercase(FineBorrowerResponse.class))
                .as("FineBorrowerResponse doit rester un résumé d'identification, jamais un profil complet")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // FineLoanResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void fineLoanResponseMapsIdentificationDatesAndCopyOnly() {
        Loan loan = baseLoan(baseUser("loan-dto@primatis.test", "M000012345"), baseTitle());

        FineLoanResponse response = FineLoanResponse.from(loan);

        assertThat(response.id()).isEqualTo(loan.getId());
        assertThat(response.loanDate()).isEqualTo(loan.getLoanDate());
        assertThat(response.dueDate()).isEqualTo(loan.getDueDate());
        assertThat(response.returnDate()).isEqualTo(loan.getReturnDate());
        assertThat(response.copy().inventoryCode()).isEqualTo(loan.getCopy().getInventoryCode());
    }

    @Test
    void fineLoanResponsePreservesNullReturnDateForAnOpenLoan() {
        Loan loan = baseLoan(baseUser("loan-open@primatis.test", "M000012345"), baseTitle());
        loan.setReturnDate(null);

        FineLoanResponse response = FineLoanResponse.from(loan);

        assertThat(response.returnDate()).isNull();
    }

    @Test
    void fineLoanResponseFromNullLoanThrowsExplicitly() {
        assertThatThrownBy(() -> FineLoanResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fineLoanResponseNeverExposesStatusOrNotesOrTimestamps() {
        Set<String> forbidden = Set.of("loanstatus", "notes", "createdat", "updatedat");

        assertThat(componentNamesLowercase(FineLoanResponse.class))
                .as("FineLoanResponse reste un résumé compact, distinct de LoanResponse (consultation Loan)")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // FineCopyResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void fineCopyResponseMapsInventoryCodeAndTitleId() {
        Title title = baseTitle();
        Copy copy = baseCopy(title, "INV-000789");

        FineCopyResponse response = FineCopyResponse.from(copy);

        assertThat(response.id()).isEqualTo(copy.getId());
        assertThat(response.inventoryCode()).isEqualTo("INV-000789");
        assertThat(response.titleId()).isEqualTo(title.getId());
    }

    @Test
    void fineCopyResponseFromNullCopyThrowsExplicitly() {
        assertThatThrownBy(() -> FineCopyResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fineCopyResponseNeverExposesConditionOrAvailability() {
        Set<String> forbidden = Set.of("location", "copycondition", "availabilitystatus");

        assertThat(componentNamesLowercase(FineCopyResponse.class))
                .as("FineCopyResponse reste un résumé compact, distinct de CopyResponse (staff)")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // Anti-fuite structurelle
    // ---------------------------------------------------------------

    @Test
    void noFineDtoComponentExposesAnEntityDirectlyOrThroughAGenericType() {
        Set<Class<?>> forbiddenEntityTypes = Set.of(Fine.class, Loan.class, AppUser.class, Copy.class, Title.class);
        List<Class<?>> fineDtos = List.of(
                FineResponse.class,
                FineBorrowerResponse.class,
                FineLoanResponse.class,
                FineCopyResponse.class);

        for (Class<?> dto : fineDtos) {
            for (RecordComponent component : dto.getRecordComponents()) {
                assertThat(forbiddenEntityTypes)
                        .as("%s.%s ne doit pas exposer une Entity directement", dto.getSimpleName(), component.getName())
                        .doesNotContain(component.getType());

                for (Class<?> typeArgument : genericTypeArgumentsOf(component)) {
                    assertThat(forbiddenEntityTypes)
                            .as("%s.%s ne doit pas exposer une Entity via un type générique",
                                    dto.getSimpleName(), component.getName())
                            .doesNotContain(typeArgument);
                }
            }
        }
    }

    @Test
    void fineResponseNeverExposesArtificialComputedFlags() {
        // DEV-09.5 §11 : aucune logique métier ici — pas de isOverdue/
        // isPayable/daysLate/overdueDays/startedWeeks calculé dans le
        // mapper, aucune donnée Notification (différée DEV-10).
        Set<String> forbidden = Set.of(
                "isoverdue", "ispayable", "iscancelable", "dayslate",
                "overduedays", "startedweeks", "notification");

        assertThat(componentNamesLowercase(FineResponse.class))
                .as("FineResponse reflète l'état persistant, jamais une décision UI recalculée")
                .noneMatch(forbidden::contains);
    }

    private static List<Class<?>> genericTypeArgumentsOf(RecordComponent component) {
        Type genericType = component.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return List.of();
        }
        return Arrays.stream(parameterizedType.getActualTypeArguments())
                .filter(Class.class::isInstance)
                .<Class<?>>map(typeArgument -> (Class<?>) typeArgument)
                .toList();
    }

    private static Set<String> componentNamesLowercase(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private static AppUser baseUser(String email, String memberNumber) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setMemberNumber(memberNumber);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        return user;
    }

    private static Title baseTitle() {
        Title title = new Title();
        title.setTitle("Titre de test");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        return title;
    }

    private static Copy baseCopy(Title title, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return copy;
    }

    private static Loan baseLoan(AppUser user, Title title) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(baseCopy(title, "FINE-DTO-COPY"));
        loan.setLoanDate(Instant.parse("2026-07-25T09:00:00Z"));
        loan.setDueDate(LocalDate.of(2026, 8, 15));
        loan.setReturnDate(LocalDate.of(2026, 8, 20));
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.parse("2026-07-25T09:00:00Z"));
        loan.setUpdatedAt(Instant.parse("2026-08-20T09:00:00Z"));
        return loan;
    }

    private static Fine baseFine(FineStatus status) {
        AppUser user = baseUser("fine-dto@primatis.test", "M000012345");
        Title title = baseTitle();
        Loan loan = baseLoan(user, title);

        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.valueOf(5.00));
        fine.setReason("Retard de test");
        fine.setIssuedAt(Instant.parse("2026-08-21T09:00:00Z"));
        fine.setFineStatus(status);
        return fine;
    }
}
