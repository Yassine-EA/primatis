import { Language } from './language';

/**
 * Voir `be.primatis.catalogue.dto.UpdateTitleRequest` côté backend (PATCH
 * sparse à trois états : absent = ne pas modifier, présent+valeur =
 * remplacer, présent+`null` = effacer — uniquement pour les champs où le
 * Service backend accepte `null` comme action distincte). Même convention
 * exacte que `UpdateUserRequest` (DEV-05.6).
 *
 * Audit champ par champ (`CatalogueService.updateTitle`) :
 * - `title`/`language` : `null` explicite est rejeté par le backend
 *   (colonnes `NOT NULL`, `TITLE_MUST_NOT_BE_BLANK`/`LANGUAGE_MUST_NOT_BE_NULL`)
 *   — jamais une action valide, donc pas de `| null` ici.
 * - `isbn`/`subtitle`/`summary`/`publicationYear`/`pageCount`/`publisher`/
 *   `coverImageUrl` : `null` est accepté et efface la valeur — `| null`
 *   légitime.
 * - `authorIds` : absent et `null` produisent le même état côté backend (pas
 *   de sémantique de clear, un Title garde toujours ≥1 Author) — pas de
 *   `| null` ici.
 * - `genreIds` : même cas — fourni (même vide `[]`) remplace intégralement
 *   l'ensemble ; absent/`null` laisse inchangé — pas de `| null` distinct
 *   (un tableau vide `[]` est la façon d'effacer tous les Genres, pas `null`).
 *
 * `titleStatus` est volontairement absent (action dédiée
 * `UpdateTitleStatusRequest`).
 */
export interface UpdateTitleRequest {
  isbn?: string | null;
  title?: string;
  subtitle?: string | null;
  summary?: string | null;
  publicationYear?: number | null;
  language?: Language;
  pageCount?: number | null;
  publisher?: string | null;
  coverImageUrl?: string | null;
  authorIds?: number[];
  genreIds?: number[];
}
