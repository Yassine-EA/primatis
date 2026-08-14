package be.primatis.access;

/**
 * Projection d'une ligne (rôle, permission) résultant de la chaîne RBAC
 * autoritaire AppUser → UserRole → Role → RolePermission → Permission.
 *
 * {@code permissionCode} vaut {@code null} lorsque le rôle n'accorde
 * actuellement aucune permission (LEFT JOIN côté RolePermission/Permission)
 * — le rôle doit néanmoins rester visible en tant qu'authority.
 */
public record RoleAndPermissionCode(String roleCode, String permissionCode) {
}
