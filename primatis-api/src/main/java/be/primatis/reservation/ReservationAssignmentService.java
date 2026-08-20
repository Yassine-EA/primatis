package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.exception.ConflictException;
import be.primatis.setting.ApplicationSettingService;
import be.primatis.user.AppUser;
import be.primatis.user.MemberExpirationPolicy;
import be.primatis.user.MemberStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Primitive partagée d'affectation FIFO d'un {@code Copy} libéré à la
 * prochaine {@code Reservation WAITING} admissible du même {@code Title}
 * (DEV-08.6, OD-DEV08-08). Extraite de
 * {@code LoanService.assignNextWaitingReservationOrMakeAvailable} (DEV-07.6)
 * pour être réutilisée à l'identique par {@code LoanService.registerReturn}
 * (retour d'un Loan) et {@code ReservationService} (annulation d'une
 * Reservation {@code READY}, DEV-08.6) — aucune duplication de la logique
 * FIFO.
 *
 * <p><b>Précondition stricte</b> : le {@code Copy} fourni doit déjà être
 * verrouillé ({@code PESSIMISTIC_WRITE}, {@code CopyRepository.findByIdForUpdate})
 * par l'appelant <em>avant</em> tout appel — cette classe ne verrouille
 * jamais le {@code Copy} elle-même, uniquement les {@code Reservation}
 * candidates, respectant strictement l'ordre {@code Copy → Reservation}
 * (DEV-DEC-0033, jamais l'inverse).
 *
 * <p>Ne crée aucun {@code Loan}, ne connaît aucun {@code Controller}, ne
 * gère aucune annulation elle-même — responsabilité strictement limitée à
 * l'affectation FIFO à partir d'un {@code Copy} déjà verrouillé.
 */
@Component
public class ReservationAssignmentService {

    private static final String RESERVATION_ASSIGNMENT_CONTENTION_CODE = "RESERVATION_ASSIGNMENT_CONTENTION";
    private static final String RESERVATION_READY_HOLD_HOURS_KEY = "RESERVATION_READY_HOLD_HOURS";

    /**
     * Garde-fou technique, jamais une règle métier (DEV-08.1 §14/OD-DEV08-09) :
     * borne le nombre de candidats examinés (raciés, non admissibles ou
     * l'un après l'autre) dans une seule transaction. Unique désormais que
     * la primitive est partagée — {@code LoanService} ne possède plus sa
     * propre constante (mission DEV-08.6 §12/OD-DEV08-09).
     */
    private static final int MAX_ASSIGNMENT_ATTEMPTS = 20;

    /**
     * Identifiant sentinelle jamais produit par {@code reservation_seq}
     * (démarre à 1) — permet de fournir un ensemble d'exclusions non vide à
     * {@link ReservationRepository#findWaitingReservationIdsForTitleOrderedByFifo}
     * dès la première tentative, sans dépendre du comportement d'un
     * {@code NOT IN ()} vide.
     */
    private static final long NO_EXCLUSION_SENTINEL_ID = 0L;

    private final ReservationRepository reservationRepository;
    private final ApplicationSettingService applicationSettingService;
    private final MemberExpirationPolicy memberExpirationPolicy;

    public ReservationAssignmentService(
            ReservationRepository reservationRepository,
            ApplicationSettingService applicationSettingService,
            MemberExpirationPolicy memberExpirationPolicy) {
        this.reservationRepository = reservationRepository;
        this.applicationSettingService = applicationSettingService;
        this.memberExpirationPolicy = memberExpirationPolicy;
    }

    /**
     * À partir d'un {@code Copy} déjà verrouillé, cherche la première
     * Reservation {@code WAITING} du même Title remplissant encore les
     * conditions d'admissibilité (business-rules.md §4.7 : « the system
     * must select the first Reservation that is still admissible... a
     * Reservation that is no longer admissible must not block the whole
     * queue ») et l'affecte ({@code → READY}, {@code assignedCopy = copy},
     * {@code expirationDate} calculée via {@code RESERVATION_READY_HOLD_HOURS}
     * lu dynamiquement, {@code Copy → RESERVED}). Si aucun candidat
     * admissible n'existe, rend le Copy disponible — {@code AVAILABLE} s'il
     * reste physiquement utilisable, {@code UNAVAILABLE} sinon (même
     * invariant que {@code ck_copy_condition_availability}, DEV-06.6 :
     * {@code CopyCondition LOST}/{@code OUT_OF_SERVICE} ⇒ {@code
     * UNAVAILABLE}).
     *
     * <p><b>Admissibilité réévaluée au moment du passage {@code READY}</b>
     * (DEV-08.1 §11, distinct de DEV-07 qui ne revalidait que le statut
     * {@code WAITING}) : seule l'adhésion du membre est réévaluée
     * (synchronisation paresseuse de l'expiration via
     * {@link MemberExpirationPolicy}, puis {@code MemberStatus == ACTIVE}
     * requis) — même périmètre exact que l'éligibilité de création
     * (DEV-08.5) : ni {@code AccountStatus}, ni Fine {@code UNPAID} ne sont
     * vérifiés, aucune source Reservation ne les impose (ne jamais
     * transposer la règle Loan). Un candidat non admissible n'est <b>jamais
     * modifié</b> (il reste {@code WAITING}, potentiellement satisfaisable
     * plus tard si son adhésion redevient active) — seulement exclu des
     * tentatives suivantes de <em>cette</em> invocation via
     * {@code excludedIds}, pour ne jamais l'examiner en boucle indéfiniment.
     *
     * <p><b>Deux issues distinctes</b> après épuisement de la file (même
     * garde-fou que DEV-07.6, désormais partagé) — jamais confondues :
     * liste de candidats réellement vide (plus aucun {@code WAITING}
     * jamais examinable) → Copy rendu disponible ; {@value
     * #MAX_ASSIGNMENT_ATTEMPTS} tentatives épuisées alors qu'un candidat
     * était systématiquement trouvé (raciés ou non admissibles) → {@link
     * ConflictException} ({@code RESERVATION_ASSIGNMENT_CONTENTION}, 409),
     * rollback complet — jamais un {@code AVAILABLE} silencieux masquant
     * une Reservation potentiellement encore satisfaisable.
     */
    public void assignNextAdmissibleWaitingReservationOrMakeAvailable(Copy copy, Instant now) {
        Set<Long> excludedIds = new LinkedHashSet<>();
        excludedIds.add(NO_EXCLUSION_SENTINEL_ID);

        for (int attempt = 0; attempt < MAX_ASSIGNMENT_ATTEMPTS; attempt++) {
            List<Long> candidateIds = reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                    copy.getTitle().getId(), ReservationStatus.WAITING, excludedIds, PageRequest.of(0, 1));

            if (candidateIds.isEmpty()) {
                releaseCopy(copy);
                return;
            }

            Long candidateId = candidateIds.get(0);
            Reservation reservation = reservationRepository.findByIdForUpdate(candidateId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Incohérence de données : Reservation " + candidateId
                                    + " disparue entre son identification et son verrouillage."));

            if (reservation.getReservationStatus() != ReservationStatus.WAITING) {
                // Raciné par un autre workflow concurrent (retour, annulation) entre
                // l'identification (non verrouillée) et ce verrouillage : son statut ne vaut
                // plus WAITING, donc le filtre de la requête l'exclura naturellement de la
                // prochaine itération — aucune exclusion explicite nécessaire.
                continue;
            }

            if (!isMemberAdmissible(reservation.getUser())) {
                // Non admissible mais toujours WAITING : jamais modifié, jamais réessayé
                // dans cette invocation (business-rules.md §4.7 — ne bloque jamais la file).
                excludedIds.add(candidateId);
                continue;
            }

            int holdHours = applicationSettingService.getInteger(RESERVATION_READY_HOLD_HOURS_KEY);
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copy);
            reservation.setExpirationDate(now.plus(holdHours, ChronoUnit.HOURS));
            reservation.setUpdatedAt(now);

            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            return;
        }

        throw new ConflictException(RESERVATION_ASSIGNMENT_CONTENTION_CODE,
                "Impossible d'assigner une Reservation WAITING après " + MAX_ASSIGNMENT_ATTEMPTS
                        + " tentatives : contention concurrente trop forte sur ce Title.");
    }

    /**
     * Seule l'adhésion active est réévaluée — voir Javadoc de la méthode
     * appelante pour la justification complète du périmètre.
     */
    private boolean isMemberAdmissible(AppUser member) {
        memberExpirationPolicy.syncIfNeeded(member);
        return member.getMemberStatus() == MemberStatus.ACTIVE;
    }

    /**
     * Même invariant physique que {@code ck_copy_condition_availability}
     * (V001, DEV-06.6) : un Copy {@code LOST}/{@code OUT_OF_SERVICE} ne
     * doit jamais redevenir {@code AVAILABLE}. Aucun helper partagé
     * existant pour ce contrôle (vérifié : {@code LoanService.registerReturn}
     * l'exprime en ligne, jamais extrait) — reproduit ici à l'identique
     * plutôt que dupliqué sans vérification.
     */
    private void releaseCopy(Copy copy) {
        if (copy.getCopyCondition() == CopyCondition.LOST || copy.getCopyCondition() == CopyCondition.OUT_OF_SERVICE) {
            copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        } else {
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        }
    }
}
