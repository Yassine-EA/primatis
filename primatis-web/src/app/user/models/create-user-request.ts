import { RoleCode } from '../../auth/models/role';
import { MemberStatus } from './member-status';

/**
 * Voir `be.primatis.user.web.CreateUserRequest` côté backend. La cohérence
 * conditionnelle (données Membership requises/refusées selon la présence de
 * `ROLE_MEMBER` dans `roles`) reste une autorité backend, jamais reproduite
 * ici comme règle frontend.
 */
export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  roles: RoleCode[];
  memberStatus?: MemberStatus;
  registrationDate?: string;
  memberExpirationDate?: string;
  blockedReason?: string;
}
