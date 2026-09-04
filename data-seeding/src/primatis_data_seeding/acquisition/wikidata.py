"""Acquisition Wikidata par identifiant EXACT uniquement (jamais de recherche).

Utilisé pour deux types d'entités, toujours par QID exact :
  - une entité Author Wikidata (référencée par `remote_ids.wikidata` d'un
    record Author Open Library déjà enrichi — jamais dérivée du nom) ;
  - une entité Country Wikidata (référencée par le claim P27 de l'entité
    Author ci-dessus).

Même mécanique que `acquisition/openlibrary_details.py` (fetch exact-clé,
cache par identifiant, manifeste SHA-256, snapshot JSONL trié déterministe,
idempotence tant que `refresh` n'est pas demandé).
"""

from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Callable
from urllib.request import Request, urlopen

from primatis_data_seeding.acquisition.provenance import sha256_file


WIKIDATA_BASE_URL = "https://www.wikidata.org/wiki/Special:EntityData"

_QID_RE_MIN_LENGTH = 2  # "Q" + at least one digit


def _validate_qid(qid: str) -> None:
    if not isinstance(qid, str) or len(qid) < _QID_RE_MIN_LENGTH:
        raise ValueError(f"Invalid Wikidata QID: {qid!r}.")
    if qid[0] != "Q" or not qid[1:].isdigit():
        raise ValueError(f"Invalid Wikidata QID: {qid!r}.")


def fetch_entity(
    qid: str,
    *,
    contact: str,
    timeout_seconds: int = 30,
    base_url: str = WIKIDATA_BASE_URL,
) -> dict:
    """Fetches exactly ONE Wikidata entity by its EXACT QID. Never a search."""
    _validate_qid(qid)
    if not contact or "@" not in contact:
        raise ValueError(
            "Wikidata contact must be an email address for an identified User-Agent."
        )

    request = Request(
        f"{base_url}/{qid}.json",
        headers={
            "Accept": "application/json",
            "User-Agent": f"PRIMATIS-Data-Seeding/0.1 ({contact})",
        },
    )
    with urlopen(request, timeout=timeout_seconds) as response:
        payload = json.load(response)

    if not isinstance(payload, dict):
        raise ValueError(f"Unexpected Wikidata payload for {qid}.")
    entities = payload.get("entities")
    if not isinstance(entities, dict) or qid not in entities:
        raise ValueError(f"Wikidata payload for {qid} does not contain that entity.")
    entity = entities[qid]
    if not isinstance(entity, dict):
        raise ValueError(f"Unexpected Wikidata entity payload for {qid}.")
    return entity


def _cache_filename(qid: str) -> str:
    _validate_qid(qid)
    return f"{qid}.json"


def cache_path(cache_dir: Path, qid: str) -> Path:
    return cache_dir / _cache_filename(qid)


def has_cached_entity(cache_dir: Path, qid: str) -> bool:
    return cache_path(cache_dir, qid).is_file()


def load_cached_entity(cache_dir: Path, qid: str) -> dict:
    path = cache_path(cache_dir, qid)
    if not path.is_file():
        raise ValueError(f"Cached Wikidata entity not found: {path}")
    entity = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(entity, dict):
        raise ValueError(f"Invalid cached Wikidata entity: {path}")
    return entity


def save_cached_entity(cache_dir: Path, qid: str, entity: dict) -> Path:
    path = cache_path(cache_dir, qid)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(entity, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    return path


def acquire_entities(
    qids: set[str],
    *,
    cache_dir: Path,
    contact: str,
    refresh: bool = False,
    fetcher: Callable[..., dict] = fetch_entity,
) -> tuple[dict[str, dict], dict]:
    """Acquires exactly the given EXACT QIDs, one Wikidata entity each.

    A rerun without `refresh` reuses every already-cached QID and performs
    NO network call at all when every QID is already cached (idempotent).
    `refresh=True` forces a live re-fetch of every requested QID.
    """
    entities: dict[str, dict] = {}
    reused_qids: list[str] = []
    fetched_qids: list[str] = []

    for qid in sorted(qids):
        if not refresh and has_cached_entity(cache_dir, qid):
            entities[qid] = load_cached_entity(cache_dir, qid)
            reused_qids.append(qid)
            continue

        entity = fetcher(qid, contact=contact)
        save_cached_entity(cache_dir, qid, entity)
        entities[qid] = entity
        fetched_qids.append(qid)

    manifest = {
        "source": "Wikidata",
        "requested_qids": sorted(qids),
        "reused_qids": sorted(reused_qids),
        "fetched_qids": sorted(fetched_qids),
        "sha256": {
            qid: sha256_file(cache_path(cache_dir, qid)) for qid in sorted(entities)
        },
        "acquired_at": datetime.now(timezone.utc).isoformat(),
    }
    manifest_path = cache_dir / "manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return entities, manifest


def write_entities_snapshot(entities: dict[str, dict], path: Path) -> None:
    """Deterministic, QID-sorted JSONL pin of acquired entities."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for qid in sorted(entities):
            entity = entities[qid]
            payload = entity if entity.get("id") == qid else {**entity, "id": qid}
            handle.write(json.dumps(payload, ensure_ascii=False, sort_keys=True) + "\n")


def load_entities_snapshot(path: Path) -> dict[str, dict]:
    if not path.is_file():
        raise ValueError(f"Wikidata entities snapshot not found: {path}")

    entities: dict[str, dict] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                entity = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"Invalid Wikidata entities snapshot at {path}:{line_number}."
                ) from exc
            if not isinstance(entity, dict) or not isinstance(entity.get("id"), str):
                raise ValueError(f"Invalid Wikidata entity at {path}:{line_number}.")

            qid = entity["id"]
            if qid in entities:
                raise ValueError(f"Duplicate QID in Wikidata entities snapshot: {qid}.")
            entities[qid] = entity

    return entities
