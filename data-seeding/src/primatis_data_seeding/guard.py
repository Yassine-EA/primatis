from primatis_data_seeding.config import SeedProfile


ALLOWED_PROFILE_DATABASES = {
    "small": "primatis_dev",
    "medium": "primatis_dev",
    "large": "primatis_dev",
    "full": "primatis_preview",
}

FORBIDDEN_DATABASES = {
    "primatis_test",
}


def validate_target(profile: SeedProfile, requested_database: str) -> None:
    if requested_database in FORBIDDEN_DATABASES:
        raise ValueError(
            f"Database '{requested_database}' is forbidden for persistent data seeding."
        )

    expected_database = ALLOWED_PROFILE_DATABASES.get(profile.name)
    if expected_database is None:
        raise ValueError(f"Unknown seed profile: {profile.name}")

    if requested_database != expected_database:
        raise ValueError(
            f"Profile '{profile.name}' must target '{expected_database}', "
            f"not '{requested_database}'."
        )
