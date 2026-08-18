package be.primatis.catalogue;

import be.primatis.catalogue.dto.CopyResponse;
import be.primatis.catalogue.dto.CreateCopyRequest;
import be.primatis.catalogue.dto.UpdateCopyAvailabilityRequest;
import be.primatis.catalogue.dto.UpdateCopyRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie {@link CopyService} (DEV-06.6) contre PostgreSQL réel : lecture
 * ({@code COPY_READ}) et gestion ({@code COPY_MANAGE}) des exemplaires,
 * invariants physiques {@code CopyCondition}/{@code AvailabilityStatus},
 * interdiction absolue d'écriture directe {@code ON_LOAN}/{@code RESERVED}.
 * Sécurité simulée via {@code SecurityContextHolder}, même distinction
 * qu'introduite au gate PostgreSQL réel #2 (DEV-06.5) : absence
 * d'Authentication → {@code AuthenticationCredentialsNotFoundException} ;
 * Authentication sans permission → {@code AccessDeniedException}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CopyServiceTests {

    @Autowired
    private CopyService copyService;

    @Autowired
    private CopyRepository copyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithCopyRead() {
        authenticate("COPY_READ");
    }

    private static void authenticateWithCopyManage() {
        authenticate("COPY_MANAGE");
    }

    private static void authenticateWithoutCopyPermissions() {
        authenticate("ROLE_MEMBER");
    }

    private static void authenticate(String authority) {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority(authority));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ---------------------------------------------------------------
    // listCopiesByTitle
    // ---------------------------------------------------------------

    @Test
    void listCopiesWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> copyService.listCopiesByTitle(-1L));
    }

    @Test
    void listCopiesWithoutCopyReadIsDenied() {
        authenticateWithoutCopyPermissions();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> copyService.listCopiesByTitle(-1L));
    }

    @Test
    void listCopiesForNonExistentTitleThrowsTitleNotFound() {
        authenticateWithCopyRead();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> copyService.listCopiesByTitle(-1L))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND"));
    }

    @Test
    void listCopiesReturnsEmptyListWhenTitleHasNoCopies() {
        authenticateWithCopyRead();
        Title title = persistTitle("List Copies Empty CRT");
        entityManager.flush();

        assertThat(copyService.listCopiesByTitle(title.getId())).isEmpty();
    }

    @Test
    void listCopiesReturnsCopiesOrderedByInventoryCode() {
        authenticateWithCopyRead();
        Title title = persistTitle("List Copies Ordered CRT");
        persistCopy(title, "CRT-LIST-002", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        persistCopy(title, "CRT-LIST-001", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        List<CopyResponse> copies = copyService.listCopiesByTitle(title.getId());

        assertThat(copies).extracting(CopyResponse::inventoryCode).containsExactly("CRT-LIST-001", "CRT-LIST-002");
    }

    // ---------------------------------------------------------------
    // getCopyById
    // ---------------------------------------------------------------

    @Test
    void getCopyByIdWithoutAuthenticationIsDenied() {
        assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
                .isThrownBy(() -> copyService.getCopyById(-1L, -1L));
    }

    @Test
    void getCopyByIdWithoutCopyReadIsDenied() {
        authenticateWithoutCopyPermissions();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> copyService.getCopyById(-1L, -1L));
    }

    @Test
    void getCopyByIdReturnsNominalDetail() {
        authenticateWithCopyRead();
        Title title = persistTitle("Detail Nominal CRT");
        Copy copy = persistCopy(title, "CRT-DETAIL-NOMINAL-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.getCopyById(title.getId(), copy.getId());

        assertThat(response.id()).isEqualTo(copy.getId());
        assertThat(response.titleId()).isEqualTo(title.getId());
        assertThat(response.inventoryCode()).isEqualTo("CRT-DETAIL-NOMINAL-1");
    }

    @Test
    void getCopyByIdForNonExistentCopyThrowsCopyNotFound() {
        authenticateWithCopyRead();
        Title title = persistTitle("Detail Nonexistent Copy CRT");
        entityManager.flush();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> copyService.getCopyById(title.getId(), -1L))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_NOT_FOUND"));
    }

    @Test
    void getCopyByIdForCopyOfAnotherTitleThrowsCopyNotFound() {
        authenticateWithCopyRead();
        Title owningTitle = persistTitle("Detail Owning Title CRT");
        Title otherTitle = persistTitle("Detail Other Title CRT");
        Copy copy = persistCopy(owningTitle, "CRT-DETAIL-OTHER-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> copyService.getCopyById(otherTitle.getId(), copy.getId()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // createCopy
    // ---------------------------------------------------------------

    @Test
    void createCopyGoodAvailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Good Available CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-GOOD-AVAIL-1", null, CopyCondition.GOOD, AvailabilityStatus.AVAILABLE));

        assertThat(response.copyCondition()).isEqualTo(CopyCondition.GOOD);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void createCopyGoodUnavailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Good Unavailable CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-GOOD-UNAVAIL-1", null, CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void createCopyDamagedAvailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Damaged Available CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-DMG-AVAIL-1", null, CopyCondition.DAMAGED, AvailabilityStatus.AVAILABLE));

        assertThat(response.copyCondition()).isEqualTo(CopyCondition.DAMAGED);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void createCopyDamagedUnavailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Damaged Unavailable CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-DMG-UNAVAIL-1", null, CopyCondition.DAMAGED, AvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void createCopyLostUnavailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Lost Unavailable CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-LOST-UNAVAIL-1", null, CopyCondition.LOST, AvailabilityStatus.UNAVAILABLE));

        assertThat(response.copyCondition()).isEqualTo(CopyCondition.LOST);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void createCopyOutOfServiceUnavailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Oos Unavailable CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-OOS-UNAVAIL-1", null, CopyCondition.OUT_OF_SERVICE, AvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void createCopyLostAvailableIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Lost Available Rejected CRT");
        entityManager.flush();

        CreateCopyRequest request = new CreateCopyRequest(
                "CRT-CREATE-LOST-AVAIL-1", null, CopyCondition.LOST, AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.createCopy(title.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_CONDITION_REQUIRES_UNAVAILABLE"));
    }

    @Test
    void createCopyOutOfServiceAvailableIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Oos Available Rejected CRT");
        entityManager.flush();

        CreateCopyRequest request = new CreateCopyRequest(
                "CRT-CREATE-OOS-AVAIL-1", null, CopyCondition.OUT_OF_SERVICE, AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.createCopy(title.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_CONDITION_REQUIRES_UNAVAILABLE"));
    }

    @Test
    void createCopyWithOnLoanIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create On Loan Rejected CRT");
        entityManager.flush();

        CreateCopyRequest request = new CreateCopyRequest(
                "CRT-CREATE-ON-LOAN-1", null, CopyCondition.GOOD, AvailabilityStatus.ON_LOAN);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.createCopy(title.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    @Test
    void createCopyWithReservedIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Reserved Rejected CRT");
        entityManager.flush();

        CreateCopyRequest request = new CreateCopyRequest(
                "CRT-CREATE-RESERVED-1", null, CopyCondition.GOOD, AvailabilityStatus.RESERVED);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.createCopy(title.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    @Test
    void createCopyWithDuplicateInventoryCodeIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Create Duplicate Inventory CRT");
        persistCopy(title, "CRT-CREATE-DUP-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        CreateCopyRequest request = new CreateCopyRequest(
                "CRT-CREATE-DUP-1", null, CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> copyService.createCopy(title.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("INVENTORY_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createCopyForNonExistentTitleThrowsTitleNotFound() {
        authenticateWithCopyManage();

        CreateCopyRequest request = new CreateCopyRequest(
                "CRT-CREATE-NO-TITLE-1", null, CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> copyService.createCopy(-1L, request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND"));
    }

    @Test
    void createCopyForWithdrawnTitleSucceeds() {
        authenticateWithCopyManage();
        Title title = persistWithdrawnTitle("Create Withdrawn Title Copy CRT");
        entityManager.flush();

        CopyResponse response = copyService.createCopy(title.getId(),
                new CreateCopyRequest("CRT-CREATE-WITHDRAWN-1", null, CopyCondition.GOOD, AvailabilityStatus.AVAILABLE));

        assertThat(response.titleId()).isEqualTo(title.getId());
    }

    // ---------------------------------------------------------------
    // updateCopy
    // ---------------------------------------------------------------

    @Test
    void updateCopyInventoryCodeSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Inventory Code CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-INV-BEFORE-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setInventoryCode("CRT-UPDATE-INV-AFTER-1");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.inventoryCode()).isEqualTo("CRT-UPDATE-INV-AFTER-1");
    }

    @Test
    void updateCopyWithDuplicateInventoryCodeIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Duplicate Inventory CRT");
        persistCopy(title, "CRT-UPDATE-DUP-TAKEN-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        Copy target = persistCopy(title, "CRT-UPDATE-DUP-OWN-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setInventoryCode("CRT-UPDATE-DUP-TAKEN-1");

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> copyService.updateCopy(title.getId(), target.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("INVENTORY_CODE_ALREADY_EXISTS"));
    }

    @Test
    void updateCopyResubmittingSameInventoryCodeIsNotTreatedAsConflict() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Same Inventory CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-SAME-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setInventoryCode("CRT-UPDATE-SAME-1");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.inventoryCode()).isEqualTo("CRT-UPDATE-SAME-1");
    }

    @Test
    void updateCopyLocationModifiedSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Location Modified CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-LOC-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setLocation("Rayon B3");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.location()).isEqualTo("Rayon B3");
    }

    @Test
    void updateCopyLocationExplicitNullClearsLocation() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Location Clear CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-LOC-CLEAR-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        copy.setLocation("Rayon A1");
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setLocation(null);

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.location()).isNull();
    }

    @Test
    void updateCopyGoodToLostForcesUnavailable() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Good To Lost CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-GOOD-LOST-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setCopyCondition(CopyCondition.LOST);

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.copyCondition()).isEqualTo(CopyCondition.LOST);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyDamagedToOutOfServiceForcesUnavailable() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Damaged To Oos CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-DMG-OOS-1", CopyCondition.DAMAGED, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setCopyCondition(CopyCondition.OUT_OF_SERVICE);

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyLostToGoodKeepsUnavailable() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Lost To Good CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-LOST-GOOD-1", CopyCondition.LOST, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setCopyCondition(CopyCondition.GOOD);

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.copyCondition()).isEqualTo(CopyCondition.GOOD);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyOutOfServiceToDamagedKeepsUnavailable() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Oos To Damaged CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-OOS-DMG-1", CopyCondition.OUT_OF_SERVICE, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setCopyCondition(CopyCondition.DAMAGED);

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyLocationOnlyPreservesAvailability() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Location Preserves Availability CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-LOC-PRESERVE-1", CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setLocation("Rayon C2");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyInventoryCodeOnlyPreservesAvailability() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Inventory Preserves Availability CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-INV-PRESERVE-BEFORE-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setInventoryCode("CRT-UPDATE-INV-PRESERVE-AFTER-1");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    /**
     * Un Copy {@code ON_LOAN} (état atteignable uniquement en base pour ce
     * test — DEV-06.6 n'écrit jamais cette valeur, §9/§29) modifié
     * uniquement sur {@code location} doit rester {@code ON_LOAN} : ce
     * endpoint ne doit jamais écraser un état géré par les futurs workflows
     * Loan/Reservation (§30).
     */
    @Test
    void updateCopyLocationDoesNotOverwriteExistingOnLoan() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Preserve On Loan CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-ON-LOAN-1", CopyCondition.GOOD, AvailabilityStatus.ON_LOAN);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setLocation("Rayon D4");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
    }

    @Test
    void updateCopyInventoryCodeDoesNotOverwriteExistingReserved() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Preserve Reserved CRT");
        Copy copy = persistCopy(title, "CRT-UPDATE-RESERVED-BEFORE-1", CopyCondition.GOOD, AvailabilityStatus.RESERVED);
        entityManager.flush();

        UpdateCopyRequest request = new UpdateCopyRequest();
        request.setInventoryCode("CRT-UPDATE-RESERVED-AFTER-1");

        CopyResponse response = copyService.updateCopy(title.getId(), copy.getId(), request);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
    }

    @Test
    void updateCopyForNonExistentCopyThrowsCopyNotFound() {
        authenticateWithCopyManage();
        Title title = persistTitle("Update Nonexistent Copy CRT");
        entityManager.flush();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> copyService.updateCopy(title.getId(), -1L, new UpdateCopyRequest()))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // updateCopyAvailability
    // ---------------------------------------------------------------

    @Test
    void updateCopyAvailabilityGoodUnavailableToAvailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Good Unavail To Avail CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-GOOD-1", CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.updateCopyAvailability(
                title.getId(), copy.getId(), new UpdateCopyAvailabilityRequest(AvailabilityStatus.AVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void updateCopyAvailabilityDamagedUnavailableToAvailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Damaged Unavail To Avail CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-DMG-1", CopyCondition.DAMAGED, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.updateCopyAvailability(
                title.getId(), copy.getId(), new UpdateCopyAvailabilityRequest(AvailabilityStatus.AVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void updateCopyAvailabilityGoodAvailableToUnavailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Good Avail To Unavail CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-GOOD-2", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.updateCopyAvailability(
                title.getId(), copy.getId(), new UpdateCopyAvailabilityRequest(AvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyAvailabilityDamagedAvailableToUnavailableSucceeds() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Damaged Avail To Unavail CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-DMG-2", CopyCondition.DAMAGED, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.updateCopyAvailability(
                title.getId(), copy.getId(), new UpdateCopyAvailabilityRequest(AvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyAvailabilityLostToAvailableIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Lost To Available Rejected CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-LOST-1", CopyCondition.LOST, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();
        UpdateCopyAvailabilityRequest request = new UpdateCopyAvailabilityRequest(AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.updateCopyAvailability(title.getId(), copy.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_CONDITION_REQUIRES_UNAVAILABLE"));
    }

    @Test
    void updateCopyAvailabilityOutOfServiceToAvailableIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Oos To Available Rejected CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-OOS-1", CopyCondition.OUT_OF_SERVICE, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();
        UpdateCopyAvailabilityRequest request = new UpdateCopyAvailabilityRequest(AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.updateCopyAvailability(title.getId(), copy.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_CONDITION_REQUIRES_UNAVAILABLE"));
    }

    @Test
    void updateCopyAvailabilityToOnLoanIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability To On Loan Rejected CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-TO-ON-LOAN-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();
        UpdateCopyAvailabilityRequest request = new UpdateCopyAvailabilityRequest(AvailabilityStatus.ON_LOAN);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.updateCopyAvailability(title.getId(), copy.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    @Test
    void updateCopyAvailabilityToReservedIsRejected() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability To Reserved Rejected CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-TO-RESERVED-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();
        UpdateCopyAvailabilityRequest request = new UpdateCopyAvailabilityRequest(AvailabilityStatus.RESERVED);

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> copyService.updateCopyAvailability(title.getId(), copy.getId(), request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_AVAILABILITY_WORKFLOW_MANAGED"));
    }

    @Test
    void updateCopyAvailabilitySameAvailableIsIdempotent() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Idempotent Available CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-IDEMPOTENT-AVAIL-1", CopyCondition.GOOD, AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.updateCopyAvailability(
                title.getId(), copy.getId(), new UpdateCopyAvailabilityRequest(AvailabilityStatus.AVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void updateCopyAvailabilitySameUnavailableIsIdempotent() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Idempotent Unavailable CRT");
        Copy copy = persistCopy(title, "CRT-AVAIL-IDEMPOTENT-UNAVAIL-1", CopyCondition.GOOD, AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        CopyResponse response = copyService.updateCopyAvailability(
                title.getId(), copy.getId(), new UpdateCopyAvailabilityRequest(AvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateCopyAvailabilityForNonExistentCopyThrowsCopyNotFound() {
        authenticateWithCopyManage();
        Title title = persistTitle("Availability Nonexistent Copy CRT");
        entityManager.flush();
        UpdateCopyAvailabilityRequest request = new UpdateCopyAvailabilityRequest(AvailabilityStatus.AVAILABLE);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> copyService.updateCopyAvailability(title.getId(), -1L, request))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("COPY_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private Title persistTitle(String title) {
        return persistTitleWithStatus(title, TitleStatus.ACTIVE);
    }

    private Title persistWithdrawnTitle(String title) {
        return persistTitleWithStatus(title, TitleStatus.WITHDRAWN);
    }

    private Title persistTitleWithStatus(String title, TitleStatus titleStatus) {
        Title entity = new Title();
        entity.setTitle(title);
        entity.setLanguage(Language.EN);
        entity.setTitleStatus(titleStatus);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entityManager.persist(entity);
        return entity;
    }

    private Copy persistCopy(
            Title title, String inventoryCode, CopyCondition copyCondition, AvailabilityStatus availabilityStatus) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(copyCondition);
        copy.setAvailabilityStatus(availabilityStatus);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }
}
