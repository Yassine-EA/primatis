package be.primatis.fine;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    /**
     * Miroir de l'invariant structurel « au maximum une Fine par Loan »
     * (uq_fine_loan_id, V001).
     */
    Optional<Fine> findByLoanId(Long loanId);

    /**
     * Détermine si un borrower possède au moins une Fine {@code UNPAID} via
     * l'un de ses Loans (DEV-07.2, propriété imbriquée {@code loan.user.id}
     * — Spring Data génère la jointure {@code Fine → Loan → AppUser}).
     * Simple test d'existence : aucun calcul de montant, aucune formule,
     * aucun workflow Fine ici — la décision d'éligibilité au prêt reste au
     * futur Service.
     */
    boolean existsByLoanUserIdAndFineStatus(Long userId, FineStatus fineStatus);

    /**
     * Consultation paginée des Fines d'un membre, tous statuts confondus
     * (historique inclus) — même précédent exact que {@code
     * LoanRepository.findByUserId}/{@code ReservationRepository.findByUserId}
     * (DEV-09.4), destinée au futur {@code GET /api/v1/me/fines}. Propriété
     * imbriquée {@code loan.user.id} (même jointure {@code Fine → Loan →
     * AppUser} que {@link #existsByLoanUserIdAndFineStatus}). Aucun ordre
     * imposé ici : le tri appartient à l'appelant via {@link Pageable}
     * (même convention que {@code LoanRepository.findByUserId}) — aucune
     * source ne fixe encore d'ordre métier pour la consultation Fine.
     *
     * <p><b>{@code @EntityGraph} (DEV-09.5)</b> : {@code FineResponse.from}
     * (DEV-09.5) accède inconditionnellement à {@code fine.getLoan()}
     * (LAZY), puis {@code loan.getUser()}/{@code loan.getCopy()} (LAZY),
     * puis {@code copy.getTitle()} (LAZY) — contrairement à
     * {@code LoanResponse.from}/{@code ReservationResponse.from}, dont
     * l'Entity racine chargée par la requête paginée est déjà {@code Loan}/
     * {@code Reservation} elle-même, ici la racine {@code Fine} ajoute un
     * premier saut LAZY supplémentaire vers {@code Loan}. Sans fetch
     * explicite, chaque page produirait jusqu'à 4 requêtes supplémentaires
     * par ligne. Même précédent exact que {@code
     * ReservationRepository.findByUserId} (DEV-08.4) : les quatre relations
     * du graphe sont toutes {@code @ManyToOne} (jamais une collection),
     * donc combiner {@code EntityGraph} avec {@link Pageable} ne produit
     * aucun risque de doublon de ligne ni de pagination en mémoire.
     */
    @EntityGraph(attributePaths = {"loan", "loan.user", "loan.copy", "loan.copy.title"})
    Page<Fine> findByLoanUserId(Long userId, Pageable pageable);

    /**
     * Redéclaration de {@link JpaRepository#findAll(Pageable)} avec le même
     * {@code @EntityGraph} que {@link #findByLoanUserId} (DEV-09.5), pour la
     * même raison exacte — consommée par la future consultation staff
     * paginée {@code GET /api/v1/fines}, qui réutilise le même
     * {@code FineResponse} (aucun contrat staff distinct, DEV-09.5 §5).
     * Comportement fonctionnel strictement identique à la méthode héritée
     * (mêmes résultats, même pagination), seule la stratégie de fetch
     * change.
     */
    @Override
    @EntityGraph(attributePaths = {"loan", "loan.user", "loan.copy", "loan.copy.title"})
    Page<Fine> findAll(Pageable pageable);

    /**
     * Chargement verrouillé par identifiant (SELECT ... FOR UPDATE), même
     * précédent exact que {@code CopyRepository}/{@code LoanRepository}/
     * {@code ReservationRepository.findByIdForUpdate} — réservé aux futurs
     * workflows autonomes de confirmation de paiement et d'annulation
     * (DEV-DEC-0049, DEV-09.7/09.8). Non consommée par aucun Service pendant
     * DEV-09.4/09.5.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fine f WHERE f.id = :id")
    Optional<Fine> findByIdForUpdate(@Param("id") Long id);
}
