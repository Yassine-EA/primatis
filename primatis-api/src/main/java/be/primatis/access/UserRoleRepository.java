package be.primatis.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Consommateur réel depuis DEV-05.5 ({@code UserService.createUser}) :
 * attribution de rôle à la création administrative. {@code save(...)}
 * suffisait alors.
 *
 * DEV-05.6 ({@code UserService.updateUser}) ajoute {@link
 * #findByIdUserId(Long)} : nécessaire pour calculer le delta entre les
 * rôles actuellement attribués et l'ensemble final demandé (ajout/retrait),
 * sans dupliquer une query déjà existante ({@code
 * AppUserRepository.findRoleAndPermissionCodesByUserId} ne retourne que des
 * codes projetés, pas les Entities {@code UserRole} nécessaires pour
 * supprimer précisément les lignes obsolètes).
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByIdUserId(Long userId);
}
