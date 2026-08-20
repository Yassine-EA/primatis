package be.primatis.fine;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.dto.FineResponse;
import be.primatis.loan.Loan;
import be.primatis.setting.ApplicationSettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Logique métier {@code Fine} : création automatique lors d'un retour
 * tardif ({@link #createForLateReturnIfApplicable}, DEV-09.6,
 * DEV-DEC-0046/0047/0048/0049), confirmation administrative d'un paiement
 * externe ({@link #confirmExternalPayment}, DEV-09.7, DEV-DEC-0050),
 * annulation administrative ({@link #cancelFine}, DEV-09.8) et
 * consultation paginée membre/staff ({@link #listOwnFines}/{@link
 * #listFines}, DEV-09.9).
 *
 * <p>{@link #createForLateReturnIfApplicable} — extraite de {@code
 * LoanService.registerReturn}, même précédent architectural exact que
 * {@code ReservationAssignmentService} (DEV-08.6) : une primitive
 * concrète, sans sa propre frontière transactionnelle ({@code
 * @Transactional} absent sur cette méthode), destinée à être appelée
 * <em>depuis</em> la transaction déjà ouverte par l'appelant — jamais
 * {@code REQUIRES_NEW}, jamais de rappel vers {@code LoanService} (aucune
 * dépendance circulaire fonctionnelle, seulement la dépendance de package
 * déjà existante {@code fine → loan}, symétrique à {@code reservation →
 * loan} déjà présente dans le projet via {@code Reservation.fulfilledByLoan}).
 * Ne verrouille jamais rien elle-même (DEV-DEC-0049) : le {@code Loan}
 * fourni est déjà verrouillé {@code PESSIMISTIC_WRITE} par l'appelant
 * avant cet appel — la protection contre une double création concurrente
 * est donc assurée <em>transitivement</em> par ce verrou déjà détenu,
 * jamais par un verrou {@code Fine} propre (qui n'existerait de toute
 * façon pas encore, la ligne n'étant pas encore créée).
 *
 * <p>{@link #confirmExternalPayment}/{@link #cancelFine} — workflows
 * autonomes, à l'inverse porteurs de leur propre frontière {@code
 * @Transactional} (jamais appelés depuis une transaction déjà ouverte par
 * un autre Service), verrouillent explicitement la {@code Fine}
 * elle-même ({@code FineRepository.findByIdForUpdate}) — la même
 * primitive de verrouillage pour les deux, ce qui garantit leur exclusion
 * mutuelle réelle sur une même Fine (voir Javadoc de {@link #cancelFine}).
 */
@Service
public class FineService {

    private static final String FINE_WEEKLY_RATE_KEY = "FINE_WEEKLY_RATE";
    private static final String FINE_MAX_AMOUNT_KEY = "FINE_MAX_AMOUNT";
    private static final long DAYS_PER_WEEK = 7;

    private static final String FINE_NOT_FOUND_CODE = "FINE_NOT_FOUND";
    private static final String FINE_NOT_PAYABLE_CODE = "FINE_NOT_PAYABLE";
    private static final String FINE_NOT_CANCELLABLE_CODE = "FINE_NOT_CANCELLABLE";

    private final FineRepository fineRepository;
    private final ApplicationSettingService applicationSettingService;
    private final Clock clock;

    public FineService(
            FineRepository fineRepository, ApplicationSettingService applicationSettingService, Clock clock) {
        this.fineRepository = fineRepository;
        this.applicationSettingService = applicationSettingService;
        this.clock = clock;
    }

    /**
     * Crée une {@code Fine UNPAID} pour {@code loan} si son retour est
     * tardif (business-rules.md §5.2, DEV-DEC-0048) ; ne fait rien
     * autrement. Précondition stricte, jamais revalidée ici : {@code
     * loan.getReturnDate()} doit déjà être renseignée par l'appelant
     * ({@code LoanService.registerReturn}) avant cet appel — aucun
     * paramètre {@code returnDate} séparé, pour éviter toute divergence
     * possible entre deux valeurs qui doivent rester strictement
     * identiques.
     *
     * <p><b>Retard</b> (DEV-DEC-0046) : {@code overdueDays = DAYS.between(
     * dueDate, returnDate)}, exclusivement sur les {@code LocalDate}
     * métier (jamais une différence d'{@code Instant}, qui introduirait
     * une dépendance à l'heure de la journée hors de propos ici — même
     * principe que le calcul de {@code dueDate} lui-même, DEV-07.5).
     * Aucune Fine si {@code overdueDays <= 0} (retour à temps ou anticipé).
     *
     * <p><b>Semaines entamées</b> : {@code startedWeeks = (overdueDays + 6)
     * / 7}, arithmétique entière {@code long} exclusivement (aucun
     * {@code double}/{@code Math.ceil}, conforme à la mission) — équivalent
     * exact de {@code ceil(overdueDays / 7.0)} pour tout {@code overdueDays
     * > 0} (ex. 1→1, 7→1, 8→2, 14→2, 15→3).
     *
     * <p><b>Montant</b> : {@code FINE_WEEKLY_RATE}/{@code FINE_MAX_AMOUNT}
     * lus exclusivement via {@link ApplicationSettingService#getDecimal(String)}
     * (jamais codés en dur ici). {@code rawAmount = FINE_WEEKLY_RATE ×
     * startedWeeks} — {@code BigDecimal.multiply(BigDecimal)} conserve une
     * échelle égale à la somme des deux échelles opérandes ; {@code
     * FINE_WEEKLY_RATE} porte déjà l'échelle 2 (bootstrappé {@code "0.80"},
     * V006) et {@code startedWeeks} est multiplié en échelle 0
     * ({@code BigDecimal.valueOf(long)}), donc {@code rawAmount} porte déjà
     * l'échelle 2, directement compatible {@code NUMERIC(10,2)} sans
     * arrondi arbitraire supplémentaire. Plafond appliqué par {@code
     * BigDecimal.min(FINE_MAX_AMOUNT)}, jamais {@code double}/{@code float}.
     *
     * <p><b>Motif</b> (DEV-DEC-0047) : généré exclusivement à partir de
     * {@code overdueDays}/{@code startedWeeks}, jamais saisi, aucune enum
     * {@code FineReason}.
     *
     * <p><b>Anti-doublon</b> (DEV-DEC-0049) : {@link
     * FineRepository#findByLoanId(Long)} revérifié avant création — une
     * Fine déjà présente pour ce {@code Loan} à ce stade signale une
     * incohérence de données structurelle (un {@code Loan} ne peut être
     * retourné qu'une seule fois, {@code registerReturn} refuse déjà tout
     * second retour via {@code LOAN_ALREADY_RETURNED} avant d'atteindre ce
     * point), jamais un cas métier normal à rejeter proprement en 409 —
     * même précédent exact que l'{@code IllegalStateException} déjà utilisée
     * dans {@code LoanService.registerReturn}/{@code
     * ReservationAssignmentService} pour ce type d'anomalie structurelle.
     * {@code uq_fine_loan_id} (V001) reste la protection structurelle
     * ultime en base, en filet de sécurité.
     */
    public void createForLateReturnIfApplicable(Loan loan, Instant now) {
        LocalDate dueDate = loan.getDueDate();
        LocalDate returnDate = loan.getReturnDate();

        long overdueDays = ChronoUnit.DAYS.between(dueDate, returnDate);
        if (overdueDays <= 0) {
            return;
        }

        if (fineRepository.findByLoanId(loan.getId()).isPresent()) {
            throw new IllegalStateException(
                    "Incohérence de données : une Fine existe déjà pour le Loan " + loan.getId()
                            + ", alors qu'aucune ne devrait exister avant son premier retour"
                            + " (uq_fine_loan_id garantit normalement son unicité).");
        }

        long startedWeeks = (overdueDays + (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK;

        BigDecimal weeklyRate = applicationSettingService.getDecimal(FINE_WEEKLY_RATE_KEY);
        BigDecimal maxAmount = applicationSettingService.getDecimal(FINE_MAX_AMOUNT_KEY);

        BigDecimal rawAmount = weeklyRate.multiply(BigDecimal.valueOf(startedWeeks));
        BigDecimal amount = rawAmount.min(maxAmount);

        String reason = "Retour tardif — " + overdueDays + " jour(s) de retard, "
                + startedWeeks + " semaine(s) entamée(s).";

        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(amount);
        fine.setReason(reason);
        fine.setIssuedAt(now);
        fine.setFineStatus(FineStatus.UNPAID);
        fineRepository.save(fine);
    }

    /**
     * Confirmation administrative d'un règlement externe (DEV-09.7, {@code
     * POST /api/v1/fines/{fineId}/payment-confirmation}, {@code FINE_MANAGE}
     * requis, DEV-DEC-0050). PRIMATIS ne traite aucune transaction
     * financière — cette méthode enregistre uniquement la confirmation
     * qu'un paiement a eu lieu hors du système (business-rules.md §5.6).
     * Aucune Entity/mécanisme {@code Payment} introduit ; {@code amount}/
     * {@code reason}/{@code issuedAt}/{@code loan} jamais modifiés.
     *
     * <p><b>Workflow transactionnel autonome</b>, contrairement à {@link
     * #createForLateReturnIfApplicable} : ce Service porte ici sa propre
     * frontière {@code @Transactional} (workflow indépendant, non appelé
     * depuis une transaction déjà ouverte par un autre Service — jamais
     * {@code REQUIRES_NEW}, cette annotation ouvre simplement la seule
     * transaction du workflow).
     *
     * <p><b>Verrouillage</b> (DEV-DEC-0049) : {@link
     * FineRepository#findByIdForUpdate(Long)} charge et verrouille en une
     * seule opération ({@code PESSIMISTIC_WRITE}) — jamais {@code
     * findById}. Le statut est revalidé <em>après</em> acquisition du
     * verrou (seule {@code UNPAID} peut transitionner ; {@code PAID}/
     * {@code CANCELLED} sont refusés explicitement, jamais transformés
     * silencieusement en succès idempotent — un second appel sur une Fine
     * déjà {@code PAID} est rejeté, même principe que {@code
     * ReservationService.cancelReservationForUser}/{@code
     * RESERVATION_NOT_CANCELLABLE}, un seul code couvrant les deux statuts
     * terminaux). Le verrou reste détenu jusqu'au commit — aucune fenêtre
     * non verrouillée entre la revalidation et la mutation.
     *
     * <p>{@code paidAt = clock.instant()} (même {@link Clock} injecté que
     * {@code LoanService}/{@code ReservationService}, jamais {@code
     * Instant.now()} direct) — déterministe en test.
     */
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Transactional
    public FineResponse confirmExternalPayment(Long fineId) {
        Fine fine = fineRepository.findByIdForUpdate(fineId)
                .orElseThrow(() -> new ResourceNotFoundException(FINE_NOT_FOUND_CODE,
                        "Aucune amende pour l'identifiant " + fineId + "."));

        if (fine.getFineStatus() != FineStatus.UNPAID) {
            throw new BusinessRuleException(FINE_NOT_PAYABLE_CODE,
                    "Cette amende n'est plus payable (statut actuel : " + fine.getFineStatus() + ").");
        }

        fine.setFineStatus(FineStatus.PAID);
        fine.setPaidAt(clock.instant());

        return FineResponse.from(fine);
    }

    /**
     * Annulation administrative d'une Fine (DEV-09.8, {@code POST
     * /api/v1/fines/{fineId}/cancel}, {@code FINE_MANAGE} requis). Ne
     * supprime jamais la Fine — conservée comme historique, même principe
     * que {@code ReservationService.cancelReservationForUser} (aucun
     * hard-delete). {@code amount}/{@code reason}/{@code issuedAt}/{@code
     * loan} jamais modifiés.
     *
     * <p>Même architecture exacte que {@link #confirmExternalPayment} :
     * workflow transactionnel autonome (propre frontière {@code
     * @Transactional}, jamais {@code REQUIRES_NEW}), verrouillage explicite
     * via {@link FineRepository#findByIdForUpdate(Long)} (jamais {@code
     * findById}), statut revalidé <em>après</em> acquisition du verrou.
     * Seule {@code UNPAID} peut transitionner vers {@code CANCELLED} —
     * {@code PAID}/{@code CANCELLED} refusés explicitement par un unique
     * code {@code FINE_NOT_CANCELLABLE} (même principe que {@code
     * FINE_NOT_PAYABLE}/{@code RESERVATION_NOT_CANCELLABLE}, un seul code
     * couvrant les deux statuts terminaux) : ni {@code PAID → CANCELLED},
     * ni {@code CANCELLED → CANCELLED} (second appel non idempotent), ni
     * {@code CANCELLED → UNPAID} ne sont jamais autorisés.
     *
     * <p>{@code cancelledAt = clock.instant()} (même {@link Clock} injecté
     * que {@link #confirmExternalPayment}), {@code paidAt} reste {@code
     * null} (la Fine n'a jamais été payée pour atteindre cette transition).
     *
     * <p>Coexistence avec {@link #confirmExternalPayment} (DEV-DEC-0049) :
     * les deux méthodes verrouillent la <em>même</em> ligne {@code Fine}
     * via la même primitive {@code findByIdForUpdate} — si les deux
     * transitions sont tentées concurremment sur la même Fine {@code
     * UNPAID}, PostgreSQL sérialise réellement les deux transactions ; la
     * seconde à acquérir le verrou revalide {@code fineStatus} et découvre
     * l'état déjà terminal fixé par la première, quel que soit l'ordre
     * d'arrivée — jamais les deux transitions ne peuvent réussir
     * simultanément, jamais {@code paidAt} et {@code cancelledAt}
     * simultanément non-null (garanti applicativement ici, et
     * structurellement par {@code ck_fine_status_consistency}, V001, en
     * filet de sécurité ultime).
     */
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Transactional
    public FineResponse cancelFine(Long fineId) {
        Fine fine = fineRepository.findByIdForUpdate(fineId)
                .orElseThrow(() -> new ResourceNotFoundException(FINE_NOT_FOUND_CODE,
                        "Aucune amende pour l'identifiant " + fineId + "."));

        if (fine.getFineStatus() != FineStatus.UNPAID) {
            throw new BusinessRuleException(FINE_NOT_CANCELLABLE_CODE,
                    "Cette amende n'est plus annulable (statut actuel : " + fine.getFineStatus() + ").");
        }

        fine.setFineStatus(FineStatus.CANCELLED);
        fine.setCancelledAt(clock.instant());

        return FineResponse.from(fine);
    }

    /**
     * Liste paginée des Fines de l'utilisateur authentifié ({@code GET
     * /api/v1/me/fines}, DEV-09.9). Aucun {@code @PreAuthorize} : {@code
     * selfUserId} provient exclusivement de l'identité authentifiée
     * (Controller), jamais d'un paramètre client — même ownership
     * structurelle que {@code LoanService.listOwnLoans}/{@code
     * ReservationService.listOwnReservations}. Historique complet
     * (UNPAID/PAID/CANCELLED), jamais filtré. Page vide (jamais 404) en
     * l'absence de Fine.
     *
     * <p>{@link FineRepository#findByLoanUserId(Long, Pageable)} (DEV-09.4,
     * {@code @EntityGraph} ajouté DEV-09.5) — jointure {@code Fine → Loan →
     * AppUser}, aucun ordre imposé par la requête elle-même (aucune règle
     * métier d'ordre Fine décidée) : le tri appartient à l'appelant via
     * {@link Pageable}.
     */
    @Transactional(readOnly = true)
    public Page<FineResponse> listOwnFines(Long selfUserId, Pageable pageable) {
        return fineRepository.findByLoanUserId(selfUserId, pageable).map(FineResponse::from);
    }

    /**
     * Liste paginée de toutes les Fines, tous membres confondus ({@code GET
     * /api/v1/fines}, {@code FINE_READ} requis, DEV-09.9). Historique
     * complet (UNPAID/PAID/CANCELLED), jamais filtré. Aucun filtre/critère
     * de recherche supplémentaire (aucun besoin démontré à ce stade).
     *
     * <p>{@link FineRepository#findAll(Pageable)} — redéclarée avec le même
     * {@code @EntityGraph} que {@link #listOwnFines} depuis DEV-09.5, pour
     * la même raison exacte (N+1 sur {@code Fine → Loan → AppUser}/{@code
     * Copy}/{@code Title}).
     */
    @PreAuthorize("hasAuthority('FINE_READ')")
    @Transactional(readOnly = true)
    public Page<FineResponse> listFines(Pageable pageable) {
        return fineRepository.findAll(pageable).map(FineResponse::from);
    }
}
