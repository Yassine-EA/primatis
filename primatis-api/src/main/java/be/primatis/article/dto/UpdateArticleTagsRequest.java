package be.primatis.article.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Contrat REST d'association/dissociation de {@code Tag} existants sur un
 * {@code Article} (DEV-11.9, {@code PATCH .../tags}, {@code ARTICLE_MANAGE},
 * DEV-DEC-0060). Porte uniquement des identifiants de {@code Tag} déjà
 * existants — structurellement impossible de fournir {@code code}/{@code
 * label}/{@code description} par ce contrat, aucun Tag ne peut donc être créé
 * à la volée depuis l'éditeur Article (business-rules.md §7.13).
 *
 * <p>Sémantique : sélection finale exacte (remplacement complet), jamais un
 * couple add/remove — {@code tagIds} représente l'ensemble de Tags associés
 * après l'opération. Idempotent. {@code null} explicite refusé ({@code
 * @NotNull}, endpoint dédié à sens unique, contrairement au PATCH sparse
 * général d'Article) ; liste vide autorisée (dissocie tous les Tags, {@code
 * Tag} n'étant pas obligatoire sur un Article). Doublons tolérés dans la
 * requête, normalisés en ensemble avant traitement (DÉDUIT MÉCANIQUEMENT :
 * la clé composite {@code article_tag} interdit déjà structurellement les
 * associations dupliquées).
 */
public record UpdateArticleTagsRequest(@NotNull List<Long> tagIds) {
}
