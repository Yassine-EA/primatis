package be.primatis.catalogue.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Contrat REST de création staff d'un {@code Genre} (DEV-06.5.1,
 * {@code CATALOGUE_MANAGE}). {@code code}/{@code label} : {@code UNIQUE} en
 * base ({@code uq_genre_code}/{@code uq_genre_label}) — pré-vérifiés dans le
 * Service ({@code GenreRepository.existsByCode}/{@code existsByLabel}),
 * jamais laissés remonter comme {@code DataIntegrityViolationException}
 * brute. Aucun enum {@code GenreType} réintroduit.
 */
public record CreateGenreRequest(@NotBlank String code, @NotBlank String label, String description) {
}
