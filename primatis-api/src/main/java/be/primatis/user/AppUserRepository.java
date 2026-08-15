package be.primatis.user;

import be.primatis.access.RoleAndPermissionCode;
import jakarta.persistence.LockModeType;
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
}
