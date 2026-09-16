import pytest

from primatis_data_seeding.load.users_scenarios_cli import build_parser


def test_only_full_profile_is_accepted() -> None:
    parser = build_parser()
    args = parser.parse_args(["--profile", "full", "--export-dir", "data/bundles/full"])
    assert args.profile == "full"


def test_other_profiles_are_rejected() -> None:
    parser = build_parser()
    with pytest.raises(SystemExit):
        parser.parse_args(["--profile", "small", "--export-dir", "data/bundles/small"])


def test_apply_flag_defaults_to_check_mode() -> None:
    parser = build_parser()
    args = parser.parse_args(["--profile", "full", "--export-dir", "data/bundles/full"])
    assert args.apply is False
