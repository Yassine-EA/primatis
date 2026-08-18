package be.primatis.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Miroir de l'invariant structurel « au maximum une Reservation active
     * par couple (user, title) » (ux_reservation_active_user_title, V001).
     */
    List<Reservation> findByUserIdAndTitleIdAndReservationStatusIn(
            Long userId, Long titleId, Collection<ReservationStatus> reservationStatuses);

    /**
     * Miroir de l'invariant structurel « un Copy affecté à au plus une
     * Reservation READY » (ux_reservation_ready_assigned_copy, V001).
     */
    Optional<Reservation> findByAssignedCopyIdAndReservationStatus(
            Long assignedCopyId, ReservationStatus reservationStatus);

    /**
     * Identification (non verrouillée) de l'identifiant de la prochaine
     * Reservation {@code WAITING} admissible d'un Title, FIFO déterministe
     * (DEV-07.6, business-rules.md §4.7 : « no persistent queuePosition,
     * order calculated dynamically »). Tri {@code reservationDate}
     * croissant (la plus ancienne demande d'abord) avec tie-break
     * déterministe sur {@code id} — même principe que le tri {@code
     * loanDate}/{@code id} de DEV-07.4 (DEV-DEC-0031), inversé ici (ASC,
     * FIFO = plus ancien en premier).
     *
     * <p><b>Retourne uniquement l'{@code id}, jamais l'entité</b> — leçon
     * tirée d'un bug réel détecté par {@code LoanServiceConcurrencyTests}
     * (DEV-07.6) : une première version retournait {@code Reservation}
     * complet, ce qui chargeait l'entité (non verrouillée) dans le
     * contexte de persistance ; le verrouillage ultérieur par {@code id}
     * ({@link #findByIdForUpdate(Long)}) exécutait bien un {@code SELECT
     * ... FOR UPDATE} réel côté PostgreSQL (le second thread se bloquait
     * correctement), mais Hibernate retournait l'instance Java déjà
     * gérée — donc encore {@code WAITING} en mémoire — au lieu de
     * recharger les colonnes fraîchement lues, rendant la revalidation
     * post-lock inopérante (les deux threads promouvaient la même
     * Reservation). Une projection scalaire ({@code Long}) ne place
     * jamais l'entité dans le contexte de persistance : le chargement
     * verrouillé qui suit est donc garanti frais.
     *
     * <p><b>Volontairement sans {@code @Lock}</b> et avec {@link Pageable}
     * plutôt qu'une méthode dérivée {@code findFirstBy...} : combiner
     * {@code ORDER BY}/{@code LIMIT} avec {@code FOR UPDATE} produit un
     * comportement documenté comme non fiable par PostgreSQL (verrouillage
     * de lignes au-delà de celles réellement retournées) — cette requête
     * ne verrouille jamais rien, le verrouillage réel se fait exclusivement
     * via {@link #findByIdForUpdate(Long)}.
     */
    @Query("SELECT r.id FROM Reservation r WHERE r.title.id = :titleId AND r.reservationStatus = :status "
            + "ORDER BY r.reservationDate ASC, r.id ASC")
    List<Long> findWaitingReservationIdsForTitleOrderedByFifo(
            @Param("titleId") Long titleId, @Param("status") ReservationStatus status, Pageable pageable);

    /**
     * Chargement verrouillé par identifiant (SELECT ... FOR UPDATE), même
     * précédent que {@code CopyRepository.findByIdForUpdate}/
     * {@code LoanRepository.findByIdForUpdate} — verrouillage sans
     * ambiguïté (clause {@code WHERE id = :id} uniquement, aucun
     * {@code ORDER BY}/{@code LIMIT}). Réservé au fulfillment
     * (DEV-07.5, {@code Reservation READY} déjà connue) et à l'assignation
     * FIFO (DEV-07.6, candidat identifié par
     * {@link #findFirstByTitleIdAndReservationStatusOrderByReservationDateAscIdAsc}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
