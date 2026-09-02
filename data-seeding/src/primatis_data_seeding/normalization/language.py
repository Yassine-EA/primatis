_LANGUAGE_MAP = {
    "fr": "FR",
    "fre": "FR",
    "fra": "FR",
    "en": "EN",
    "eng": "EN",
    "nl": "NL",
    "dut": "NL",
    "nld": "NL",
    "de": "DE",
    "ger": "DE",
    "deu": "DE",
    "es": "ES",
    "spa": "ES",
    "it": "IT",
    "ita": "IT",
    "la": "LA",
    "lat": "LA",
}


def normalize_language_code(value: object) -> str | None:
    if value is None:
        return None

    if isinstance(value, dict):
        value = value.get("key")

    text = str(value).strip().lower()
    if text.startswith("/languages/"):
        text = text.rsplit("/", 1)[-1]

    return _LANGUAGE_MAP.get(text)


def select_supported_language(values: object) -> str | None:
    if not isinstance(values, list):
        return None

    for value in values:
        normalized = normalize_language_code(value)
        if normalized is not None:
            return normalized
    return None
