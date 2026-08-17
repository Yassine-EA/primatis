package be.primatis.catalogue.dto;

import be.primatis.catalogue.Genre;

import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code Genre} (DEV-06.3). Mapping 1:1. Ne
 * réintroduit aucun enum {@code GenreType} (abandonné de la baseline finale).
 */
public record GenreResponse(Long id, String code, String label, String description) {

    public static GenreResponse from(Genre genre) {
        Objects.requireNonNull(genre, "genre");
        return new GenreResponse(genre.getId(), genre.getCode(), genre.getLabel(), genre.getDescription());
    }
}
