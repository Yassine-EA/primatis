package be.primatis.user.web;

import be.primatis.user.City;

/**
 * Contrat REST de lecture d'une {@code City} (DEV-05.8), imbriqué dans
 * {@link AddressResponse}. Aucun CRUD City n'est introduit par DEV-05.8
 * (DEV-05.8-DEC-03) : ce DTO ne sert qu'à la représentation en lecture.
 */
public record CityResponse(Long id, String name, String postalCode, CountryResponse country) {

    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getPostalCode(),
                CountryResponse.from(city.getCountry()));
    }
}
