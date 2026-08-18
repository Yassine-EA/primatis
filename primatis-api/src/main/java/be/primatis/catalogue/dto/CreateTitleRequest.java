package be.primatis.catalogue.dto;

import be.primatis.catalogue.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Contrat REST de création staff d'un {@code Title} (DEV-06.5,
 * {@code CATALOGUE_MANAGE}). Volontairement absent : {@code id}, {@code
 * titleStatus} (toujours {@code ACTIVE} à la création, imposé backend),
 * {@code createdAt}/{@code updatedAt}, toute donnée {@code Copy}.
 *
 * <p>{@code authorIds} : au moins un identifiant d'Author existant — un
 * {@code Title} doit toujours posséder ≥1 Author (business-rules.md §2.2).
 * Aucune création automatique d'Author à partir d'un nom : chaque identifiant
 * doit référencer un Author déjà existant, sinon {@code 404 AUTHOR_NOT_FOUND}
 * (vérifié dans le Service, pas exprimable en Bean Validation simple).
 *
 * <p>{@code genreIds} : facultatif (aucune règle métier ne rend Genre
 * obligatoire), chaque identifiant fourni doit référencer un Genre existant
 * ({@code 404 GENRE_NOT_FOUND} sinon).
 *
 * <p>{@code isbn} : facultatif ; normalisation (blanc → absent) et unicité
 * ({@code ISBN_ALREADY_EXISTS}) vérifiées dans le Service — non exprimables
 * en Bean Validation simple sans dépendance à l'état de la base.
 *
 * <p>{@code pageCount} : {@code @Positive} accepte {@code null} (nullable
 * dans le modèle) et rejette uniquement une valeur fournie ≤ 0, reflet direct
 * de {@code ck_title_page_count_positive} (V001).
 *
 * <p>{@code publicationYear} : aucune borne — aucune contrainte réelle
 * (CHECK, documentation) ne fixe de plage pour cette colonne.
 */
public record CreateTitleRequest(
        String isbn,
        @NotBlank String title,
        String subtitle,
        String summary,
        Integer publicationYear,
        @NotNull Language language,
        @Positive Integer pageCount,
        String publisher,
        String coverImageUrl,
        @NotEmpty List<Long> authorIds,
        List<Long> genreIds) {
}
