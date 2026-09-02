from argparse import ArgumentParser
from pathlib import Path

from primatis_data_seeding.config import load_profiles
from primatis_data_seeding.guard import validate_target


def build_parser() -> ArgumentParser:
    parser = ArgumentParser(description="PRIMATIS data-seeding bootstrap")
    parser.add_argument("--profile", required=True)
    parser.add_argument(
        "--database",
        help="Database target. Defaults to the database defined by the profile.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    config_path = Path(__file__).resolve().parents[2] / "config" / "profiles.toml"
    profiles = load_profiles(config_path)

    if args.profile not in profiles:
        available = ", ".join(sorted(profiles))
        raise SystemExit(f"Unknown profile '{args.profile}'. Available: {available}")

    profile = profiles[args.profile]
    database = args.database or profile.database
    validate_target(profile, database)

    print(
        f"profile={profile.name} "
        f"database={database} "
        f"title_target={profile.title_target} "
        f"copy_target={profile.copy_target} "
        f"demo_scenarios={profile.include_demo_scenarios}"
    )
    print("Bootstrap only: PostgreSQL loading is not implemented in DEV-13.3.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
