package be.primatis.user;

import be.primatis.access.RoleAndPermissionCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * email est l'identifiant d'authentification (architecture.md §5.2).
     */
    Optional<AppUser> findByEmail(String email);

    /**
     * Chargement verrouillé (SELECT ... FOR UPDATE), réservé au workflow de
     * login (DEV-03.6) : deux tentatives concurrentes sur le même compte se
     * sérialisent au niveau de la ligne PostgreSQL, évitant une mise à jour
     * perdue de failedLoginCount/lockedUntil. Verrou ciblé au seul
     * chargement d'authentification — {@link #findByEmail(String)} reste
     * non verrouillé pour tous les autres usages (ex. PrimatisUserDetailsService).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.email = :email")
    Optional<AppUser> findByEmailForAuthentication(@Param("email") String email);

    /**
     * Chaîne RBAC autoritaire AppUser → UserRole → Role → RolePermission →
     * Permission, en une seule requête (pas de N+1). LEFT JOIN côté
     * RolePermission/Permission : un rôle sans permission actuellement
     * accordée reste présent (permissionCode = null) plutôt que d'être
     * silencieusement exclu par une jointure interne.
     */
    @Query("""
            SELECT NEW be.primatis.access.RoleAndPermissionCode(r.code, p.code)
            FROM UserRole ur
            JOIN ur.role r
            LEFT JOIN RolePermission rp ON rp.role = r
            LEFT JOIN rp.permission p
            WHERE ur.id.userId = :userId
            """)
    List<RoleAndPermissionCode> findRoleAndPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * Permet à un futur Service (création/modification administrative,
     * DEV-05.5/DEV-05.6) de produire une erreur métier propre (ex. 409
     * CONFLICT) avant de heurter la contrainte {@code uq_app_user_email},
     * sans charger l'Entity complète. Ne remplace pas la contrainte DB, qui
     * reste l'autorité finale en cas de course concurrente.
     */
    boolean existsByEmail(String email);

    /**
     * Même usage qu'{@link #existsByEmail(String)} pour {@code memberNumber}
     * (contrainte {@code uq_app_user_member_number}). Ne doit jamais être
     * appelée avec {@code null} : {@code memberNumber} est nullable en base,
     * mais un appelant ne vérifie l'existence que d'une valeur candidate
     * concrète, jamais de l'absence de valeur (l'égalité SQL sur NULL ne
     * renverrait de toute façon jamais {@code true}).
     */
    boolean existsByMemberNumber(String memberNumber);

    /**
     * Recherche staff optionnelle (DEV-07.9.1, {@code GET /api/v1/users?q=}) :
     * sous-chaîne insensible à la casse sur {@code memberNumber}/{@code
     * firstName}/{@code lastName}/{@code email}, même principe que {@link
     * be.primatis.catalogue.AuthorRepository#findByFullNameContainingIgnoreCase}.
     * Le même terme trimé est passé aux quatre paramètres par l'appelant
     * ({@code UserService}) — aucune combinaison de filtres indépendants
     * (contrairement à {@code TitleSpecifications}), donc pas besoin d'une
     * {@code Specification} : une requête dérivée Spring Data standard
     * suffit. {@code memberNumber} étant nullable en base, un utilisateur
     * sans adhésion ne matche simplement jamais sur ce champ (comparaison
     * SQL sur {@code NULL}), sans erreur.
     */
    Page<AppUser> findByMemberNumberContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String memberNumber, String firstName, String lastName, String email, Pageable pageable);

    /**
     * Population destinataire de la diffusion {@code ARTICLE_PUBLISHED}
     * (DEV-11.7, business-rules.md §6.5/§6.10 : « recipient population →
     * AppUser where MemberStatus = ACTIVE »). Filtre strictement sur
     * {@code memberStatus} — jamais {@code AccountStatus}, rôles ou
     * permissions (mission DEV-11.7 §11 : aucune source ne l'exige).
     * Chargement complet (pas de {@link Pageable}) : la baseline exige un
     * fanout transactionnel synchrone (architecture.md §7.2), le volume
     * réel visé par PRIMATIS (bibliothèque, adhérents V1) ne justifie pas
     * un batching — IMPLEMENTATION FREEDOM documentée au log DEV-11.7 §25,
     * pas une limite arbitraire.
     */
    List<AppUser> findByMemberStatus(MemberStatus memberStatus);
}
