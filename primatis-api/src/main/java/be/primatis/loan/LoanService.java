package be.primatis.loan;

import be.primatis.loan.dto.LoanResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation des {@code Loan} (DEV-07.4) : liste paginée staff ({@code
 * LOAN_READ}) et liste paginée personnelle ({@code /me/loans}, ownership
 * structurelle). Lecture strictement pure — {@code loanStatus} est exposé
 * tel que persisté, sans jamais muter {@code ACTIVE → OVERDUE}. Cette
 * transition appartient à un traitement périodique non encore implémenté
 * (business-rules.md §9, implementation freedom) ; DEV-07.4 ne l'anticipe
 * pas.
 *
 * <p>{@code LoanResponse.from} accède à {@code loan.getUser()}/{@code
 * loan.getCopy()} (LAZY) et {@code copy.getTitle()} (LAZY) : au plus deux
 * requêtes supplémentaires par ligne, bornées par la pagination (maximum
 * 100 lignes/page, contrat pagination). Aucun {@code JOIN FETCH}/{@code
 * EntityGraph} introduit sans besoin mesuré (aucune Repository modifiée
 * par DEV-07.4, conformément à la mission).
 */
@Service
public class LoanService {

    private final LoanRepository loanRepository;

    public LoanService(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    /**
     * Liste paginée de tous les Loans, tous emprunteurs confondus ({@code
     * GET /api/v1/loans}, {@code LOAN_READ} requis).
     */
    @PreAuthorize("hasAuthority('LOAN_READ')")
    @Transactional(readOnly = true)
    public Page<LoanResponse> listLoans(Pageable pageable) {
        return loanRepository.findAll(pageable).map(LoanResponse::from);
    }

    /**
     * Liste paginée des Loans de l'utilisateur authentifié ({@code GET
     * /api/v1/me/loans}). Aucun {@code @PreAuthorize} : {@code selfUserId}
     * provient exclusivement de l'identité authentifiée (Controller), jamais
     * d'un paramètre client — ownership structurel, même convention que
     * {@code ResidenceService#getOwnCurrentResidence}. Aucune permission
     * {@code LOAN_READ} exigée sur ce parcours. Page vide (jamais 404) en
     * l'absence de Loan.
     */
    @Transactional(readOnly = true)
    public Page<LoanResponse> listOwnLoans(Long selfUserId, Pageable pageable) {
        return loanRepository.findByUserId(selfUserId, pageable).map(LoanResponse::from);
    }
}
