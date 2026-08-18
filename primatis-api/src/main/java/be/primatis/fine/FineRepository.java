package be.primatis.fine;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
