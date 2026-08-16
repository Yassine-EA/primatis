import { RoleCode } from '../../auth/models/role';
import { UserResponse } from './user-response';

/**
 * Voir `be.primatis.user.web.UserDetailResponse` côté backend (DEV-05.12
 * Décision 13). `roles` ne contient que les codes de rôle actuels de
 * l'utilisateur, jamais de permissions.
 */
export interface UserDetailResponse {
  user: UserResponse;
  roles: RoleCode[];
}
