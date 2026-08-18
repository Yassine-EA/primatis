package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.FineRepository;
import be.primatis.fine.FineStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.loan.dto.LoanResponse;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationRepository;
import be.primatis.reservation.ReservationStatus;
import be.primatis.setting.ApplicationSettingService;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberExpirationPolicy;
import be.primatis.user.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Consultation (DEV-07.4) et enregistrement (DEV-07.5) des {@code Loan}.
 *
 * <p><b>Consultation</b> : liste paginée staff ({@code LOAN_READ}) et
 * liste paginée personnelle ({@code /me/loans}, ownership structurelle).
 * Lecture strictement pure — {@code loanStatus} est exposé tel que
 * persisté, sans jamais muter {@code ACTIVE → OVERDUE}. Cette transition
 * appartient à un traitement périodique non encore implémenté
 * (business-rules.md §9, implementation freedom) ; ni {@link #listLoans}
 * ni {@link #listOwnLoans} ni {@link #registerLoan} ne l'anticipent.
 *
 * <p>{@code LoanResponse.from} accède à {@code loan.getUser()}/{@code
 * loan.getCopy()} (LAZY) et {@code copy.getTitle()} (LAZY) : au plus deux
 * requêtes supplémentaires par ligne, bornées par la pagination (maximum
 * 100 lignes/page, contrat pagination). Aucun {@code JOIN FETCH}/{@code
 * EntityGraph} introduit sans besoin mesuré (aucune Repository modifiée
 * par DEV-07.4, conformément à la mission).
 *
 * <p><b>Enregistrement</b> ({@link #registerLoan}, DEV-07.5) : workflow
 * transactionnel unique — éligibilité complète du bénéficiaire, lock
 * {@code PESSIMISTIC_WRITE} ciblé sur {@code Copy} (DEV-DEC-0030,
 * {@link CopyRepository#findByIdForUpdate(Long)}), revalidation après
 * lock, fulfillment minimal d'une {@code Reservation READY} le cas
 * échéant, lecture de {@code LOAN_DURATION_DAYS} via
 * {@link ApplicationSettingService} (jamais codée en dur). Aucune Fine
 * créée, aucune Notification, aucun {@code @Scheduled} — strictement hors
 * périmètre DEV-07.5 (DEV-09/DEV-10 futurs).
 */
@Service
public class LoanService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String ACCOUNT_DISABLED_CODE = "ACCOUNT_DISABLED";
    private static final String NOT_A_MEMBER_CODE = "NOT_A_MEMBER";
    private static final String MEMBER_BLOCKED_CODE = "MEMBER_BLOCKED";
    private static final String MEMBER_EXPIRED_CODE = "MEMBER_EXPIRED";
    private static final String MEMBER_HAS_UNPAID_FINE_CODE = "MEMBER_HAS_UNPAID_FINE";
    private static final String MAX_ACTIVE_LOANS_REACHED_CODE = "MAX_ACTIVE_LOANS_REACHED";
    private static final String COPY_NOT_FOUND_CODE = "COPY_NOT_FOUND";
    private static final String COPY_ALREADY_ON_LOAN_CODE = "COPY_ALREADY_ON_LOAN";
    private static final String COPY_NOT_LENDABLE_CODE = "COPY_NOT_LENDABLE";
    private static final String COPY_RESERVED_FOR_ANOTHER_MEMBER_CODE = "COPY_RESERVED_FOR_ANOTHER_MEMBER";

    private static final String LOAN_DURATION_DAYS_KEY = "LOAN_DURATION_DAYS";
    private static final int MAX_ACTIVE_LOANS = 5;
    private static final List<LoanStatus> OPEN_LOAN_STATUSES = List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);

    private final LoanRepository loanRepository;
    private final AppUserRepository appUserRepository;
    private final CopyRepository copyRepository;
    private final FineRepository fineRepository;
    private final ReservationRepository reservationRepository;
    private final ApplicationSettingService applicationSettingService;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final Clock clock;

    public LoanService(
            LoanRepository loanRepository,
            AppUserRepository appUserRepository,
            CopyRepository copyRepository,
            FineRepository fineRepository,
            ReservationRepository reservationRepository,
            ApplicationSettingService applicationSettingService,
            MemberExpirationPolicy memberExpirationPolicy,
            Clock clock) {
        this.loanRepository = loanRepository;
        this.appUserRepository = appUserRepository;
        this.copyRepository = copyRepository;
        this.fineRepository = fineRepository;
        this.reservationRepository = reservationRepository;
        this.applicationSettingService = applicationSettingService;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.clock = clock;
    }

    /**
     * Liste paginée de tous les Loans, tous emprunteurs confondus ({@code
     * GET /api/v1/loans}, {@code LOAN_READ} requis).
     */
    @PreAuthorize("hasAuthority('LOAN_READ')")
    @Transactional(readOnly = true)
    public Page<LoanResponse> listLoans(Pageable pageable) {
        return loanRepository.findAll(pageable).map(LoanResponse::from);
    }

    /**
     * Liste paginée des Loans de l'utilisateur authentifié ({@code GET
     * /api/v1/me/loans}). Aucun {@code @PreAuthorize} : {@code selfUserId}
     * provient exclusivement de l'identité authentifiée (Controller), jamais
     * d'un paramètre client — ownership structurel, même convention que
     * {@code ResidenceService#getOwnCurrentResidence}. Aucune permission
     * {@code LOAN_READ} exigée sur ce parcours. Page vide (jamais 404) en
     * l'absence de Loan.
     */
    @Transactional(readOnly = true)
    public Page<LoanResponse> listOwnLoans(Long selfUserId, Pageable pageable) {
        return loanRepository.findByUserId(selfUserId, pageable).map(LoanResponse::from);
    }

    /**
     * Enregistrement transactionnel d'un Loan ({@code POST /api/v1/loans},
     * {@code LOAN_MANAGE} requis, DEV-07.5). Ordre fixé par la mission :
     * (1) chargement + éligibilité complète du bénéficiaire, (2) lock
     * {@code PESSIMISTIC_WRITE} du Copy, (3) revalidation post-lock, (4)
     * fulfillment minimal d'une Reservation READY le cas échéant, (5)
     * lecture {@code LOAN_DURATION_DAYS}, (6) persistance Loan + transition
     * Copy → {@code ON_LOAN}.
     *
     * <p>L'éligibilité est vérifiée <em>avant</em> l'acquisition du lock
     * Copy (aucun besoin de retenir le lock pendant les requêtes
     * Fine/comptage Loans, qui ne dépendent pas de l'état du Copy).
     */
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Transactional
    public LoanResponse registerLoan(CreateLoanRequest request) {
        AppUser borrower = appUserRepository.findById(request.borrowerUserId())
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + request.borrowerUserId() + "."));
        requireEligibleBorrower(borrower);

        Copy copy = copyRepository.findByIdForUpdate(request.copyId())
                .orElseThrow(() -> new ResourceNotFoundException(COPY_NOT_FOUND_CODE,
                        "Aucun exemplaire pour l'identifiant " + request.copyId() + "."));

        if (!loanRepository.findByCopyIdAndLoanStatusIn(copy.getId(), OPEN_LOAN_STATUSES).isEmpty()) {
            throw new BusinessRuleException(COPY_ALREADY_ON_LOAN_CODE,
                    "Un prêt ouvert existe déjà pour cet exemplaire.");
        }
        Reservation fulfilledReservation = requireLendableCopy(copy, borrower);

        int loanDurationDays = applicationSettingService.getInteger(LOAN_DURATION_DAYS_KEY);
        Instant now = clock.instant();

        Loan loan = new Loan();
        loan.setUser(borrower);
        loan.setCopy(copy);
        loan.setLoanDate(now);
        loan.setDueDate(LocalDate.now(clock).plusDays(loanDurationDays));
        loan.setReturnDate(null);
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(now);
        loan.setUpdatedAt(now);
        loanRepository.save(loan);

        if (fulfilledReservation != null) {
            fulfilledReservation.setReservationStatus(ReservationStatus.FULFILLED);
            fulfilledReservation.setFulfilledByLoan(loan);
            fulfilledReservation.setUpdatedAt(now);
        }

        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        copy.setUpdatedAt(now);

        return LoanResponse.from(loan);
    }

    /**
     * Éligibilité métier complète (business-rules.md §3.3) : compte
     * compatible ({@code AccountStatus} ≠ {@code DISABLED}), adhésion
     * existante, {@code MemberStatus} permettant l'emprunt (ni
     * {@code BLOCKED} ni {@code EXPIRED} — synchronisation paresseuse de
     * l'expiration via {@link MemberExpirationPolicy}, même précédent que
     * {@code UserService.getUserById}), aucune Fine {@code UNPAID}, moins
     * de {@value #MAX_ACTIVE_LOANS} Loans ouverts ({@code ACTIVE}/{@code
     * OVERDUE}).
     */
    private void requireEligibleBorrower(AppUser borrower) {
        if (borrower.getAccountStatus() == AccountStatus.DISABLED) {
            throw new BusinessRuleException(ACCOUNT_DISABLED_CODE, "Le compte de cet utilisateur est désactivé.");
        }
        if (borrower.getMemberNumber() == null) {
            throw new BusinessRuleException(NOT_A_MEMBER_CODE, "Cet utilisateur n'est pas adhérent.");
        }

        memberExpirationPolicy.syncIfNeeded(borrower);
        if (borrower.getMemberStatus() == MemberStatus.BLOCKED) {
            throw new BusinessRuleException(MEMBER_BLOCKED_CODE, "Cet adhérent est bloqué.");
        }
        if (borrower.getMemberStatus() == MemberStatus.EXPIRED) {
            throw new BusinessRuleException(MEMBER_EXPIRED_CODE, "L'adhésion de cet adhérent est expirée.");
        }

        if (fineRepository.existsByLoanUserIdAndFineStatus(borrower.getId(), FineStatus.UNPAID)) {
            throw new BusinessRuleException(MEMBER_HAS_UNPAID_FINE_CODE, "Cet adhérent a une amende impayée.");
        }
        if (loanRepository.countByUserIdAndLoanStatusIn(borrower.getId(), OPEN_LOAN_STATUSES) >= MAX_ACTIVE_LOANS) {
            throw new BusinessRuleException(MAX_ACTIVE_LOANS_REACHED_CODE,
                    "Cet adhérent a déjà " + MAX_ACTIVE_LOANS + " prêts ouverts.");
        }
    }

    /**
     * Revalidation post-lock de l'{@code AvailabilityStatus} (business-rules.md
     * §3.4). {@code CopyCondition} n'est jamais revérifiée séparément ici :
     * {@code ck_copy_condition_availability} (V001) garantit
     * structurellement que {@code LOST}/{@code OUT_OF_SERVICE} impliquent
     * déjà {@code UNAVAILABLE} — la vérification {@code AvailabilityStatus}
     * ci-dessous couvre donc transitivement {@code CopyCondition}, sans
     * dupliquer la règle physique déjà FIGÉE (DEV-06.6).
     *
     * @return la {@code Reservation READY} à fulfill si le Copy était
     * {@code RESERVED} pour ce bénéficiaire, {@code null} si {@code AVAILABLE}.
     */
    private Reservation requireLendableCopy(Copy copy, AppUser borrower) {
        return switch (copy.getAvailabilityStatus()) {
            case AVAILABLE -> null;
            case ON_LOAN, UNAVAILABLE -> throw new BusinessRuleException(COPY_NOT_LENDABLE_CODE,
                    "Cet exemplaire n'est pas actuellement prêtable.");
            case RESERVED -> requireMatchingReadyReservation(copy, borrower);
        };
    }

    /**
     * {@code RESERVED} n'autorise le Loan que pour le bénéficiaire exact de
     * la {@code Reservation READY} assignée à ce Copy, cohérente avec son
     * Title (business-rules.md §3.4/§3.11) — sinon refus, qu'aucune
     * Reservation READY n'existe (incohérence de données) ou qu'elle
     * appartienne à un autre adhérent.
     */
    private Reservation requireMatchingReadyReservation(Copy copy, AppUser borrower) {
        return reservationRepository
                .findByAssignedCopyIdAndReservationStatus(copy.getId(), ReservationStatus.READY)
                .filter(reservation -> reservation.getUser().getId().equals(borrower.getId()))
                .filter(reservation -> reservation.getTitle().getId().equals(copy.getTitle().getId()))
                .orElseThrow(() -> new BusinessRuleException(COPY_RESERVED_FOR_ANOTHER_MEMBER_CODE,
                        "Cet exemplaire est réservé pour un autre adhérent."));
    }
}
