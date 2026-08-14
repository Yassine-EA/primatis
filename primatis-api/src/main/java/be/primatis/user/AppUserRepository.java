package be.primatis.user;

import be.primatis.access.RoleAndPermissionCode;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
