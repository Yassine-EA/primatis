import re


_NON_ISBN_RE = re.compile(r"[^0-9Xx]")


def normalize_isbn(value: object) -> str | None:
    if value is None:
        return None
    compact = _NON_ISBN_RE.sub("", str(value)).upper()
    return compact or None


def is_valid_isbn10(value: str) -> bool:
    if len(value) != 10:
        return False
    if not value[:9].isdigit():
        return False
    if not (value[9].isdigit() or value[9] == "X"):
        return False

    total = 0
    for index, char in enumerate(value):
        digit = 10 if char == "X" else int(char)
        total += (10 - index) * digit
    return total % 11 == 0


def is_valid_isbn13(value: str) -> bool:
    if len(value) != 13 or not value.isdigit():
        return False

    total = 0
    for index, char in enumerate(value[:12]):
        digit = int(char)
        total += digit if index % 2 == 0 else 3 * digit

    check_digit = (10 - (total % 10)) % 10
    return check_digit == int(value[12])


def is_valid_isbn(value: str) -> bool:
    return is_valid_isbn10(value) or is_valid_isbn13(value)


def select_valid_isbn(
    isbn13_values: object,
    isbn10_values: object,
) -> str | None:
    for collection in (isbn13_values, isbn10_values):
        if not isinstance(collection, list):
            continue
        for raw in collection:
            normalized = normalize_isbn(raw)
            if normalized and is_valid_isbn(normalized):
                return normalized
    return None
