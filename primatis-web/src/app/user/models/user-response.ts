import { AccountStatus } from './account-status';
import { MemberStatus } from './member-status';

/**
 * Voir `be.primatis.user.web.UserResponse` côté backend. N'inclut jamais
 * Residence/roles/permissions — contrat distinct.
 */
export interface UserResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string | null;
  accountStatus: AccountStatus;
  memberNumber: string | null;
  memberStatus: MemberStatus | null;
  registrationDate: string | null;
  memberExpirationDate: string | null;
  blockedReason: string | null;
  createdAt: string;
  updatedAt: string;
}
