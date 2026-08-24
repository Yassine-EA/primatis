/**
 * Voir `be.primatis.article.ArticleStatus` côté backend. `ARCHIVED` est
 * terminal (aucun retour possible) ; `DRAFT → PUBLISHED → ARCHIVED` est
 * l'unique sens de transition (business-rules.md §7.1).
 */
export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
