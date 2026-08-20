package be.primatis.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * Comptage des Reservations actives (WAITING/READY) d'un membre
     * (DEV-08.2), même précédent que {@code LoanRepository.countByUserIdAndLoanStatusIn}
     * (DEV-07.2) : le Repository expose uniquement la donnée, la règle
     * {@code MAX_ACTIVE_RESERVATIONS_PER_MEMBER} (business-rules.md §4.5)
     * reste arbitrée par le futur {@code ReservationService} (DEV-08.5),
     * jamais lue ici — ce Repository ne consulte jamais
     * {@code application_setting}.
     */
    long countByUserIdAndReservationStatusIn(Long userId, Collection<ReservationStatus> reservationStatuses);

    /**
     * Consultation paginée des Reservations d'un membre, tous statuts
     * confondus (historique inclus) — même précédent que
     * {@code LoanRepository.findByUserId} (DEV-07.2), consommée par la
     * surface self-service {@code /me/reservations} (DEV-08.4). La liste
     * staff non filtrée reste couverte par {@link #findAll(Pageable)}
     * (redéclarée ci-dessous avec le même {@code @EntityGraph}), jamais
     * dupliquée ici.
     *
     * <p>{@code @EntityGraph} sur {@code user}/{@code title}/{@code
     * assignedCopy} (DEV-08.4) : {@code ReservationResponse.from} (DEV-08.3)
     * accède inconditionnellement à ces trois relations {@code LAZY}
     * ({@code assignedCopy} seulement si non {@code null}) — sans fetch
     * explicite, chaque page de résultats produirait un N+1 évident
     * (jusqu'à 3 requêtes supplémentaires par ligne). Les trois relations
     * sont {@code @ManyToOne} (jamais une collection) : combiner
     * {@code EntityGraph} avec {@code Pageable} ne produit donc aucun
     * risque de doublon de ligne ni de pagination en mémoire (le risque
     * documenté ne concerne que {@code JOIN FETCH}/{@code EntityGraph} sur
     * une collection {@code *ToMany}). {@code fulfilledByLoan}
     * délibérément **exclu** de ce graphe : {@code ReservationResponse}
     * n'utilise jamais que son {@code id}, déjà disponible sur le proxy
     * {@code LAZY} non initialisé, sans déclencher aucun chargement — y
     * ajouter un fetch serait un chargement inutile.
     */
    @EntityGraph(attributePaths = {"user", "title", "assignedCopy"})
    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    /**
     * Redéclaration de {@link JpaRepository#findAll(Pageable)} avec le même
     * {@code @EntityGraph} que {@link #findByUserId} (DEV-08.4), pour la
     * même raison exacte — consommée par la consultation staff paginée
     * {@code GET /api/v1/reservations}. Comportement fonctionnel strictement
     * identique à la méthode héritée (mêmes résultats, même pagination),
     * seule la stratégie de fetch change.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "title", "assignedCopy"})
    Page<Reservation> findAll(Pageable pageable);

    /**
     * Miroir de l'invariant structurel « un Copy affecté à au plus une
     * Reservation READY » (ux_reservation_ready_assigned_copy, V001).
     */
    Optional<Reservation> findByAssignedCopyIdAndReservationStatus(
            Long assignedCopyId, ReservationStatus reservationStatus);

    /**
     * Identification (non verrouillée) des candidats {@code READY} dont
     * l'échéance de retrait est dépassée à l'instant de référence fourni
     * ({@code expirationDate <= referenceInstant}), ordre déterministe
     * ({@code expirationDate} croissant, {@code id} croissant en tie-break)
     * — même précédent exact que {@link #findWaitingReservationIdsForTitleOrderedByFifo}
     * (DEV-07.6) : <b>projection scalaire {@code id}, jamais l'entité</b>,
     * pour permettre un verrouillage individuel ultérieur via
     * {@link #findByIdForUpdate(Long)} sans réintroduire le bug de
     * staleness d'identity map JPA déjà rencontré et corrigé une fois sur
     * la primitive FIFO (DEV-07.6 §9). {@code status} reste paramétré (même
     * choix de conception que la méthode FIFO ci-dessus) mais n'a de sens
     * métier qu'appelée avec {@code READY} : seule une Reservation
     * {@code READY} peut expirer (business-rules.md §4.13 — {@code WAITING}
     * n'expire jamais selon la baseline actuelle, DEV-08.1 §9/§13).
     *
     * <p>Cette méthode identifie uniquement les candidates : elle ne mute
     * aucune Reservation, ne lance aucun workflow d'expiration et ne
     * préjuge d'aucun mécanisme de déclenchement (scheduler, lecture
     * paresseuse ou autre — OD-DEV08-06, toujours ouverte, à trancher lors
     * de DEV-08.7).
     */
    @Query("SELECT r.id FROM Reservation r WHERE r.reservationStatus = :status "
            + "AND r.expirationDate <= :referenceInstant ORDER BY r.expirationDate ASC, r.id ASC")
    List<Long> findExpiredReservationIds(
            @Param("status") ReservationStatus status,
            @Param("referenceInstant") Instant referenceInstant,
            Pageable pageable);

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
     *
     * <p><b>{@code excludedIds}</b> (DEV-08.6) : permet à
     * {@code ReservationAssignmentService} de sauter un candidat
     * {@code WAITING} devenu métier non admissible (ex. membre {@code
     * BLOCKED}/{@code EXPIRED}) <em>sans le modifier</em> — un candidat
     * non admissible reste {@code WAITING} indéfiniment, donc le seul
     * filtre {@code reservationStatus = :status} ne suffit jamais à
     * l'exclure d'un appel suivant (DEV-08.1 §11, business-rules.md §4.7 :
     * « a Reservation that is no longer admissible must not block the
     * whole queue »). Toujours non vide en pratique : l'appelant doit
     * fournir au moins un identifiant sentinelle (ex. {@code 0L}, aucune
     * séquence PostgreSQL ne produit d'identifiant réel ≤ 0) lorsqu'aucune
     * exclusion réelle n'est encore connue, pour éviter un {@code NOT IN ()}
     * vide.
     */
    @Query("SELECT r.id FROM Reservation r WHERE r.title.id = :titleId AND r.reservationStatus = :status "
            + "AND r.id NOT IN :excludedIds ORDER BY r.reservationDate ASC, r.id ASC")
    List<Long> findWaitingReservationIdsForTitleOrderedByFifo(
            @Param("titleId") Long titleId,
            @Param("status") ReservationStatus status,
            @Param("excludedIds") Collection<Long> excludedIds,
            Pageable pageable);

    /**
     * Chargement verrouillé par identifiant (SELECT ... FOR UPDATE), même
     * précédent que {@code CopyRepository.findByIdForUpdate}/
     * {@code LoanRepository.findByIdForUpdate} — verrouillage sans
     * ambiguïté (clause {@code WHERE id = :id} uniquement, aucun
     * {@code ORDER BY}/{@code LIMIT}). Réservé au fulfillment
     * (DEV-07.5, {@code Reservation READY} déjà connue), à l'assignation
     * FIFO (DEV-07.6/DEV-08.6, candidat identifié par
     * {@link #findWaitingReservationIdsForTitleOrderedByFifo}), à
     * l'identification des expirées (candidat identifié par
     * {@link #findExpiredReservationIds}) et à l'annulation d'une
     * Reservation {@code WAITING}/{@code READY} (DEV-08.6, candidat
     * identifié par {@link #findCancellationSnapshotById}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /**
     * Instantané scalaire (non verrouillé) d'une Reservation, destiné
     * exclusivement à décider — <b>avant</b> tout verrouillage — quel ordre
     * de lock appliquer pour son annulation (DEV-08.6, DEV-DEC-0033 : {@code
     * Copy → Reservation}, jamais l'inverse). Ne charge jamais l'Entity
     * {@code Reservation} elle-même ({@code assignedCopyId}/{@code userId}
     * en projection scalaire uniquement) : si {@code assignedCopyId} n'est
     * pas {@code null}, l'appelant verrouille d'abord le {@code Copy}
     * correspondant, puis la {@code Reservation} via
     * {@link #findByIdForUpdate(Long)} — jamais l'inverse. Toute valeur lue
     * ici doit être revalidée après verrouillage (elle peut être périmée
     * dès sa lecture).
     */
    @Query("SELECT r.id AS id, r.reservationStatus AS reservationStatus, "
            + "r.assignedCopy.id AS assignedCopyId, r.user.id AS userId "
            + "FROM Reservation r WHERE r.id = :id")
    Optional<ReservationCancellationSnapshot> findCancellationSnapshotById(@Param("id") Long id);
}
