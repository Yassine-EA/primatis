from primatis_data_seeding.models import NormalizedPostalLocality
from primatis_data_seeding.validation.result import ValidationResult


def validate_postal_locality(value: NormalizedPostalLocality) -> ValidationResult:
    result = ValidationResult()

    if len(value.postal_code) != 4 or not value.postal_code.isdigit():
        result.add(
            "POSTAL_CODE_INVALID",
            "postal_code",
            "Belgian postal code must contain exactly four digits.",
        )

    if not value.locality.strip():
        result.add("LOCALITY_REQUIRED", "locality", "Locality is required.")

    if len(value.locality) > 255:
        result.add("LOCALITY_TOO_LONG", "locality", "Locality exceeds 255 characters.")

    return result
