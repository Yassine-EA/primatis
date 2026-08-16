import { UserResponse } from './user-response';

/**
 * Voir `be.primatis.user.web.CreateUserResponse` côté backend.
 * `initialPassword` n'est retourné qu'ici, une seule fois.
 */
export interface CreateUserResponse {
  user: UserResponse;
  initialPassword: string;
}
