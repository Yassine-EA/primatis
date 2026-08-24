/**
 * Voir `be.primatis.article.dto.UpdateArticleTagsRequest` côté backend
 * (`PATCH .../articles/{id}/tags`, DEV-DEC-0060). Porte uniquement des
 * identifiants de `Tag` déjà existants — structurellement impossible
 * d'envoyer `code`/`label`/`description` par ce contrat, aucun Tag ne peut
 * donc être créé à la volée depuis l'éditeur Article.
 *
 * Sémantique : sélection finale exacte (remplacement complet), jamais un
 * couple add/remove. `tagIds` est requis (jamais optionnel) : cet endpoint
 * dédié n'a pas de sémantique PATCH sparse, contrairement à
 * `UpdateArticleRequest` — chaque appel exprime la sélection complète
 * voulue, un tableau vide dissocie tous les Tags.
 */
export interface UpdateArticleTagsRequest {
  tagIds: number[];
}
