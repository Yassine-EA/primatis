import pytest
from datetime import datetime, timedelta, timezone

from primatis_data_seeding.generation.reference_datetime import parse_reference_datetime


def test_parses_z_suffix_as_utc() -> None:
    parsed = parse_reference_datetime("2026-09-19T08:00:00Z")
    assert parsed == datetime(2026, 9, 19, 8, tzinfo=timezone.utc)
    assert parsed.utcoffset() == timedelta(0)


def test_offset_is_normalised_to_utc() -> None:
    parsed = parse_reference_datetime("2026-09-19T10:00:00+02:00")
    assert parsed == datetime(2026, 9, 19, 8, tzinfo=timezone.utc)
    assert parsed.tzinfo == timezone.utc


@pytest.mark.parametrize("value", [None, "", "   "])
def test_value_is_mandatory_no_silent_now_fallback(value) -> None:
    with pytest.raises(ValueError, match="required"):
        parse_reference_datetime(value)


def test_naive_datetime_is_rejected() -> None:
    with pytest.raises(ValueError, match="timezone"):
        parse_reference_datetime("2026-09-19T08:00:00")


def test_garbage_is_rejected() -> None:
    with pytest.raises(ValueError, match="ISO-8601"):
        parse_reference_datetime("demain matin")


def test_scenario_script_requires_the_cli_option(capsys) -> None:
    import importlib.util, sys
    from pathlib import Path

    path = Path(__file__).resolve().parents[1] / "scripts" / "dev34_build_consolidated_scenarios.py"
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location("dev34_cli", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    with pytest.raises(SystemExit):
        module.build_parser().parse_args(["--base-dir", "a", "--output-dir", "b"])
    source = path.read_text(encoding="utf-8")
    assert "scenarios_build_report" not in source.replace("DEV-16", "")
    assert "datetime.now" not in source
