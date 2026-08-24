import { SettingUpdatedByResponse } from './setting-updated-by-response';
import { SettingValueType } from './setting-value-type';

/**
 * Voir `be.primatis.setting.web.SettingResponse` côté backend
 * (`GET`/`PATCH /api/v1/settings`, DEV-12.2). Dates ISO conservées en
 * chaîne, jamais converties en `Date` ici (frontend.md « Dates »).
 *
 * `updatedAt: string | null` reprend le typage explicitement demandé par le
 * mandat DEV-12.3 §8. En pratique, le backend ne renvoie jamais `null` pour
 * ce champ (`ApplicationSetting.updatedAt` est `NOT NULL`, bootstrappé par
 * Flyway V001/V006, DEV-12.1 §8) — seul `updatedByUser` est réellement
 * nullable tant qu'aucune modification administrative n'a eu lieu. Le
 * typage nullable est conservé tel quel par prudence (aucun contrat
 * runtime validé, comme tout DTO de ce projet) ; la page traite
 * `updatedByUser === null` comme le signal réel de « jamais modifié »
 * (DÉDUIT MÉCANIQUEMENT, cf. log DEV-12.3 §5).
 */
export interface SettingResponse {
  readonly settingKey: string;
  readonly settingValue: string;
  readonly valueType: SettingValueType;
  readonly description: string;
  readonly updatedAt: string | null;
  readonly updatedByUser: SettingUpdatedByResponse | null;
}
