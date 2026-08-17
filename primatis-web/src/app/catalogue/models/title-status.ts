/**
 * Voir `be.primatis.catalogue.TitleStatus` côté backend. `WITHDRAWN` n'est
 * pas terminal (réversible vers `ACTIVE`).
 */
export type TitleStatus = 'ACTIVE' | 'WITHDRAWN';
