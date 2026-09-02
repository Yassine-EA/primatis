import re
import unicodedata


_WHITESPACE_RE = re.compile(r"\s+")


def normalize_text(value: object) -> str | None:
    if value is None:
        return None

    text = unicodedata.normalize("NFC", str(value))
    text = _WHITESPACE_RE.sub(" ", text).strip()
    return text or None


def truncate_or_none(value: object, max_length: int) -> str | None:
    text = normalize_text(value)
    if text is None:
        return None
    return text[:max_length]
