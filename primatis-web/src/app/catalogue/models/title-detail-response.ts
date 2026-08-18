import { AuthorResponse } from './author-response';
import { GenreResponse } from './genre-response';
import { Language } from './language';
import { TitleStatus } from './title-status';

/**
 * Voir `be.primatis.catalogue.dto.TitleDetailResponse` côté backend. Ne
 * contient délibérément aucune liste de `Copy` (domaine staff distinct,
 * `CopyResponse`/`CopyApiService`). `createdAt`/`updatedAt` restent des
 * chaînes ISO — aucune conversion `Date` dans la couche API.
 */
export interface TitleDetailResponse {
  id: number;
  isbn: string | null;
  title: string;
  subtitle: string | null;
  summary: string | null;
  publicationYear: number | null;
  language: Language;
  pageCount: number | null;
  publisher: string | null;
  coverImageUrl: string | null;
  titleStatus: TitleStatus;
  authors: AuthorResponse[];
  genres: GenreResponse[];
  createdAt: string;
  updatedAt: string;
}
