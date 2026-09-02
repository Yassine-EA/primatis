from primatis_data_seeding.load.users_scenarios import (
    SEED_MEMBER_PREFIX,
    SEED_USER_EMAIL_SUFFIX,
)


def test_seed_user_email_namespace_is_stable() -> None:
    assert SEED_USER_EMAIL_SUFFIX == "@seed.primatis.invalid"


def test_seed_member_namespace_is_stable() -> None:
    assert SEED_MEMBER_PREFIX == "M8"
