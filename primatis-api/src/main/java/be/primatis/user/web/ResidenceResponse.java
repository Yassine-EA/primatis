package be.primatis.user.web;

import be.primatis.user.Residence;

import java.time.LocalDate;

/**
 * Contrat REST de lecture d'une {@code Residence} (DEV-05.8, résidence
 * courante ou entrée d'historique). {@code endDate} est {@code null} pour la
 * résidence courante. N'expose jamais {@code UserResponse} (DEV-05.8-DEC-10 :
 * le lien inverse n'est pas nécessaire, la ressource est déjà accédée depuis
 * un utilisateur identifié par l'URL).
 */
public record ResidenceResponse(Long id, AddressResponse address, LocalDate startDate, LocalDate endDate) {

    public static ResidenceResponse from(Residence residence) {
        return new ResidenceResponse(
                residence.getId(),
                AddressResponse.from(residence.getAddress()),
                residence.getStartDate(),
                residence.getEndDate());
    }
}
