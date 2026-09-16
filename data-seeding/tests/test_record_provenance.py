import json

from primatis_data_seeding.provenance.record_provenance import (
    FieldDecision,
    build_record_provenance,
    write_provenance_jsonl,
)


def test_build_record_provenance_minimal():
    record = build_record_provenance(
        source_key="/books/OL1M",
        batch_id="2026-09-11-fr-literature-001",
        raw_source="openlibrary_search",
        raw_payload_sha256="a" * 64,
        acquired_at="2026-09-11T10:00:00+00:00",
    )
    assert record.source_key == "/books/OL1M"
    assert record.field_decisions == ()
    assert record.rejected_fields() == ()


def test_field_decisions_track_nulled_and_rejected_fields():
    record = build_record_provenance(
        source_key="/books/OL1M",
        batch_id="2026-09-11-fr-literature-001",
        raw_source="openlibrary_search",
        raw_payload_sha256="a" * 64,
        acquired_at="2026-09-11T10:00:00+00:00",
        field_decisions=(
            FieldDecision("publisher", "KEPT", "Real value from Edition detail.", "DEV-16.2 §7"),
            FieldDecision(
                "summary", "NULLED",
                "Confirmed mojibake signature.", "DEV-16.2 §6.4.a",
            ),
        ),
    )
    rejected = record.rejected_fields()
    assert len(rejected) == 1
    assert rejected[0].field == "summary"
    assert rejected[0].outcome == "NULLED"


def test_to_dict_round_trips_through_json():
    record = build_record_provenance(
        source_key="/books/OL1M",
        batch_id="batch-1",
        raw_source="openlibrary_search",
        raw_payload_sha256="a" * 64,
        acquired_at="2026-09-11T10:00:00+00:00",
        normalization_steps=("normalize_text", "select_valid_isbn"),
        enrichments_applied=("authors_dump",),
        deduplication_decision="KEPT",
    )
    payload = json.loads(json.dumps(record.to_dict()))
    assert payload["source_key"] == "/books/OL1M"
    assert payload["normalization_steps"] == ["normalize_text", "select_valid_isbn"]
    assert payload["deduplication_decision"] == "KEPT"


def test_write_provenance_jsonl_is_sorted_and_deterministic(tmp_path):
    records = [
        build_record_provenance(
            source_key="/books/OL2M", batch_id="b1", raw_source="s",
            raw_payload_sha256="b" * 64, acquired_at="2026-09-11T10:00:00+00:00",
        ),
        build_record_provenance(
            source_key="/books/OL1M", batch_id="b1", raw_source="s",
            raw_payload_sha256="a" * 64, acquired_at="2026-09-11T10:00:00+00:00",
        ),
    ]
    path = tmp_path / "provenance.jsonl"
    write_provenance_jsonl(records, path)

    lines = path.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 2
    first = json.loads(lines[0])
    second = json.loads(lines[1])
    assert first["source_key"] == "/books/OL1M"
    assert second["source_key"] == "/books/OL2M"
