package be.primatis.loan;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * Miroir de l'invariant structurel « au maximum un Loan ouvert par Copy »
     * (ux_loan_open_copy, V001) : permet de retrouver/revalider le Loan
     * ouvert d'un Copy avant une opération concurrente.
     */
    List<Loan> findByCopyIdAndLoanStatusIn(Long copyId, Collection<LoanStatus> loanStatuses);

    /**
     * Comptage des Loans ouverts (ACTIVE/OVERDUE) d'un borrower (DEV-07.2).
     * Expose uniquement la donnée : la règle « maximum 5 Loans actifs »
     * reste arbitrée par le futur Service, jamais ici.
     */
    long countByUserIdAndLoanStatusIn(Long userId, Collection<LoanStatus> loanStatuses);

    /**
     * Consultation paginée des Loans d'un borrower, tous statuts confondus
     * (historique inclus) — réutilisable aussi bien par un futur écran staff
     * filtré par utilisateur que par la surface self-service {@code
     * /me/loans} (DEV-07.2). La liste staff non filtrée reste couverte par
     * {@link JpaRepository#findAll(Pageable)}, jamais dupliquée ici.
     */
    Page<Loan> findByUserId(Long userId, Pageable pageable);

    /**
     * Chargement verrouillé (SELECT ... FOR UPDATE), réservé au futur
     * workflow de retour (DEV-07.6, {@code registerReturn}) — même
     * précédent que {@code CopyRepository.findByIdForUpdate} (DEV-07.2) :
     * empêche deux retours concurrents du même Loan de progresser
     * simultanément. Charge et verrouille en une seule opération : aucune
     * fenêtre non verrouillée entre le chargement et le premier contrôle de
     * {@code loanStatus}. {@link #findById(Object)}/les autres méthodes
     * restent non verrouillées pour tous les autres usages (consultation,
     * DEV-07.4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);

    /**
     * Identification (non verrouillée) des candidats {@code ACTIVE} dont
     * {@code dueDate} est strictement dépassée à la date de référence
     * fournie ({@code dueDate < referenceDate}), ordre déterministe
     * ({@code dueDate} croissant, {@code id} croissant en tie-break) —
     * même précédent exact que {@code ReservationRepository.findExpiredReservationIds}
     * (DEV-08.7) : <b>projection scalaire {@code id}, jamais l'entité</b>,
     * pour permettre un verrouillage individuel ultérieur via {@link
     * #findByIdForUpdate(Long)} sans réintroduire le bug de staleness
     * d'identity map JPA déjà rencontré une fois sur la primitive FIFO
     * (DEV-07.6 §9). {@code dueDate == referenceDate} n'est volontairement
     * <b>pas</b> inclus : un Loan dû aujourd'hui reste {@code ACTIVE}
     * jusqu'à la fin de la journée (DEV-10.8, mission §8) — même
     * précédent exact que {@code registerReturn}, qui n'a jamais traité
     * une échéance du jour comme un retard.
     *
     * <p>Cette méthode identifie uniquement les candidats : elle ne mute
     * aucun Loan, ne crée aucune Notification et ne préjuge d'aucun
     * mécanisme de déclenchement (scheduler ou autre) — {@link
     * LoanDeadlineService} porte la responsabilité complète du traitement.
     */
    @Query("SELECT l.id FROM Loan l WHERE l.loanStatus = :status AND l.dueDate < :referenceDate "
            + "ORDER BY l.dueDate ASC, l.id ASC")
    List<Long> findOverdueLoanIds(
            @Param("status") LoanStatus status,
            @Param("referenceDate") LocalDate referenceDate,
            Pageable pageable);

    /**
     * Identification (non verrouillée) des candidats {@code ACTIVE} dont
     * {@code dueDate} tombe strictement après la date de référence et au
     * plus tard à la borne supérieure fournie ({@code referenceDate <
     * dueDate <= upperBound}) — même style exact que {@link
     * #findOverdueLoanIds}. La borne supérieure ({@code referenceDate +
     * LOAN_DUE_SOON_DAYS}) est calculée par l'appelant ({@link
     * LoanDeadlineService}, jamais codée en dur ici) à partir du paramètre
     * {@code LOAN_DUE_SOON_DAYS} (business-rules.md §6.7). Un Loan déjà
     * {@code OVERDUE} (donc {@code dueDate < referenceDate}) est exclu
     * structurellement par la borne inférieure stricte {@code
     * referenceDate < dueDate} — jamais besoin de filtrer explicitement
     * sur le statut {@code OVERDUE}.
     */
    @Query("SELECT l.id FROM Loan l WHERE l.loanStatus = :status AND l.dueDate > :referenceDate "
            + "AND l.dueDate <= :upperBound ORDER BY l.dueDate ASC, l.id ASC")
    List<Long> findDueSoonLoanIds(
            @Param("status") LoanStatus status,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("upperBound") LocalDate upperBound,
            Pageable pageable);
}
