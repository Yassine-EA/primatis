package be.primatis.reservation.dto;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
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
 * Vérifie les mappings {@code Entity → DTO} du domaine Reservation
 * (DEV-08.3) : {@link ReservationResponse#from},
 * {@link ReservationMemberResponse#from}, {@link ReservationTitleResponse#from},
 * {@link ReservationCopyResponse#from}. Tests unitaires purs — aucun
 * Spring, aucun PostgreSQL, aucune dépendance à
 * {@code ReservationRepositoryTests} (DEV-08.2). Même précédent exact que
 * {@code loan.dto.LoanDtoTests} (DEV-07.3) : constructeur statique
 * {@code from(...)} directement sur chaque Response record, pas de mapper
 * dédié.
 */
class ReservationDtoTests {

    // ---------------------------------------------------------------
    // ReservationResponse — mapping par statut
    // ---------------------------------------------------------------

    @Test
    void reservationResponseMapsAWaitingReservationWithoutAssignedCopyOrExpirationOrFulfillment() {
        Reservation reservation = baseReservation(ReservationStatus.WAITING);

        ReservationResponse response = ReservationResponse.from(reservation);

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(response.assignedCopy()).isNull();
        assertThat(response.expirationDate()).isNull();
        assertThat(response.fulfilledByLoanId()).isNull();
    }

    @Test
    void reservationResponseMapsAReadyReservationWithAssignedCopyAndExpirationButNoFulfillment() {
        Reservation reservation = baseReservation(ReservationStatus.READY);
        Copy copy = baseCopy(reservation.getTitle(), "READY-COPY-1", AvailabilityStatus.RESERVED);
        Instant expirationDate = Instant.parse("2026-08-21T10:00:00Z");
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(expirationDate);

        ReservationResponse response = ReservationResponse.from(reservation);

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(response.assignedCopy()).isNotNull();
        assertThat(response.assignedCopy().inventoryCode()).isEqualTo("READY-COPY-1");
        assertThat(response.assignedCopy().titleId()).isEqualTo(reservation.getTitle().getId());
        assertThat(response.expirationDate()).isEqualTo(expirationDate);
        assertThat(response.fulfilledByLoanId()).isNull();
    }

    @Test
    void reservationResponseMapsAFulfilledReservationPreservingAssignedCopyAndExpirationAsIs() {
        // DEV-08.1 §8 : LoanService.registerLoan ne réinitialise jamais
        // assignedCopy/expirationDate à null lors du passage READY→FULFILLED
        // — le mapper ne doit jamais réinterpréter silencieusement cet état.
        Reservation reservation = baseReservation(ReservationStatus.FULFILLED);
        Copy copy = baseCopy(reservation.getTitle(), "FULFILLED-COPY-1", AvailabilityStatus.ON_LOAN);
        Instant expirationDate = Instant.parse("2026-08-21T10:00:00Z");
        Loan loan = baseLoan(reservation.getUser(), copy);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(expirationDate);
        reservation.setFulfilledByLoan(loan);

        ReservationResponse response = ReservationResponse.from(reservation);

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(response.assignedCopy()).isNotNull();
        assertThat(response.assignedCopy().inventoryCode()).isEqualTo("FULFILLED-COPY-1");
        assertThat(response.expirationDate()).isEqualTo(expirationDate);
        // Loan.id est généré par séquence DB (aucun setter, par conception) :
        // en test unitaire pur sans persistance, loan.getId() reste null —
        // l'assertion vérifie que fulfilledByLoanId provient bien de
        // loan.getId(), même précédent que LoanDtoTests.loanBorrowerResponseMapsIdentificationFieldsOnly.
        assertThat(response.fulfilledByLoanId()).isEqualTo(loan.getId());
    }

    @Test
    void reservationResponseMapsBaseDataAndTimestamps() {
        Instant reservationDate = Instant.parse("2026-08-15T09:00:00Z");
        Instant createdAt = Instant.parse("2026-08-15T09:00:01Z");
        Instant updatedAt = Instant.parse("2026-08-15T09:00:02Z");
        Reservation reservation = baseReservation(ReservationStatus.WAITING);
        reservation.setReservationDate(reservationDate);
        reservation.setCreatedAt(createdAt);
        reservation.setUpdatedAt(updatedAt);

        ReservationResponse response = ReservationResponse.from(reservation);

        assertThat(response.id()).isEqualTo(reservation.getId());
        assertThat(response.member().memberNumber()).isEqualTo("M000012345");
        assertThat(response.title().title()).isEqualTo("Titre de test");
        assertThat(response.reservationDate()).isEqualTo(reservationDate);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void reservationResponseFromNullReservationThrowsExplicitly() {
        assertThatThrownBy(() -> ReservationResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------
    // ReservationMemberResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void reservationMemberResponseMapsIdentificationFieldsOnly() {
        AppUser user = baseUser("member@primatis.test", "M000099999");

        ReservationMemberResponse response = ReservationMemberResponse.from(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.memberNumber()).isEqualTo("M000099999");
        assertThat(response.firstName()).isEqualTo("Prénom");
        assertThat(response.lastName()).isEqualTo("Nom");
    }

    @Test
    void reservationMemberResponseFromNullAppUserThrowsExplicitly() {
        assertThatThrownBy(() -> ReservationMemberResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void reservationMemberResponseNeverExposesEmailOrMembershipStatusData() {
        Set<String> forbidden = Set.of(
                "email", "phonenumber", "accountstatus",
                "memberstatus", "registrationdate", "memberexpirationdate", "blockedreason");

        assertThat(componentNamesLowercase(ReservationMemberResponse.class))
                .as("ReservationMemberResponse doit rester un résumé d'identification, jamais un profil complet")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // ReservationTitleResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void reservationTitleResponseMapsIdentificationFieldsOnly() {
        Title title = baseTitle();
        title.setIsbn("9780000000001");

        ReservationTitleResponse response = ReservationTitleResponse.from(title);

        assertThat(response.id()).isEqualTo(title.getId());
        assertThat(response.title()).isEqualTo("Titre de test");
        assertThat(response.isbn()).isEqualTo("9780000000001");
    }

    @Test
    void reservationTitleResponsePreservesNullIsbn() {
        Title title = baseTitle();

        ReservationTitleResponse response = ReservationTitleResponse.from(title);

        assertThat(response.isbn()).isNull();
    }

    @Test
    void reservationTitleResponseFromNullTitleThrowsExplicitly() {
        assertThatThrownBy(() -> ReservationTitleResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void reservationTitleResponseNeverExposesCatalogueDetailFields() {
        Set<String> forbidden = Set.of(
                "subtitle", "summary", "publicationyear", "language",
                "pagecount", "publisher", "coverimageurl", "titlestatus");

        assertThat(componentNamesLowercase(ReservationTitleResponse.class))
                .as("ReservationTitleResponse reste un résumé compact, distinct de TitleResponse (catalogue)")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // ReservationCopyResponse — résumé compact
    // ---------------------------------------------------------------

    @Test
    void reservationCopyResponseMapsInventoryCodeAndTitleId() {
        Title title = baseTitle();
        Copy copy = baseCopy(title, "INV-000456", AvailabilityStatus.RESERVED);

        ReservationCopyResponse response = ReservationCopyResponse.from(copy);

        assertThat(response.id()).isEqualTo(copy.getId());
        assertThat(response.inventoryCode()).isEqualTo("INV-000456");
        assertThat(response.titleId()).isEqualTo(title.getId());
    }

    @Test
    void reservationCopyResponseFromNullCopyThrowsExplicitly() {
        assertThatThrownBy(() -> ReservationCopyResponse.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void reservationCopyResponseNeverExposesConditionOrAvailability() {
        Set<String> forbidden = Set.of("location", "copycondition", "availabilitystatus");

        assertThat(componentNamesLowercase(ReservationCopyResponse.class))
                .as("ReservationCopyResponse reste un résumé compact, distinct de CopyResponse (staff)")
                .noneMatch(forbidden::contains);
    }

    // ---------------------------------------------------------------
    // Anti-fuite structurelle
    // ---------------------------------------------------------------

    @Test
    void noReservationDtoComponentExposesAnEntityDirectlyOrThroughAGenericType() {
        Set<Class<?>> forbiddenEntityTypes =
                Set.of(Reservation.class, AppUser.class, Copy.class, Title.class, Loan.class);
        List<Class<?>> reservationDtos = List.of(
                ReservationResponse.class,
                ReservationMemberResponse.class,
                ReservationTitleResponse.class,
                ReservationCopyResponse.class);

        for (Class<?> dto : reservationDtos) {
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
    void reservationResponseNeverExposesArtificialComputedFlags() {
        // Mission DEV-08.3 §5 : aucun indicateur artificiel isExpired/
        // isCancelable/canFulfill calculé dans le mapper, aucune donnée
        // Fine/Notification, aucune queuePosition (elle n'existe pas).
        Set<String> forbidden = Set.of(
                "isexpired", "iscancelable", "canfulfill", "queueposition",
                "maxactivereservations", "eligibility", "fine", "notification");

        assertThat(componentNamesLowercase(ReservationResponse.class))
                .as("ReservationResponse reflète l'état persistant, jamais une décision UI recalculée")
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

    private static Copy baseCopy(Title title, String inventoryCode, AvailabilityStatus availabilityStatus) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(availabilityStatus);
        return copy;
    }

    private static Loan baseLoan(AppUser user, Copy copy) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(Instant.parse("2026-08-21T09:00:00Z"));
        loan.setDueDate(LocalDate.of(2026, 9, 11));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(Instant.parse("2026-08-21T09:00:00Z"));
        loan.setUpdatedAt(Instant.parse("2026-08-21T09:00:00Z"));
        return loan;
    }

    private static Reservation baseReservation(ReservationStatus status) {
        AppUser user = baseUser("reservation-dto@primatis.test", "M000012345");
        Title title = baseTitle();

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(Instant.parse("2026-08-15T09:00:00Z"));
        reservation.setReservationStatus(status);
        reservation.setCreatedAt(Instant.parse("2026-08-15T09:00:00Z"));
        reservation.setUpdatedAt(Instant.parse("2026-08-15T09:00:00Z"));
        return reservation;
    }
}
