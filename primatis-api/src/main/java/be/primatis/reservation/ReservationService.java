package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.reservation.dto.CreateReservationRequest;
import be.primatis.reservation.dto.ReservationResponse;
import be.primatis.setting.ApplicationSettingService;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberExpirationPolicy;
import be.primatis.user.MemberStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Consultation (DEV-08.4), création (DEV-08.5) et annulation (DEV-08.6)
 * des {@code Reservation}.
 *
 * <p><b>Consultation</b> : liste paginée staff ({@code RESERVATION_READ}) et
 * liste paginée personnelle ({@code /me/reservations}, ownership
 * structurelle) — même principe exact que {@code LoanService.listLoans}/
 * {@code listOwnLoans} (DEV-07.4). Lecture strictement pure, aucune
 * mutation.
 *
 * <p>{@code ReservationResponse.from} accède à {@code reservation.getUser()}/
 * {@code getTitle()} (LAZY, inconditionnel) et {@code getAssignedCopy()}
 * (LAZY, si non {@code null}) : {@link ReservationRepository#findByUserId}/
 * {@link ReservationRepository#findAll(Pageable)} appliquent un
 * {@code @EntityGraph} dédié (DEV-08.4) pour éviter le N+1 évident sur ces
 * trois relations.
 *
 * <p><b>Création</b> ({@link #createOwnReservation}/{@link #createReservation},
 * DEV-08.5, DEV-DEC-0037) : deux entrées publiques (self via JWT, staff via
 * {@code RESERVATION_MANAGE} + {@code userId} explicite) délèguent à la même
 * méthode privée {@link #createReservationForUser} pour ne jamais dupliquer
 * la règle métier. Une nouvelle Reservation est toujours créée
 * {@code WAITING} (jamais directement {@code READY}) : {@code assignedCopy}/
 * {@code fulfilledByLoan}/{@code expirationDate} restent {@code null}.
 * Aucune création/annulation/expiration de Fine ou de Notification —
 * strictement hors périmètre DEV-08.5 (DEV-09/DEV-10 futurs, RESERVATION_CREATED
 * différée). Aucun {@code @Scheduled}.
 *
 * <p><b>Annulation</b> ({@link #cancelOwnReservation}/{@link #cancelReservation},
 * DEV-08.6, DEV-DEC-0038) : {@code WAITING → CANCELLED} et
 * {@code READY → CANCELLED} uniquement, jamais de hard-delete. L'ordre de
 * lock {@code Copy → Reservation} (DEV-DEC-0033) est respecté via une
 * projection scalaire préalable ({@link ReservationRepository#findCancellationSnapshotById})
 * qui décide, avant tout verrouillage, si un {@code Copy} doit être
 * verrouillé en premier. La réaffectation FIFO consécutive à une annulation
 * {@code READY} délègue à {@link ReservationAssignmentService} — primitive
 * partagée avec {@code LoanService.registerReturn} (OD-DEV08-08), aucune
 * duplication. Aucune Notification (ni {@code RESERVATION_CANCELLED}, ni
 * {@code RESERVATION_READY}), aucune expiration automatique — strictement
 * hors périmètre DEV-08.6 (DEV-10/DEV-08.7 futurs).
 */
@Service
public class ReservationService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String NOT_A_MEMBER_CODE = "NOT_A_MEMBER";
    private static final String MEMBER_BLOCKED_CODE = "MEMBER_BLOCKED";
    private static final String MEMBER_EXPIRED_CODE = "MEMBER_EXPIRED";
    private static final String TITLE_NOT_FOUND_CODE = "TITLE_NOT_FOUND";
    private static final String RESERVATION_COPY_AVAILABLE_CODE = "RESERVATION_COPY_AVAILABLE";
    private static final String RESERVATION_ALREADY_ACTIVE_CODE = "RESERVATION_ALREADY_ACTIVE";
    private static final String RESERVATION_LIMIT_REACHED_CODE = "RESERVATION_LIMIT_REACHED";
    private static final String RESERVATION_NOT_FOUND_CODE = "RESERVATION_NOT_FOUND";
    private static final String RESERVATION_NOT_CANCELLABLE_CODE = "RESERVATION_NOT_CANCELLABLE";
    private static final String RESERVATION_CANCELLATION_CONTENTION_CODE = "RESERVATION_CANCELLATION_CONTENTION";

    private static final String MAX_ACTIVE_RESERVATIONS_PER_MEMBER_KEY = "MAX_ACTIVE_RESERVATIONS_PER_MEMBER";
    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            List.of(ReservationStatus.WAITING, ReservationStatus.READY);

    /**
     * Garde-fou technique, jamais une règle métier (même principe que
     * {@code ReservationAssignmentService.MAX_ASSIGNMENT_ATTEMPTS}) : borne
     * le nombre de réévaluations complètes (snapshot → verrouillage) d'une
     * annulation lorsque le snapshot initial s'avère périmé — voir Javadoc
     * de {@link #cancelReservationForUser}.
     */
    private static final int MAX_CANCELLATION_REASSESSMENT_ATTEMPTS = 5;

    /**
     * Nom de la contrainte PostgreSQL (V001) dont la violation traduit
     * spécifiquement un doublon de Reservation active — même précédent que
     * {@code ResidenceService.RESIDENCE_CONFLICT_CONSTRAINTS} (DEV-05.8) :
     * dernier rempart contre une vraie course concurrente, sans introduire
     * de lock pessimiste (aucun besoin démontré ici — voir Javadoc de
     * {@link #createReservationForUser}).
     */
    private static final Set<String> RESERVATION_DUPLICATE_CONSTRAINTS = Set.of("ux_reservation_active_user_title");

    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final TitleRepository titleRepository;
    private final CopyRepository copyRepository;
    private final ApplicationSettingService applicationSettingService;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final ReservationAssignmentService reservationAssignmentService;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            AppUserRepository appUserRepository,
            TitleRepository titleRepository,
            CopyRepository copyRepository,
            ApplicationSettingService applicationSettingService,
            MemberExpirationPolicy memberExpirationPolicy,
            ReservationAssignmentService reservationAssignmentService,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.titleRepository = titleRepository;
        this.copyRepository = copyRepository;
        this.applicationSettingService = applicationSettingService;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.reservationAssignmentService = reservationAssignmentService;
        this.clock = clock;
    }

    /**
     * Liste paginée de toutes les Reservations, tous membres confondus
     * ({@code GET /api/v1/reservations}, {@code RESERVATION_READ} requis).
     */
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Transactional(readOnly = true)
    public Page<ReservationResponse> listReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable).map(ReservationResponse::from);
    }

    /**
     * Liste paginée des Reservations de l'utilisateur authentifié ({@code
     * GET /api/v1/me/reservations}). Aucun {@code @PreAuthorize} :
     * {@code selfUserId} provient exclusivement de l'identité authentifiée
     * (Controller), jamais d'un paramètre client — ownership structurelle,
     * même convention que {@code LoanService#listOwnLoans}. Aucune
     * permission {@code RESERVATION_READ} exigée sur ce parcours. Page
     * vide (jamais 404) en l'absence de Reservation.
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> listOwnReservations(Long selfUserId, Pageable pageable) {
        return reservationRepository.findByUserId(selfUserId, pageable).map(ReservationResponse::from);
    }

    /**
     * Création self-service ({@code POST /api/v1/me/reservations}).
     * {@code selfUserId} provient exclusivement de l'identité authentifiée
     * (Controller), jamais du corps de la requête — même ownership
     * structurelle que {@link #listOwnReservations}. Aucun
     * {@code @PreAuthorize} : ce parcours n'exige aucune permission
     * {@code RESERVATION_MANAGE} (DEV-DEC-0037).
     */
    @Transactional
    public ReservationResponse createOwnReservation(Long selfUserId, CreateOwnReservationRequest request) {
        return createReservationForUser(selfUserId, request.titleId());
    }

    /**
     * Création staff ({@code POST /api/v1/reservations}, {@code
     * RESERVATION_MANAGE} requis, DEV-DEC-0037). {@code userId} provient
     * exclusivement du corps de la requête — jamais de l'identité de
     * l'agent authentifié.
     */
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        return createReservationForUser(request.userId(), request.titleId());
    }

    /**
     * Workflow métier commun aux deux parcours (business-rules.md §4.3/§4.5/
     * §4.6, DEV-08.1 §9) : (1) éligibilité du membre (adhésion active), (2)
     * existence du Title, (3) aucun Copy immédiatement disponible
     * (OD-DEV08-02), (4) aucune Reservation active du même membre sur ce
     * Title, (5) limite {@code MAX_ACTIVE_RESERVATIONS_PER_MEMBER} non
     * atteinte (lue dynamiquement, jamais codée en dur), puis persistance
     * {@code WAITING}. Un Loan actif du même membre sur ce Title n'est
     * jamais vérifié (business-rules.md §4.3 : « does NOT, by itself,
     * forbid a Reservation » — ne jamais transposer la règle Loan).
     * Aucune Fine vérifiée : aucune source Reservation n'impose qu'une Fine
     * {@code UNPAID} bloque une création (DEV-08.1 §9/§19, à ne jamais
     * transposer depuis Loan).
     *
     * <p><b>Concurrence</b> : les contrôles (3)/(4) ci-dessus restent des
     * lectures non verrouillées — un doublon actif créé par une course
     * concurrente entre l'étape (4) et la persistance reste possible en
     * théorie. Aucun verrou pessimiste introduit (aucun besoin démontré :
     * contrairement à {@code registerLoan}/{@code registerReturn}, DEV-DEC-0030/
     * 0033, ce workflow ne combine ni Copy ni Reservation sous écriture
     * concurrente sur la même ligne — il ne fait qu'insérer une nouvelle
     * ligne). La contrainte structurelle {@code ux_reservation_active_user_title}
     * (V001) reste l'autorité finale : sa violation est traduite
     * explicitement en {@code RESERVATION_ALREADY_ACTIVE} (même précédent
     * exact que {@code ResidenceService.doReplaceResidence}, DEV-05.8 §2),
     * jamais laissée remonter comme {@code DataIntegrityViolationException}
     * brute.
     */
    private ReservationResponse createReservationForUser(Long userId, Long titleId) {
        AppUser member = requireEligibleMember(userId);
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResourceNotFoundException(TITLE_NOT_FOUND_CODE,
                        "Aucun titre pour l'identifiant " + titleId + "."));

        if (copyRepository.existsByTitleIdAndAvailabilityStatus(titleId, AvailabilityStatus.AVAILABLE)) {
            throw new BusinessRuleException(RESERVATION_COPY_AVAILABLE_CODE,
                    "Un exemplaire de ce titre est immédiatement disponible.");
        }

        if (!reservationRepository
                .findByUserIdAndTitleIdAndReservationStatusIn(userId, titleId, ACTIVE_RESERVATION_STATUSES)
                .isEmpty()) {
            throw new BusinessRuleException(RESERVATION_ALREADY_ACTIVE_CODE,
                    "Cet adhérent a déjà une réservation active pour ce titre.");
        }

        int maxActiveReservations =
                applicationSettingService.getInteger(MAX_ACTIVE_RESERVATIONS_PER_MEMBER_KEY);
        if (reservationRepository.countByUserIdAndReservationStatusIn(userId, ACTIVE_RESERVATION_STATUSES)
                >= maxActiveReservations) {
            throw new BusinessRuleException(RESERVATION_LIMIT_REACHED_CODE,
                    "Cet adhérent a atteint le nombre maximal de réservations actives.");
        }

        Instant now = clock.instant();
        Reservation reservation = new Reservation();
        reservation.setUser(member);
        reservation.setTitle(title);
        reservation.setReservationDate(now);
        reservation.setReservationStatus(ReservationStatus.WAITING);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);

        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            if (isReservationDuplicateConstraint(ex)) {
                throw new BusinessRuleException(RESERVATION_ALREADY_ACTIVE_CODE,
                        "Cet adhérent a déjà une réservation active pour ce titre.");
            }
            throw ex;
        }

        return ReservationResponse.from(reservation);
    }

    /**
     * Annulation self-service ({@code POST
     * /api/v1/me/reservations/{reservationId}/cancel}, DEV-DEC-0038). Aucun
     * {@code @PreAuthorize} : {@code selfUserId} provient exclusivement de
     * l'identité authentifiée (Controller) — même ownership structurelle
     * que {@link #createOwnReservation}. Un membre ne peut jamais annuler
     * la Reservation d'un autre : rejetée en {@code RESERVATION_NOT_FOUND}
     * (jamais un code distinct de « n'existe pas »), même précédent exact
     * que {@code CopyRepository.findByIdAndTitleId} (DEV-06.6, « ne jamais
     * révéler qu'un identifiant existe sous un autre scope »).
     */
    @Transactional
    public ReservationResponse cancelOwnReservation(Long selfUserId, Long reservationId) {
        return cancelReservationForUser(reservationId, selfUserId);
    }

    /**
     * Annulation staff ({@code POST
     * /api/v1/reservations/{reservationId}/cancel}, {@code
     * RESERVATION_MANAGE} requis, DEV-DEC-0038). Aucune restriction de
     * propriétaire.
     */
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Transactional
    public ReservationResponse cancelReservation(Long reservationId) {
        return cancelReservationForUser(reservationId, null);
    }

    /**
     * Workflow commun d'annulation (business-rules.md §4.12/E-034→E-040,
     * DEV-08.6). Transitions autorisées : {@code WAITING → CANCELLED},
     * {@code READY → CANCELLED}. {@code FULFILLED}/{@code CANCELLED}/
     * {@code EXPIRED} → {@code RESERVATION_NOT_CANCELLABLE} (409). Aucun
     * hard-delete.
     *
     * <p><b>Ordre de lock strict {@code Copy → Reservation}</b> (DEV-DEC-0033,
     * jamais l'inverse) : impossible de savoir a priori si la Reservation
     * est {@code READY} (donc si un {@code Copy} doit être verrouillé
     * <em>avant</em> elle) sans l'inspecter — {@link
     * ReservationRepository#findCancellationSnapshotById} fournit cette
     * décision via une projection scalaire non verrouillée (jamais
     * l'Entity, même leçon que DEV-07.6 §9), <b>avant</b> tout verrouillage.
     * Si {@code assignedCopyId} est renseigné : (1) verrouille le
     * {@code Copy}, (2) verrouille la {@code Reservation}, (3) revalide
     * intégralement statut/{@code assignedCopy}/ownership. Sinon : verrouille
     * uniquement la {@code Reservation}, revalide.
     *
     * <p><b>Snapshot périmé — réévaluation bornée</b> : si le snapshot
     * indiquait {@code assignedCopyId == null} (donc aucun {@code Copy}
     * verrouillé) mais qu'après verrouillage de la seule {@code Reservation}
     * son statut réel est déjà {@code READY} (promue par un retour/une autre
     * annulation concurrente entre le snapshot et ce verrouillage), la
     * mutation ne peut pas se poursuivre sans le {@code Copy} — la tentative
     * est abandonnée et l'opération entièrement réévaluée depuis un nouveau
     * snapshot (le lock déjà acquis reste détenu jusqu'au commit, sans
     * effet néfaste : aucune donnée n'a encore été modifiée). Bornée par
     * {@value #MAX_CANCELLATION_REASSESSMENT_ATTEMPTS} tentatives — garde-fou
     * technique, jamais une règle métier.
     *
     * <p><b>READY → CANCELLED</b> : {@code assignedCopy}/{@code expirationDate}
     * de la Reservation annulée sont <b>conservés tels quels</b> (jamais
     * nettoyés silencieusement — trace historique du Copy qui avait été
     * affecté, même principe que DEV-08.1 §8 pour {@code FULFILLED}). La
     * libération effective du Copy passe uniquement par le changement de
     * son {@code AvailabilityStatus}/sa réaffectation, déléguée à {@link
     * ReservationAssignmentService#assignNextAdmissibleWaitingReservationOrMakeAvailable}
     * — primitive partagée avec {@code LoanService.registerReturn}
     * (OD-DEV08-08), aucune duplication de la logique FIFO.
     */
    private ReservationResponse cancelReservationForUser(Long reservationId, Long selfUserId) {
        for (int attempt = 0; attempt < MAX_CANCELLATION_REASSESSMENT_ATTEMPTS; attempt++) {
            ReservationCancellationSnapshot snapshot = reservationRepository
                    .findCancellationSnapshotById(reservationId)
                    .orElseThrow(() -> new ResourceNotFoundException(RESERVATION_NOT_FOUND_CODE,
                            "Aucune réservation pour l'identifiant " + reservationId + "."));
            requireOwnershipIfSelf(snapshot.getUserId(), selfUserId);

            Instant now = clock.instant();

            if (snapshot.getAssignedCopyId() != null) {
                Copy copy = copyRepository.findByIdForUpdate(snapshot.getAssignedCopyId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Incohérence de données : aucun exemplaire pour l'identifiant "
                                        + snapshot.getAssignedCopyId() + ", pourtant référencé par le snapshot "
                                        + "de la Reservation " + reservationId + "."));
                Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Incohérence de données : Reservation " + reservationId
                                        + " disparue entre son instantané et son verrouillage."));

                requireOwnershipIfSelf(reservation.getUser().getId(), selfUserId);

                if (reservation.getReservationStatus() != ReservationStatus.READY) {
                    throw notCancellableException(reservation.getReservationStatus());
                }
                if (reservation.getAssignedCopy() == null
                        || !reservation.getAssignedCopy().getId().equals(copy.getId())) {
                    throw new IllegalStateException("Incohérence de données : Reservation " + reservationId
                            + " READY sans correspondance avec le Copy verrouillé " + copy.getId() + ".");
                }

                reservation.setReservationStatus(ReservationStatus.CANCELLED);
                reservation.setUpdatedAt(now);
                reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, now);
                copy.setUpdatedAt(now);

                return ReservationResponse.from(reservation);
            }

            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Incohérence de données : Reservation " + reservationId
                                    + " disparue entre son instantané et son verrouillage."));

            requireOwnershipIfSelf(reservation.getUser().getId(), selfUserId);

            if (reservation.getReservationStatus() == ReservationStatus.WAITING) {
                reservation.setReservationStatus(ReservationStatus.CANCELLED);
                reservation.setUpdatedAt(now);
                return ReservationResponse.from(reservation);
            }

            if (reservation.getReservationStatus() == ReservationStatus.READY) {
                // Snapshot périmé : promue WAITING→READY entre l'instantané et ce verrouillage,
                // aucun Copy verrouillé pour l'instant — impossible de continuer sans violer
                // l'ordre Copy→Reservation (DEV-DEC-0033). Réévaluation complète depuis un
                // nouveau snapshot, qui reflétera cette fois assignedCopyId.
                continue;
            }

            throw notCancellableException(reservation.getReservationStatus());
        }

        throw new ConflictException(RESERVATION_CANCELLATION_CONTENTION_CODE,
                "Impossible d'annuler la réservation " + reservationId + " après "
                        + MAX_CANCELLATION_REASSESSMENT_ATTEMPTS + " tentatives : contention concurrente trop forte.");
    }

    /**
     * Ownership self : {@code selfUserId == null} signifie un appel staff
     * (aucune restriction). Même code {@code RESERVATION_NOT_FOUND} que
     * « n'existe pas » — ne jamais révéler qu'un {@code reservationId}
     * existe sous un autre membre (§7 de la mission, même principe que
     * {@code CopyRepository.findByIdAndTitleId}).
     */
    private void requireOwnershipIfSelf(Long ownerUserId, Long selfUserId) {
        if (selfUserId != null && !ownerUserId.equals(selfUserId)) {
            throw new ResourceNotFoundException(RESERVATION_NOT_FOUND_CODE,
                    "Aucune réservation accessible pour cet identifiant.");
        }
    }

    private BusinessRuleException notCancellableException(ReservationStatus currentStatus) {
        return new BusinessRuleException(RESERVATION_NOT_CANCELLABLE_CODE,
                "Cette réservation n'est plus annulable (statut actuel : " + currentStatus + ").");
    }

    /**
     * Éligibilité du membre (business-rules.md §4.3 : « adhésion active » —
     * E-005, FIGÉ). Traduction technique directe de l'invariant déjà
     * existant : {@code memberNumber == null} signifie que l'utilisateur
     * n'est pas adhérent (aucune adhésion, donc jamais « active ») ;
     * {@link MemberExpirationPolicy#syncIfNeeded} synchronise paresseusement
     * l'expiration (même précédent que {@code LoanService.requireEligibleBorrower}/
     * {@code UserService.getUserById}) ; seul {@code MemberStatus.ACTIVE}
     * satisfait « adhésion active » — {@code BLOCKED}/{@code EXPIRED} sont
     * rejetés. {@code AccountStatus} n'est volontairement PAS vérifié ici :
     * aucune source ni invariant transversal ne l'impose pour Reservation
     * (business-rules.md §1.3 : {@code AccountStatus.DISABLED} concerne
     * l'accès au compte/authentification, jamais explicitement lié à une
     * règle de création Reservation — contrairement à Loan, où
     * {@code AccountStatus} est explicitement listé, DEV-08.1 §19/OD-DEV08-01
     * : ne pas transposer une condition Loan non démontrée pour Reservation).
     * Aucune Fine vérifiée (cf. Javadoc de {@link #createReservationForUser}).
     */
    private AppUser requireEligibleMember(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + userId + "."));

        if (user.getMemberNumber() == null) {
            throw new BusinessRuleException(NOT_A_MEMBER_CODE, "Cet utilisateur n'est pas adhérent.");
        }

        memberExpirationPolicy.syncIfNeeded(user);
        if (user.getMemberStatus() == MemberStatus.BLOCKED) {
            throw new BusinessRuleException(MEMBER_BLOCKED_CODE, "Cet adhérent est bloqué.");
        }
        if (user.getMemberStatus() == MemberStatus.EXPIRED) {
            throw new BusinessRuleException(MEMBER_EXPIRED_CODE, "L'adhésion de cet adhérent est expirée.");
        }

        return user;
    }

    /**
     * {@code getCause()} (le cause direct, {@code org.hibernate.exception.
     * ConstraintViolationException}) — pas {@code getMostSpecificCause()},
     * qui descendrait jusqu'au {@code PSQLException} JDBC brut, lequel ne
     * porte pas {@code getConstraintName()}. Même précédent exact que
     * {@code ResidenceService.isResidenceConstraintViolation} (DEV-05.8).
     */
    private boolean isReservationDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return RESERVATION_DUPLICATE_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }
}
