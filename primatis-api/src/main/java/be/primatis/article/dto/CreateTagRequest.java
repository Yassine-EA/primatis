package be.primatis.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contrat REST de création staff d'un {@code Tag} (DEV-11.9, {@code
 * ARTICLE_MANAGE}, DEV-DEC-0060). {@code code} : identifiant métier stable
 * et unique ({@code uq_tag_code}, V001) — jamais régénéré depuis {@code
 * label} (business-rules.md §7.13 : « Tag.code remains required, unique,
 * stable »). {@code @Size} alignés sur les longueurs réelles des colonnes
 * ({@code code VARCHAR(50)}, {@code label VARCHAR(100)}, {@code description
 * VARCHAR(255)}, V001) — DÉDUIT MÉCANIQUEMENT, aucun format supplémentaire
 * (regex/casse) imposé par aucune source. {@code description} : facultative.
 */
public record CreateTagRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String label,
        @Size(max = 255) String description) {
}
