"""Per-record provenance (DEV-16.2 §10.2, DEV-16.3 Étape K).

Traces one accepted Title back to its source, batch, raw payload and the
transformations/decisions that produced it — entirely inside
`data-seeding/`, never inside the 23 PRIMATIS tables (CLAUDE.md §12,
`.claude/rules/database.md`).

This is a reporting/audit artifact, not a runtime dependency: nothing in
`primatis-api`/`primatis-web` ever reads it.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class FieldDecision:
    """One quality/policy decision applied to a single field."""

    field: str
    outcome: str  # "KEPT" | "NULLED" | "REJECTED"
    reason: str
    rule_reference: str  # e.g. "DEV-16.2 §6.4.a"


@dataclass(frozen=True)
class RecordProvenance:
    source_key: str
    batch_id: str
    raw_source: str  # e.g. "openlibrary_search", "openlibrary_dump_authors"
    raw_payload_sha256: str
    acquired_at: str  # ISO 8601 UTC
    normalization_steps: tuple[str, ...]
    enrichments_applied: tuple[str, ...]
    field_decisions: tuple[FieldDecision, ...] = field(default_factory=tuple)
    deduplication_decision: str | None = None  # e.g. "KEPT", "MERGED_INTO:<key>"

    def rejected_fields(self) -> tuple[FieldDecision, ...]:
        return tuple(d for d in self.field_decisions if d.outcome != "KEPT")

    def to_dict(self) -> dict:
        return {
            "source_key": self.source_key,
            "batch_id": self.batch_id,
            "raw_source": self.raw_source,
            "raw_payload_sha256": self.raw_payload_sha256,
            "acquired_at": self.acquired_at,
            "normalization_steps": list(self.normalization_steps),
            "enrichments_applied": list(self.enrichments_applied),
            "field_decisions": [
                {
                    "field": d.field,
                    "outcome": d.outcome,
                    "reason": d.reason,
                    "rule_reference": d.rule_reference,
                }
                for d in self.field_decisions
            ],
            "deduplication_decision": self.deduplication_decision,
        }


def build_record_provenance(
    *,
    source_key: str,
    batch_id: str,
    raw_source: str,
    raw_payload_sha256: str,
    acquired_at: str,
    normalization_steps: tuple[str, ...] = (),
    enrichments_applied: tuple[str, ...] = (),
    field_decisions: tuple[FieldDecision, ...] = (),
    deduplication_decision: str | None = None,
) -> RecordProvenance:
    return RecordProvenance(
        source_key=source_key,
        batch_id=batch_id,
        raw_source=raw_source,
        raw_payload_sha256=raw_payload_sha256,
        acquired_at=acquired_at,
        normalization_steps=normalization_steps,
        enrichments_applied=enrichments_applied,
        field_decisions=field_decisions,
        deduplication_decision=deduplication_decision,
    )


def write_provenance_jsonl(records: list[RecordProvenance], path) -> None:
    import json

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for record in sorted(records, key=lambda r: r.source_key):
            handle.write(json.dumps(record.to_dict(), ensure_ascii=False, sort_keys=True) + "\n")
