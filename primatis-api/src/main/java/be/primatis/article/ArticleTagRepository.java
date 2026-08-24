package be.primatis.article;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Consommateur réel depuis DEV-11.9 ({@code ArticleService.associateTags}/
 * {@code deleteDraftArticle}) : calcul du delta entre les associations
 * {@code Tag} actuellement attribuées à un {@code Article} et l'ensemble
 * final demandé (ajout/retrait) — même précédent exact que {@code
 * TitleAuthorRepository.findByIdTitleId}/{@code TitleGenreRepository.findByIdTitleId}
 * (DEV-06.5). Absent avant DEV-11.9 : aucune écriture sur {@code
 * article_tag} n'existait encore (DEV-11.3/11.5 : lecture seule via
 * {@code ArticleRepository.findTagsByArticleId}).
 */
public interface ArticleTagRepository extends JpaRepository<ArticleTag, ArticleTagId> {

    List<ArticleTag> findByIdArticleId(Long articleId);

    /**
     * Nettoyage complet des associations d'un Article avant son hard-delete
     * (DEV-11.9, {@code ArticleService.deleteDraftArticle}) — réconcilie
     * DEV-DEC-0058 (hard-delete {@code DRAFT}) avec {@code fk_article_tag_article_id
     * ON DELETE RESTRICT} (V001) : un {@code DRAFT} tagué ne peut être
     * physiquement supprimé qu'après suppression de ses {@code ArticleTag}.
     * Ne supprime jamais les {@code Tag} eux-mêmes (uniquement les lignes
     * d'association).
     */
    long deleteByIdArticleId(Long articleId);
}
