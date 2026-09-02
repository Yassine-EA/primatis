from __future__ import annotations


ALLOWED_PROFILE_DATABASES = {
    "small": "primatis_dev",
    "medium": "primatis_dev",
    "large": "primatis_dev",
    "full": "primatis_preview",
}

FORBIDDEN_DATABASES = {"primatis_test"}


def expected_database(profile: str) -> str:
    try:
        return ALLOWED_PROFILE_DATABASES[profile]
    except KeyError as exc:
        raise ValueError(f"Unknown data-seeding profile: {profile!r}.") from exc


def validate_requested_target(profile: str, requested_database: str) -> None:
    expected = expected_database(profile)

    if requested_database in FORBIDDEN_DATABASES:
        raise ValueError(
            f"Database {requested_database!r} is forbidden for persistent data seeding."
        )

    if requested_database != expected:
        raise ValueError(
            f"Profile {profile!r} must target {expected!r}, not {requested_database!r}."
        )


def validate_live_database(
    profile: str,
    requested_database: str,
    live_database: str,
) -> None:
    validate_requested_target(profile, requested_database)

    if live_database != requested_database:
        raise ValueError(
            "Connected PostgreSQL database does not match the explicitly requested "
            f"target: requested={requested_database!r} live={live_database!r}."
        )

    if live_database not in set(ALLOWED_PROFILE_DATABASES.values()):
        raise ValueError(
            f"Database {live_database!r} is not in the persistent seeding allowlist."
        )


def require_apply_confirmation(
    *,
    apply: bool,
    confirmation: str | None,
    database: str,
) -> None:
    if not apply:
        return

    if confirmation != database:
        raise ValueError(
            "Write mode requires --confirm-database with the exact live database name."
        )
