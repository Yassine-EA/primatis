package be.primatis.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResidenceRepository extends JpaRepository<Residence, Long> {

    /**
     * Résidence courante d'un utilisateur (DEV-05.9, {@code /me/profile}).
     * La DB garantit au maximum un résultat ({@code ux_residence_current_per_user}),
     * mais un utilisateur peut n'avoir aucune résidence courante.
     */
    Optional<Residence> findByUserIdAndEndDateIsNull(Long userId);

    /**
     * Historique complet des résidences d'un utilisateur (DEV-05.8), du plus
     * récent au plus ancien. Aucun ordre métier n'est figé par les sources ;
     * {@code startDate} descendant est l'ordre technique le plus naturel pour
     * un historique (IMPLEMENTATION FREEDOM, à documenter si contredit plus
     * tard par une décision explicite).
     */
    List<Residence> findByUserIdOrderByStartDateDesc(Long userId);

    /**
     * Indique si au moins une résidence existante de {@code userId} chevauche
     * la période candidate {@code [candidateStartDate, candidateEndDate]}
     * ({@code candidateEndDate} nullable = résidence courante candidate,
     * borne ouverte). Formule d'intersection d'intervalles à bornes de fin
     * optionnelles (NULL = infini) des deux côtés :
     * {@code existing.start <= candidate.end (ou infini) AND candidate.start <= existing.end (ou infini)}.
     * <p>
     * {@code candidateEndDate} n'est jamais comparé via {@code IS NULL} seul
     * dans le prédicat : PostgreSQL/JDBC ne peut pas typer un paramètre dont
     * la seule occurrence est une comparaison à {@code NULL} (SQLState
     * {@code 42P18}, "could not determine data type of parameter"). Il passe
     * donc par {@code COALESCE(:candidateEndDate, r.startDate)}, qui lui
     * donne le type de {@code r.startDate} (DATE) tout en préservant
     * exactement la sémantique voulue : si {@code candidateEndDate} est
     * {@code null} (borne ouverte), l'expression retombe sur
     * {@code r.startDate <= r.startDate}, toujours vraie, donc neutre —
     * strictement équivalent à l'ancienne condition
     * {@code candidateEndDate IS NULL OR r.startDate <= candidateEndDate}.
     * <p>
     * Ne décide pas si la nouvelle période est autorisée : cette décision
     * métier (DEV-05.8) appartient au Service, qui interprète un résultat
     * {@code true} comme un chevauchement à refuser.
     */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Residence r
            WHERE r.user.id = :userId
              AND r.startDate <= COALESCE(:candidateEndDate, r.startDate)
              AND (r.endDate IS NULL OR r.endDate >= :candidateStartDate)
            """)
    boolean existsOverlappingPeriod(
            @Param("userId") Long userId,
            @Param("candidateStartDate") LocalDate candidateStartDate,
            @Param("candidateEndDate") LocalDate candidateEndDate);
}
