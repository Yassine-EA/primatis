"""DEV-17.3 — chargement de `copy_condition` (copy_states.csv) sans base réelle."""

import csv
from pathlib import Path

from primatis_data_seeding.load import users_scenarios as loader

USERS_HEADER = ("source_key,email,password_hash,first_name,last_name,phone_number,account_status,"
                "member_number,member_status,registration_date,member_expiration_date,blocked_reason,"
                "failed_login_count,role_code")


def _write(path: Path, header: str, rows: list[str] = ()) -> None:
    path.write_text("\n".join([header, *rows]) + "\n", encoding="utf-8")


def _bundle(tmp_path: Path, copy_states_header: str, rows: list[str]) -> loader.UsersScenarioExportPaths:
    _write(tmp_path / "bpost_localities.csv", "postal_code,locality")
    _write(tmp_path / "users.csv", USERS_HEADER)
    _write(tmp_path / "addresses.csv", "source_key,postal_code,locality,street,street_number,box_number,additional_info")
    _write(tmp_path / "residences.csv", "user_source_key,address_source_key,start_date,end_date")
    _write(tmp_path / "loans.csv", "source_key,user_source_key,inventory_code,loan_date,due_date,return_date,loan_status,notes")
    _write(tmp_path / "reservations.csv", "source_key,user_source_key,title_source_key,title_inventory_code,"
           "assigned_inventory_code,fulfilled_by_loan_source_key,reservation_date,expiration_date,reservation_status")
    _write(tmp_path / "fines.csv", "source_key,loan_source_key,amount,reason,issued_at,fine_status,paid_at,cancelled_at")
    _write(tmp_path / "notifications.csv", "source_key,recipient_user_source_key,loan_source_key,reservation_source_key,"
           "fine_source_key,article_source_key,notification_type,title,message,notification_status,created_at,read_at")
    _write(tmp_path / "copy_states.csv", copy_states_header, rows)
    return loader._required_paths(tmp_path)


def _captured(monkeypatch, paths):
    captured: dict[str, list] = {}

    def fake_copy_rows(conn, statement, rows):
        captured[statement.split("(")[0].strip()] = list(rows)

    monkeypatch.setattr(loader, "_copy_rows", fake_copy_rows)
    loader._stage(None, paths)
    return captured


def test_copy_condition_is_staged(tmp_path, monkeypatch) -> None:
    paths = _bundle(tmp_path, "inventory_code,availability_status,copy_condition",
                    ["PRI-C-A-01,UNAVAILABLE,LOST", "PRI-C-B-01,ON_LOAN,GOOD", "PRI-C-C-01,AVAILABLE,DAMAGED"])
    rows = _captured(monkeypatch, paths)["COPY seed_copy_state_stage"]
    assert rows == [("PRI-C-A-01", "UNAVAILABLE", "LOST"), ("PRI-C-B-01", "ON_LOAN", "GOOD"),
                    ("PRI-C-C-01", "AVAILABLE", "DAMAGED")]


def test_legacy_copy_states_without_condition_default_to_good(tmp_path, monkeypatch) -> None:
    paths = _bundle(tmp_path, "inventory_code,availability_status", ["PRI-C-A-01,ON_LOAN"])
    assert _captured(monkeypatch, paths)["COPY seed_copy_state_stage"] == [("PRI-C-A-01", "ON_LOAN", "GOOD")]


def test_condition_column_is_updated_together_with_availability() -> None:
    source = Path(loader.__file__).read_text(encoding="utf-8")
    assert "copy_condition=s.copy_condition" in source
    assert "copy_condition='GOOD'" in source  # teardown : LOST/OOS ne peut pas redevenir AVAILABLE seul
    assert "ck_copy_condition_availability" in source
