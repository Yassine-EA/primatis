/**
 * Voir `be.primatis.user.web.UpdateResidenceRequest` côté backend.
 * Remplacement complet (pas un PATCH sparse) : `cityId`/`street`/
 * `streetNumber` obligatoires, `boxNumber`/`additionalInfo` facultatifs.
 */
export interface UpdateResidenceRequest {
  cityId: number;
  street: string;
  streetNumber: string;
  boxNumber?: string;
  additionalInfo?: string;
}
