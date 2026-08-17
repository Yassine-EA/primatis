package be.primatis.user.web;

import be.primatis.user.Address;

/**
 * Contrat REST de lecture d'une {@code Address} (DEV-05.8), imbriqué dans
 * {@link ResidenceResponse}.
 */
public record AddressResponse(
        Long id,
        String street,
        String streetNumber,
        String boxNumber,
        String additionalInfo,
        CityResponse city) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getStreet(),
                address.getStreetNumber(),
                address.getBoxNumber(),
                address.getAdditionalInfo(),
                CityResponse.from(address.getCity()));
    }
}
