import { TitleStatus } from './title-status';

/**
 * Voir `be.primatis.catalogue.dto.UpdateTitleStatusRequest` côté backend.
 * Champ nommé `status` (pas `titleStatus`) — nom de propriété backend exact.
 */
export interface UpdateTitleStatusRequest {
  status: TitleStatus;
}
