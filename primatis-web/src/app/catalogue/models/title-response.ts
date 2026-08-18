import { Language } from './language';
import { TitleStatus } from './title-status';

/**
 * Voir `be.primatis.catalogue.dto.TitleResponse` côté backend. Contrat
 * compact de liste/recherche : n'inclut ni `summary`, ni `pageCount`, ni
 * `authors`/`genres`, ni `createdAt`/`updatedAt` — réservés à
 * `TitleDetailResponse`.
 */
export interface TitleResponse {
  id: number;
  isbn: string | null;
  title: string;
  subtitle: string | null;
  publicationYear: number | null;
  language: Language;
  publisher: string | null;
  coverImageUrl: string | null;
  titleStatus: TitleStatus;
}
