package be.primatis.catalogue.dto;

import be.primatis.catalogue.Author;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code Author} (DEV-06.3). Mapping 1:1, aucune
 * transformation ni déduction. {@code fullName} n'est pas une clé métier
 * unique (aucune contrainte {@code UNIQUE} en base) : deux Authors distincts
 * peuvent légitimement porter le même nom, ce DTO n'introduit donc aucune
 * notion d'unicité.
 */
public record AuthorResponse(
        Long id,
        String fullName,
        LocalDate birthDate,
        LocalDate deathDate,
        String nationality,
        String biography) {

    public static AuthorResponse from(Author author) {
        Objects.requireNonNull(author, "author");
        return new AuthorResponse(
                author.getId(),
                author.getFullName(),
                author.getBirthDate(),
                author.getDeathDate(),
                author.getNationality(),
                author.getBiography());
    }
}
