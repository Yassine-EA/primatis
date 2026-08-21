package be.primatis.reservation;

import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyRepository;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Expiration automatique des {@code Reservation READY} dont
 * {@code expirationDate} est dépassée (DEV-08.7, DEV-DEC-0039, résout
 * OD-DEV08-06). Composant dédié, distinct de {@link ReservationService} —
 * même principe de séparation que {@link ReservationAssignmentService}
 * (DEV-08.6).
 *
 * <p><b>Deux méthodes publiques, deux responsabilités transactionnelles
 * distinctes</b> (mission DEV-08.7 §9) : {@link #processExpiredReadyReservations}
 * orchestre un batch borné sans porter elle-même une transaction unique
 * couvrant tout le batch (une erreur sur une candidate ne doit jamais
 * empêcher le traitement des autres, indépendantes) ; {@link
 * #expireReservationIfStillDue} porte une transaction dédiée par
 * candidate. Un appel {@code this.expireReservationIfStillDue(...)}
 * depuis la boucle de {@code processExpiredReadyReservations} constituerait
 * une auto-invocation qui contournerait le proxy Spring AOP, rendant
 * {@code @Transactional} inopérant sur cet appel — {@code self} est donc
 * injecté paresseusement ({@code @Lazy}) pour router l'appel à travers le
 * proxy réel, pattern documenté par Spring pour ce problème précis, sans
 * fractionner artificiellement ce composant conceptuellement unique en
 * deux beans distincts.
 */
@Service
public class ReservationExpirationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationService.class);

    /**
     * Garde-fou technique, jamais une règle métier ni une
     * {@code ApplicationSetting} (DEV-DEC-0039) : borne le nombre de
     * candidates traitées par exécution du scheduler. Les candidates
     * excédentaires seront traitées lors d'une exécution ultérieure
     * (cadence d'une minute, DEV-DEC-0039) — jamais une boucle « jusqu'à
     * base vide ».
     */
    static final int EXPIRATION_BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final CopyRepository copyRepository;
    private final ReservationAssignmentService reservationAssignmentService;
    private final NotificationService notificationService;
    private final ReservationExpirationService self;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            CopyRepository copyRepository,
            ReservationAssignmentService reservationAssignmentService,
            NotificationService notificationService,
            @Lazy ReservationExpirationService self) {
        this.reservationRepository = reservationRepository;
        this.copyRepository = copyRepository;
        this.reservationAssignmentService = reservationAssignmentService;
        this.notificationService = notificationService;
        this.self = self;
    }

    /**
     * Point d'entrée appelé par {@link ReservationExpirationScheduler}.
     * Identifie un batch borné de candidates {@code READY} expirées
     * ({@link ReservationRepository#findExpiredReservationIds}, réutilisée
     * sans modification depuis DEV-08.2, projection scalaire non
     * verrouillée — jamais l'Entity, même précédent que la primitive FIFO,
     * DEV-07.6 §9), puis délègue chaque candidate à {@link
     * #expireReservationIfStillDue} dans sa propre transaction.
     *
     * <p><b>Résilience</b> (mission §17) : une candidate dont le
     * traitement échoue de façon inattendue (invariant de données
     * réellement impossible, jamais une simple péremption de snapshot,
     * cf. Javadoc de {@link #expireReservationIfStillDue}) est journalisée
     * puis ignorée — le batch continue avec les candidates suivantes,
     * strictement indépendantes. Jamais un {@code catch (Exception) {}}
     * silencieux : chaque échec est tracé.
     */
    public void processExpiredReadyReservations(Instant referenceInstant) {
        List<Long> candidateIds = reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, EXPIRATION_BATCH_SIZE));

        for (Long candidateId : candidateIds) {
            try {
                self.expireReservationIfStillDue(candidateId, referenceInstant);
            } catch (RuntimeException ex) {
                log.error("Échec du traitement d'expiration pour la Reservation {} : {}",
                        candidateId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Expire une candidate unique, dans sa propre transaction. Réutilise
     * telle quelle la projection scalaire {@link ReservationCancellationSnapshot}/
     * {@link ReservationRepository#findCancellationSnapshotById} introduite par
     * DEV-08.6 (mission §6 : ne jamais dupliquer une projection identique
     * uniquement pour un nom « expiration » — {@code userId} y reste
     * présent mais simplement inutilisé ici, aucune notion d'ownership
     * n'existant côté scheduler).
     *
     * <p><b>Ordre de lock strict {@code Copy → Reservation}</b>
     * (DEV-DEC-0033, jamais l'inverse), même stratégie exacte que
     * l'annulation {@code READY→CANCELLED} (DEV-08.6) : le snapshot décide,
     * avant tout verrouillage, si un {@code Copy} doit être verrouillé.
     * Une candidate issue de {@link #processExpiredReadyReservations} a
     * toujours {@code assignedCopyId != null} en pratique (seule une
     * Reservation {@code READY} peut apparaître dans le batch, et
     * {@code READY} implique toujours un {@code assignedCopy}) — vérifié
     * malgré tout explicitement plutôt que supposé, pour rester
     * défensivement correct si cette méthode était un jour appelée avec un
     * identifiant dont le statut a changé entre l'identification et cet
     * appel.
     *
     * <p><b>Revalidation post-lock</b> (mission §7) — expire uniquement
     * si, après verrouillage : {@code reservationStatus == READY},
     * {@code assignedCopy} correspond toujours au {@code Copy} verrouillé,
     * {@code expirationDate != null} et {@code expirationDate <=
     * referenceInstant}. Sinon, la candidate est stale (déjà {@code
     * CANCELLED}/{@code FULFILLED} par une opération concurrente, ou plus
     * assez ancienne) : aucune mutation, retour silencieux — un candidat
     * scheduler devenu stale n'est jamais une erreur métier. Une
     * incohérence de données réellement impossible (candidate encore
     * {@code READY} mais {@code assignedCopy} ne correspond plus au
     * {@code Copy} verrouillé à partir de son propre {@code assignedCopyId}
     * ; ou {@code Copy}/{@code Reservation} disparus entre le snapshot et
     * le verrouillage, alors qu'aucun hard-delete n'existe dans la
     * baseline) est en revanche signalée explicitement ({@link
     * IllegalStateException}), même traitement exact que {@code
     * ReservationService.cancelReservationForUser} (DEV-08.6).
     *
     * <p>Sur expiration effective : {@code reservationStatus = EXPIRED},
     * {@code updatedAt = referenceInstant}, {@code assignedCopy}/{@code
     * expirationDate} <b>conservés tels quels</b> (jamais nettoyés, même
     * principe exact que {@code READY→CANCELLED}, DEV-DEC-0038), puis
     * délégation obligatoire à {@link
     * ReservationAssignmentService#assignNextAdmissibleWaitingReservationOrMakeAvailable}
     * — aucune duplication de la logique FIFO (OD-DEV08-08).
     *
     * <p><b>{@code RESERVATION_EXPIRED}</b> (DEV-10.6) : émise juste après
     * la mutation {@code EXPIRED}, <em>avant</em> la délégation FIFO — si
     * celle-ci promeut une {@code Reservation WAITING} suivante, {@code
     * RESERVATION_READY} est alors émise séparément par {@link
     * ReservationAssignmentService} (DEV-DEC-0056), dans cette même
     * transaction candidate (aucun {@code REQUIRES_NEW} sur aucun des deux
     * appels).
     */
    @Transactional
    public void expireReservationIfStillDue(Long reservationId, Instant referenceInstant) {
        ReservationCancellationSnapshot snapshot =
                reservationRepository.findCancellationSnapshotById(reservationId).orElse(null);
        if (snapshot == null
                || snapshot.getReservationStatus() != ReservationStatus.READY
                || snapshot.getAssignedCopyId() == null) {
            return;
        }

        Copy copy = copyRepository.findByIdForUpdate(snapshot.getAssignedCopyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : aucun exemplaire pour l'identifiant "
                                + snapshot.getAssignedCopyId() + ", pourtant référencé par le snapshot "
                                + "de la Reservation " + reservationId + "."));
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : Reservation " + reservationId
                                + " disparue entre son instantané et son verrouillage."));

        if (reservation.getReservationStatus() != ReservationStatus.READY) {
            return;
        }
        if (reservation.getAssignedCopy() == null || !reservation.getAssignedCopy().getId().equals(copy.getId())) {
            throw new IllegalStateException("Incohérence de données : Reservation " + reservationId
                    + " READY sans correspondance avec le Copy verrouillé " + copy.getId() + ".");
        }
        if (reservation.getExpirationDate() == null || reservation.getExpirationDate().isAfter(referenceInstant)) {
            return;
        }

        reservation.setReservationStatus(ReservationStatus.EXPIRED);
        reservation.setUpdatedAt(referenceInstant);
        notificationService.createForReservation(reservation, NotificationType.RESERVATION_EXPIRED,
                "Réservation expirée", "Le délai de retrait de votre réservation est expiré.");
        reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, referenceInstant);
        copy.setUpdatedAt(referenceInstant);
    }
}
