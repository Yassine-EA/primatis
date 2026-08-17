package be.primatis.user.web;

import be.primatis.user.Country;

/**
 * Contrat REST de lecture d'un {@code Country} (DEV-05.8), imbriqué dans
 * {@link CityResponse}. Aucun CRUD Country n'est introduit par DEV-05.8
 * (DEV-05.8-DEC-03) : ce DTO ne sert qu'à la représentation en lecture.
 */
public record CountryResponse(Long id, String name, String code) {

    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getId(), country.getName(), country.getCode());
    }
}
