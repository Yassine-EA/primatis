/**
 * Voir `be.primatis.article.dto.ArticleUserResponse` côté backend —
 * représentation compacte partagée pour `author` et `lastModifiedBy` dans
 * `ArticleResponse`. Aucun `memberNumber` (délibérément omis côté backend,
 * un auteur/éditeur d'Article n'a le plus souvent aucune adhésion associée).
 */
export interface ArticleUserResponse {
  id: number;
  firstName: string;
  lastName: string;
}
