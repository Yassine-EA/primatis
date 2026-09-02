from primatis_data_seeding.load.users_scenarios import UsersScenarioLoadSummary


def test_summary_carries_apply_mode() -> None:
    summary = UsersScenarioLoadSummary(
        users=10,
        localities=100,
        addresses=10,
        residences=10,
        loans=5,
        reservations=2,
        fines=1,
        notifications=8,
        applied=False,
    )

    assert summary.applied is False
    assert summary.users == 10
