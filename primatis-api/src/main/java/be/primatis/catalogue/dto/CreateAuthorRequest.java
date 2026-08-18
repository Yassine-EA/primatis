package be.primatis.catalogue.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Contrat REST de création staff d'un {@code Author} (DEV-06.5.1,
 * {@code CATALOGUE_MANAGE}). {@code fullName} n'est jamais traité comme une
 * clé métier unique : deux Authors distincts peuvent légitimement porter le
 * même nom (homonymie) — aucun refus, aucune fusion automatique. Cohérence
 * {@code birthDate <= deathDate} (si les deux sont fournies) vérifiée dans
 * le Service, pas exprimable en Bean Validation simple (contrainte
 * croisée entre deux champs).
 */
public record CreateAuthorRequest(
        @NotBlank String fullName,
        LocalDate birthDate,
        LocalDate deathDate,
        String nationality,
        String biography) {
}
