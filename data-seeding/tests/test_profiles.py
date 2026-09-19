from pathlib import Path

from primatis_data_seeding.config import load_profiles


CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "profiles.toml"


def test_profiles_match_validated_targets() -> None:
    profiles = load_profiles(CONFIG_PATH)

    assert profiles["small"].database == "primatis_dev"
    assert profiles["small"].title_target == 100

    assert profiles["medium"].database == "primatis_dev"
    assert profiles["medium"].title_target == 1000

    assert profiles["large"].database == "primatis_dev"
    assert profiles["large"].title_target == 5000

    assert profiles["full"].database == "primatis_preview"
    assert profiles["full"].title_target == 15000
    assert profiles["full"].copy_target == 24000
    assert profiles["full"].include_demo_scenarios is True


def test_no_profile_targets_primatis_test() -> None:
    profiles = load_profiles(CONFIG_PATH)
    assert all(profile.database != "primatis_test" for profile in profiles.values())


def test_full_consolidated_profile_matches_human_decision() -> None:
    profiles = load_profiles(CONFIG_PATH)

    consolidated = profiles["full_consolidated"]
    assert consolidated.database == "primatis_staging"
    assert consolidated.title_target == 14600
    assert consolidated.copy_target == 24000
    assert consolidated.user_target == 1500
    assert consolidated.include_demo_scenarios is True

    # Le profil historique n'est pas modifié.
    assert profiles["full"].title_target == 15000
    assert profiles["full"].database == "primatis_preview"
