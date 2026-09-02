from primatis_data_seeding.load.postgres import (
    ADVISORY_LOCK_KEY,
    SEED_INVENTORY_PREFIX,
)


def test_seeder_inventory_namespace_is_reserved() -> None:
    assert SEED_INVENTORY_PREFIX == "PRI-C-"


def test_advisory_lock_key_is_stable() -> None:
    assert isinstance(ADVISORY_LOCK_KEY, int)
    assert ADVISORY_LOCK_KEY > 0
