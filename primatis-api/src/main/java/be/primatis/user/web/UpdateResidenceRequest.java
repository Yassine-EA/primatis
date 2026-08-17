package be.primatis.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Contrat REST de définition/remplacement de la résidence courante (DEV-05.8,
 * {@code PUT /api/v1/me/residence} et {@code PUT /api/v1/users/{id}/residence}).
 * Le client ne fournit jamais {@code userId}, {@code addressId}, {@code
 * startDate} ni {@code endDate} : {@code startDate} est toujours la date du
 * jour côté backend (DEV-05.8-DEC-05), {@code endDate} est toujours {@code
 * null} pour la nouvelle résidence, et {@code userId}/{@code addressId} sont
 * dérivés côté Service (cible + nouvelle Address créée à partir des champs
 * ci-dessous, jamais réutilisée — DEV-05.8-DEC-02).
 *
 * Tailles alignées sur les colonnes {@code address} de V001 ({@code street}
 * VARCHAR(255), {@code street_number} VARCHAR(20), {@code box_number}
 * VARCHAR(20), {@code additional_info} VARCHAR(255)).
 */
public record UpdateResidenceRequest(
        @NotNull Long cityId,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 20) String streetNumber,
        @Size(max = 20) String boxNumber,
        @Size(max = 255) String additionalInfo) {
}
