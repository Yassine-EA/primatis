package be.primatis.loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

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
}
