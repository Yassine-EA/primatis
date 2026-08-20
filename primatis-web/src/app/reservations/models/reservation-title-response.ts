/**
 * Voir `be.primatis.reservation.dto.ReservationTitleResponse` côté
 * backend. Représentation compacte du Title ciblé, embarquée dans
 * `ReservationResponse`. Volontairement limité à l'identification —
 * n'expose ni `subtitle`, ni `language`, ni `authors`/`genres`/`copies` :
 * ces informations détaillées restent du ressort du contrat catalogue
 * (`TitleResponse`/`TitleDetailResponse`), pas d'un résumé Reservation.
 */
export interface ReservationTitleResponse {
  id: number;
  title: string;
  isbn: string | null;
}
