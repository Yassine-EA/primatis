package be.primatis.fine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    /**
     * Miroir de l'invariant structurel « au maximum une Fine par Loan »
     * (uq_fine_loan_id, V001).
     */
    Optional<Fine> findByLoanId(Long loanId);
}
