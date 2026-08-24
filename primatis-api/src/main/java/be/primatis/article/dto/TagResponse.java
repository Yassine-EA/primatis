package be.primatis.article.dto;

import be.primatis.article.Tag;

import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code Tag} (DEV-11.3). Mapping 1:1, aucune
 * transformation ni déduction — {@code Tag} ne possède aucune donnée
 * sensible à filtrer (contrairement à {@code AppUser}), les quatre champs
 * structurels ({@code id}/{@code code}/{@code label}/{@code description})
 * sont donc tous exposés, même précédent que {@code
 * catalogue.dto.GenreResponse}/{@code catalogue.dto.AuthorResponse}. Réutilisé
 * tel quel à la fois pour représenter un Tag associé à un {@link
 * ArticleResponse} et pour une future consultation Tag autonome (DEV-11.9) —
 * un seul contrat de lecture, pas de duplication.
 */
public record TagResponse(Long id, String code, String label, String description) {

    public static TagResponse from(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        return new TagResponse(tag.getId(), tag.getCode(), tag.getLabel(), tag.getDescription());
    }
}
