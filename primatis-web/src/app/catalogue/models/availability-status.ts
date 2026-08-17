/**
 * Voir `be.primatis.catalogue.AvailabilityStatus` côté backend. `ON_LOAN`/
 * `RESERVED` restent représentables en lecture (DEV-07/08) mais ne sont
 * jamais écrits par la couche DEV-06.7 : aucune règle métier n'est dupliquée
 * ici, cette contrainte appartient exclusivement au backend.
 */
export type AvailabilityStatus = 'AVAILABLE' | 'ON_LOAN' | 'RESERVED' | 'UNAVAILABLE';
