package be.primatis.loan;

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
}
