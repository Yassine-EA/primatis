from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from hashlib import sha256

from primatis_data_seeding.mapping.models import PrimatisTitleRow


@dataclass(frozen=True)
class CopyDistribution:
    one_copy_titles: int
    two_copy_titles: int
    three_copy_titles: int
    five_copy_titles: int

    @property
    def title_count(self) -> int:
        return (
            self.one_copy_titles
            + self.two_copy_titles
            + self.three_copy_titles
            + self.five_copy_titles
        )

    @property
    def copy_count(self) -> int:
        return (
            self.one_copy_titles
            + 2 * self.two_copy_titles
            + 3 * self.three_copy_titles
            + 5 * self.five_copy_titles
        )


# Physical location convention: <level>-A<aisle>-E<shelf>, e.g. "RDC-A01-E01".
# Ground floor is "RDC"; higher floors are "ET1", "ET2", ... (unbounded, so
# capacity is never artificially capped). Several Copies legitimately share
# the same shelf (a real library does not give each exemplar its own exact
# slot) — see DEV-13.19.D §9.
LOCATION_AISLES_PER_LEVEL = 20
LOCATION_SHELVES_PER_AISLE = 10
LOCATION_COPIES_PER_SHELF = 20
_SHELF_CAPACITY = LOCATION_SHELVES_PER_AISLE * LOCATION_COPIES_PER_SHELF
_LEVEL_CAPACITY = LOCATION_AISLES_PER_LEVEL * _SHELF_CAPACITY


def _level_name(level: int) -> str:
    return "RDC" if level == 0 else f"ET{level}"


def location_for_copy(index: int) -> str:
    """Deterministic physical location for the Copy at global `index`
    (0-based). A PURE function of `index` alone — never of the profile's
    total Copy count — so the same index always yields the same location
    regardless of profile size (small/medium/large/full)."""
    if index < 0:
        raise ValueError(f"Copy index must be non-negative, got {index}.")

    level, remainder = divmod(index, _LEVEL_CAPACITY)
    aisle, remainder = divmod(remainder, _SHELF_CAPACITY)
    shelf, _ = divmod(remainder, LOCATION_COPIES_PER_SHELF)

    return f"{_level_name(level)}-A{aisle + 1:02d}-E{shelf + 1:02d}"


PROFILE_COPY_DISTRIBUTIONS: dict[str, CopyDistribution] = {
    # Intermediate profiles preserve the same 1.6 Copies/Title average while
    # staying very close to the proportions of the final dataset.
    "small": CopyDistribution(59, 28, 10, 3),        # 100 Titles / 160 Copies
    "medium": CopyDistribution(599, 268, 100, 33),   # 1 000 / 1 600
    "large": CopyDistribution(3001, 1332, 500, 167), # 5 000 / 8 000
    "full": CopyDistribution(9000, 4000, 1500, 500), # 15 000 / 24 000
}


@dataclass(frozen=True)
class PrimatisCopyRow:
    title_source_key: str
    inventory_code: str
    location: str | None
    copy_condition: str
    availability_status: str


@dataclass
class CopyGenerationResult:
    copies: list[PrimatisCopyRow] = field(default_factory=list)
    titles_by_copy_count: dict[int, int] = field(default_factory=dict)


def _stable_rank(title_source_key: str) -> tuple[str, str]:
    digest = sha256(title_source_key.encode("utf-8")).hexdigest()
    return digest, title_source_key


def _inventory_code(title_source_key: str, ordinal: int) -> str:
    digest = sha256(title_source_key.encode("utf-8")).hexdigest()[:16].upper()
    return f"PRI-C-{digest}-{ordinal:02d}"


def _copy_counts_by_title(
    titles: list[PrimatisTitleRow],
    distribution: CopyDistribution,
) -> dict[str, int]:
    if len(titles) != distribution.title_count:
        raise ValueError(
            "Title count does not match Copy distribution: "
            f"received={len(titles)} expected={distribution.title_count}."
        )

    source_keys = [title.source_key for title in titles]
    if len(source_keys) != len(set(source_keys)):
        raise ValueError("Duplicate title source_key detected before Copy generation.")

    ranked = sorted(titles, key=lambda title: _stable_rank(title.source_key))

    counts: dict[str, int] = {}
    cursor = 0

    # Scarcer multi-copy buckets are assigned first using a stable hash rank.
    # This distributes them independently from source/input ordering.
    for copies_per_title, bucket_size in (
        (5, distribution.five_copy_titles),
        (3, distribution.three_copy_titles),
        (2, distribution.two_copy_titles),
        (1, distribution.one_copy_titles),
    ):
        for title in ranked[cursor : cursor + bucket_size]:
            counts[title.source_key] = copies_per_title
        cursor += bucket_size

    if cursor != len(ranked):
        raise AssertionError("Copy distribution did not consume all Titles.")

    return counts


def generate_copies(
    titles: list[PrimatisTitleRow],
    *,
    profile: str,
) -> CopyGenerationResult:
    try:
        distribution = PROFILE_COPY_DISTRIBUTIONS[profile]
    except KeyError as exc:
        raise ValueError(f"Unknown Copy generation profile: {profile!r}.") from exc

    counts_by_title = _copy_counts_by_title(titles, distribution)
    result = CopyGenerationResult()

    inventory_codes: set[str] = set()
    copy_index = 0

    for title in sorted(titles, key=lambda item: item.source_key):
        copy_count = counts_by_title[title.source_key]

        for ordinal in range(1, copy_count + 1):
            inventory_code = _inventory_code(title.source_key, ordinal)

            if len(inventory_code) > 50:
                raise AssertionError("Generated inventoryCode exceeds VARCHAR(50).")
            if inventory_code in inventory_codes:
                raise ValueError(
                    f"Generated duplicate inventoryCode: {inventory_code}."
                )

            inventory_codes.add(inventory_code)
            result.copies.append(
                PrimatisCopyRow(
                    title_source_key=title.source_key,
                    inventory_code=inventory_code,
                    location=location_for_copy(copy_index),
                    copy_condition="GOOD",
                    availability_status="AVAILABLE",
                )
            )
            copy_index += 1

    result.titles_by_copy_count = dict(
        sorted(Counter(counts_by_title.values()).items())
    )

    if len(result.copies) != distribution.copy_count:
        raise AssertionError(
            "Generated Copy count does not match profile target: "
            f"generated={len(result.copies)} expected={distribution.copy_count}."
        )

    return result
