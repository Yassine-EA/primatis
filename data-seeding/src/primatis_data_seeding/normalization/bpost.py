from primatis_data_seeding.models import NormalizedPostalLocality
from primatis_data_seeding.normalization.text import normalize_text


def normalize_postal_locality(
    postal_code: object,
    locality: object,
) -> NormalizedPostalLocality | None:
    postal = normalize_text(postal_code)
    name = normalize_text(locality)

    if postal is None or name is None:
        return None

    postal = postal.replace(" ", "")
    if len(postal) != 4 or not postal.isdigit():
        return None

    return NormalizedPostalLocality(
        postal_code=postal,
        locality=name[:255],
    )
