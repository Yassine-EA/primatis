import { Language } from './language';

/**
 * Voir `be.primatis.catalogue.dto.CreateTitleRequest` côté backend.
 * `titleStatus` est volontairement absent (toujours `ACTIVE` à la création,
 * imposé backend). `authorIds` : au moins un élément (`@NotEmpty` côté
 * backend) — non revalidé ici, le backend reste l'autorité.
 */
export interface CreateTitleRequest {
  isbn?: string;
  title: string;
  subtitle?: string;
  summary?: string;
  publicationYear?: number;
  language: Language;
  pageCount?: number;
  publisher?: string;
  coverImageUrl?: string;
  authorIds: number[];
  genreIds?: number[];
}
