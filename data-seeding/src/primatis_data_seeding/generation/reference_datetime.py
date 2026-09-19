"""DEV-17.3 — analyse stricte de `--reference-datetime`.

Toutes les dates de scénarios sont relatives à cet instant. Règles :

- valeur explicite obligatoire (aucun repli silencieux sur `datetime.now()`) ;
- ISO-8601 **avec fuseau** (`Z` ou `+HH:MM`) ; une date naïve est rejetée ;
- normalisée en UTC, notion utilisée par les schedulers backend
  (`Clock.systemUTC()`, `LocalDate.now(clock)`).
"""

from __future__ import annotations

from datetime import datetime, timezone


def parse_reference_datetime(value: str | None) -> datetime:
    if value is None or not value.strip():
        raise ValueError("reference_datetime is required (ISO-8601 with timezone, e.g. 2026-09-19T08:00:00Z).")
    text = value.strip()
    if text.endswith(("Z", "z")):
        text = text[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError as exc:
        raise ValueError(f"reference_datetime is not ISO-8601: {value!r}.") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError(f"reference_datetime must carry a timezone: {value!r}.")
    return parsed.astimezone(timezone.utc)
