import { AccountStatus } from './account-status';
import { MemberStatus } from './member-status';

/**
 * Voir `be.primatis.user.web.MeProfileResponse` côté backend. Exclut
 * volontairement `roles`/`permissions` (déjà disponibles côté frontend via
 * le JWT, ne pas dupliquer), `createdAt`/`updatedAt`/`lastLoginAt` et
 * `Residence` (reste sur `/api/v1/me/residence`, jamais imbriquée ici).
 */
export interface MeProfileResponse {
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
}
