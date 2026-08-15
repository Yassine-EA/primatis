package be.primatis.access;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Consommateur réel depuis DEV-05.5 ({@code UserService.createUser}) :
 * attribution de rôle à la création administrative. {@code save(...)}
 * suffit, aucune query dérivée nécessaire à ce stade.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
