from dataclasses import dataclass
from pathlib import Path
import tomllib


@dataclass(frozen=True)
class SeedProfile:
    name: str
    database: str
    title_target: int
    copy_target: int | None
    include_demo_scenarios: bool
    user_target: int = 0


def load_profiles(path: Path) -> dict[str, SeedProfile]:
    with path.open("rb") as handle:
        raw = tomllib.load(handle)

    profiles: dict[str, SeedProfile] = {}
    for name, values in raw["profiles"].items():
        profiles[name] = SeedProfile(
            name=name,
            database=str(values["database"]),
            title_target=int(values["title_target"]),
            copy_target=(
                int(values["copy_target"])
                if "copy_target" in values
                else None
            ),
            include_demo_scenarios=bool(values["include_demo_scenarios"]),
            user_target=int(values.get("user_target", 0)),
        )
    return profiles



