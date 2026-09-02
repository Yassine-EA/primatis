from pathlib import Path

from openpyxl import Workbook
import pytest
import xlwt

from primatis_data_seeding.reference.bpost import load_bpost_localities


def _write_xlsx(path: Path, rows):
    wb = Workbook()
    ws = wb.active
    for row in rows:
        ws.append(row)
    wb.save(path)
    wb.close()


def _write_xls(path: Path, rows):
    wb = xlwt.Workbook()
    ws = wb.add_sheet("Sheet1")
    for r, row in enumerate(rows):
        for c, value in enumerate(row):
            ws.write(r, c, value)
    wb.save(str(path))


def test_loads_xlsx_and_sorts(tmp_path: Path):
    path = tmp_path / "bpost.xlsx"
    _write_xlsx(path, [
        ("Code postal", "Localité"),
        ("6000", "Charleroi"),
        ("1000", "Bruxelles"),
    ])
    rows = load_bpost_localities(path)
    assert [(r.postal_code, r.locality) for r in rows] == [
        ("1000", "Bruxelles"),
        ("6000", "Charleroi"),
    ]


def test_accepts_official_bpost_xls_headers_with_numeric_postal_codes(tmp_path: Path):
    path = tmp_path / "zipcodes_num_fr_2025.xls"
    _write_xls(path, [
        ("Code", "Localite", "Commune principale", "Province"),
        (1040, "Etterbeek", "Etterbeek", ""),
        (6000, "Charleroi", "Charleroi", "Hainaut"),
    ])
    rows = load_bpost_localities(path)
    assert [(r.postal_code, r.locality) for r in rows] == [
        ("1040", "Etterbeek"),
        ("6000", "Charleroi"),
    ]


def test_numeric_postal_code_is_zero_padded_to_four_digits(tmp_path: Path):
    path = tmp_path / "bpost.xls"
    _write_xls(path, [
        ("Code", "Localite"),
        (900, "Test Locality"),
    ])
    rows = load_bpost_localities(path)
    assert rows[0].postal_code == "0900"


def test_accepts_dutch_headers_and_deduplicates(tmp_path: Path):
    path = tmp_path / "bpost.xlsx"
    _write_xlsx(path, [
        ("Postcode", "Gemeente"),
        ("6000", "Charleroi"),
        ("6000", "Charleroi"),
    ])
    assert len(load_bpost_localities(path)) == 1


def test_ignores_invalid_postal_rows(tmp_path: Path):
    path = tmp_path / "bpost.xlsx"
    _write_xlsx(path, [
        ("Code", "Localite"),
        ("600", "Invalid"),
        ("6000", "Charleroi"),
    ])
    rows = load_bpost_localities(path)
    assert len(rows) == 1
    assert rows[0].postal_code == "6000"


def test_rejects_unsupported_format(tmp_path: Path):
    path = tmp_path / "bpost.csv"
    path.write_text("Code,Localite\n6000,Charleroi\n", encoding="utf-8")
    with pytest.raises(ValueError, match="Unsupported Bpost workbook format"):
        load_bpost_localities(path)
