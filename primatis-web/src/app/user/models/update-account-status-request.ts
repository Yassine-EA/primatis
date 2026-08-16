import { AccountStatus } from './account-status';

/**
 * Voir `be.primatis.user.web.UpdateAccountStatusRequest` côté backend.
 */
export interface UpdateAccountStatusRequest {
  status: AccountStatus;
}
