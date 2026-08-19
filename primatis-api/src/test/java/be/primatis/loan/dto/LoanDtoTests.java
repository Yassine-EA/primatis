package be.primatis.loan.dto;

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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie les mappings {@code Entity → DTO} et la validation structurelle
 * du domaine Loan (DEV-07.3) : {@link LoanResponse#from},
 * {@link LoanBorrowerResponse#from}, {@link LoanCopyResponse#from},
 * {@link CreateLoanRequest}. Tests unitaires purs — aucun Spring, aucun
 * PostgreSQL, aucune dépendance à {@code LoanRepositoryTests}/
 * {@code FineRepositoryTests} (DEV-07.2). Même précédent que
 * {@code CatalogueDtoTests} (DEV-06.3) : constructeur statique
 * {@code from(...)} directement sur chaque Response record, pas de mapper
 * dédié.
 */
class LoanDtoTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    // ---------------------------------------------------------------
    // LoanResponse — mapping par statut
    // ---------------------------------------------------------------

    @Test
    void loanResponseMapsAnActiveLoanWithoutReturnDate() {
        Loan loan = baseLoan(LoanStatus.ACTIVE, null);

        LoanResponse response = LoanResponse.from(loan);

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(response.returnDate()).isNull();
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void loanResponseMapsAnOverdueLoanWithoutReturnDate() {
        Loan loan = baseLoan(LoanStatus.OVERDUE, null);

        LoanResponse response = LoanResponse.from(loan);

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.OVERDUE);
        assertThat(response.returnDate()).isNull();
    }

    @Test
    void loanResponseMapsAReturnedLoanWithReturnDate() {
        Loan loan = baseLoan(LoanStatus.RETURNED, LocalDate.of(2026, 8, 20));

        LoanResponse response = LoanResponse.from(loan);

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(response.returnDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void loanResponseMapsTimestampsAndNotes() {
        Instant loanDate = Instant.parse("2026-08-01T10:00:00Z");
        Instant createdAt = Instant.parse("2026-08-01T10:00:01Z");
        Instant updatedAt = Instant.parse("2026-08-01T10:00:02Z");
        Loan loan = baseLoan(LoanStatus.ACTIVE, null);
        loan.setLoanDate(loanDate);
        loan.setCreatedAt(createdAt);
        loan.setUpdatedAt(updatedAt);
        loan.setNotes("Prêt exceptionnel");

        LoanResponse response = LoanResponse.from(loan);

        assertThat(response.loanDate()).isEqualTo(loanDate);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.notes()).isEqualTo("Prêt exceptionnel");
    }

    @Test
    void loanResponsePreservesNullNotes() {
        Loan loan = baseLoan(LoanStatus.ACTIVE, null);

        LoanResponse response = LoanResponse.from(loan);

        assertThat(response.notes()).isNull();
    }

    @Test
    void loanResponseFromNullLoanThrowsExplicitly() {
        assertThatThrownBy(() -> LoanResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // LoanBorrowerResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void loanBorrowerResponseMapsIdentificationFieldsOnly() {
        AppUser user = new AppUser();
        user.setEmail("borrower@primatis.test");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setMemberNumber("M000012345");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);

        LoanBorrowerResponse response = LoanBorrowerResponse.from(user);

        // AppUser.id est généré par séquence DB (aucun setter exposé, par
        // conception) : en test unitaire pur sans persistance, user.getId()
        // reste null — l'assertion vérifie que l'id provient bien de
        // user.getId() (même précédent que CatalogueDtoTests.copyResponseMapsAllFields).
        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.memberNumber()).isEqualTo("M000012345");
        assertThat(response.firstName()).isEqualTo("Prénom");
        assertThat(response.lastName()).isEqualTo("Nom");
    }

    @Test
    void loanBorrowerResponsePreservesNullMemberNumber() {
        AppUser user = new AppUser();
        user.setEmail("non-member-borrower@primatis.test");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);

        LoanBorrowerResponse response = LoanBorrowerResponse.from(user);

        assertThat(response.memberNumber()).isNull();
    }

    @Test
    void loanBorrowerResponseFromNullAppUserThrowsExplicitly() {
        assertThatThrownBy(() -> LoanBorrowerResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void loanBorrowerResponseNeverExposesEmailOrMembershipStatusData() {
        Set<String> forbidden = Set.of(
                "email", "phonenumber", "accountstatus",
                "memberstatus", "registrationdate", "memberexpirationdate", "blockedreason");

        assertThat(componentNamesLowercase(LoanBorrowerResponse.class))
                .as("LoanBorrowerResponse doit rester un résumé d'identification, jamais un profil complet")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // LoanCopyResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void loanCopyResponseMapsInventoryCodeAndTitleId() {
        Title title = baseTitle();
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode("INV-000456");
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);

        LoanCopyResponse response = LoanCopyResponse.from(copy);

        assertThat(response.id()).isEqualTo(copy.getId());
        assertThat(response.inventoryCode()).isEqualTo("INV-000456");
        assertThat(response.titleId()).isEqualTo(title.getId());
    }

    @Test
    void loanCopyResponseFromNullCopyThrowsExplicitly() {
        assertThatThrownBy(() -> LoanCopyResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void loanCopyResponseNeverExposesConditionOrAvailability() {
        Set<String> forbidden = Set.of("location", "copycondition", "availabilitystatus");

        assertThat(componentNamesLowercase(LoanCopyResponse.class))
                .as("LoanCopyResponse reste un résumé compact, distinct de CopyResponse (staff)")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // Anti-fuite structurelle
    // ---------------------------------------------------------------

    @Test
    void noLoanDtoComponentExposesAnEntityDirectlyOrThroughAGenericType() {
        Set<Class<?>> forbiddenEntityTypes = Set.of(Loan.class, AppUser.class, Copy.class, Title.class);
        List<Class<?>> loanDtos = List.of(LoanResponse.class, LoanBorrowerResponse.class, LoanCopyResponse.class);

        for (Class<?> dto : loanDtos) {
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
    // CreateLoanRequest — validation structurelle
    // ---------------------------------------------------------------

    @Test
    void createLoanRequestAcceptsTwoPositiveIds() {
        CreateLoanRequest request = new CreateLoanRequest(1L, 2L);

        Set<ConstraintViolation<CreateLoanRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void createLoanRequestRejectsNullBorrowerUserId() {
        CreateLoanRequest request = new CreateLoanRequest(null, 2L);

        Set<ConstraintViolation<CreateLoanRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("borrowerUserId");
    }

    @Test
    void createLoanRequestRejectsNullCopyId() {
        CreateLoanRequest request = new CreateLoanRequest(1L, null);

        Set<ConstraintViolation<CreateLoanRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("copyId");
    }

    @Test
    void createLoanRequestRejectsZeroOrNegativeBorrowerUserId() {
        assertThat(validator.validate(new CreateLoanRequest(0L, 2L))).isNotEmpty();
        assertThat(validator.validate(new CreateLoanRequest(-1L, 2L))).isNotEmpty();
    }

    @Test
    void createLoanRequestRejectsZeroOrNegativeCopyId() {
        assertThat(validator.validate(new CreateLoanRequest(1L, 0L))).isNotEmpty();
        assertThat(validator.validate(new CreateLoanRequest(1L, -1L))).isNotEmpty();
    }

    @Test
    void createLoanRequestNeverValidatesBusinessRules() {
        // Confirme structurellement qu'aucun champ métier (MemberStatus,
        // AvailabilityStatus, Fine, Reservation, quota de 5 emprunts) n'a
        // été ajouté à ce contrat — seuls borrowerUserId/copyId existent.
        assertThat(componentNamesLowercase(CreateLoanRequest.class))
                .containsExactlyInAnyOrder("borroweruserid", "copyid");
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private static Title baseTitle() {
        Title title = new Title();
        title.setTitle("Titre de test");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        return title;
    }

    private static Loan baseLoan(LoanStatus status, LocalDate returnDate) {
        AppUser user = new AppUser();
        user.setEmail("loan-dto@primatis.test");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);

        Copy copy = new Copy();
        copy.setTitle(baseTitle());
        copy.setInventoryCode("INV-000789");
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(Instant.parse("2026-08-01T10:00:00Z"));
        loan.setDueDate(LocalDate.of(2026, 9, 1));
        loan.setReturnDate(returnDate);
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.parse("2026-08-01T10:00:00Z"));
        loan.setUpdatedAt(Instant.parse("2026-08-01T10:00:00Z"));
        return loan;
    }
}
