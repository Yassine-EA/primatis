"""DEV-16.G3.6 — upscale déterministe des covers < 120×160 (outil ponctuel).

Requiert Pillow (dépendance NON ajoutée au projet : outil de préparation
exécuté une fois). Source immuable : `book-covers/` du dataset. Pour chaque
cover référencée par `title_source_map.csv` : si la source respecte 120×160
elle reste octet-à-octet identique à la destination ; sinon elle est
agrandie sans crop ni déformation (LANCZOS, ratio conservé,
scale = max(120/w, 160/h), dimensions = ceil(w×scale) × ceil(h×scale)),
JPEG qualité 95. Jamais de réduction.

Complément (5e argument facultatif) : une cover conforme en taille mais dont
le contenu source n'est pas du JPEG (PNG nommé `.jpg`) est ré-encodée en
JPEG à dimensions identiques (contrat : `<ISBN>.jpg` = JPEG) et tracée dans
un second manifeste (`REENCODE_PNG_TO_JPEG`).
"""

from __future__ import annotations

import csv
import hashlib
import io
import sys
from fractions import Fraction
from math import ceil
from pathlib import Path

from PIL import Image

MIN_W, MIN_H = 120, 160
QUALITY = 95
OPERATION = "UPSCALE_MIN_120x160"
REENCODE_OPERATION = "REENCODE_PNG_TO_JPEG"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def target_size(width: int, height: int) -> tuple[int, int]:
    scale = max(Fraction(MIN_W, width), Fraction(MIN_H, height))
    return ceil(width * scale), ceil(height * scale)


def upscale(data: bytes) -> tuple[bytes, tuple[int, int], tuple[int, int]]:
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        source_size = image.size
        new_size = target_size(*source_size)
        if image.mode not in ("RGB", "L"):
            image = image.convert("RGB")
        resized = image.resize(new_size, Image.Resampling.LANCZOS)
    out = io.BytesIO()
    resized.save(out, format="JPEG", quality=QUALITY)
    return out.getvalue(), source_size, new_size


def reencode_to_jpeg(data: bytes) -> tuple[bytes, tuple[int, int]]:
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        size = image.size
        if image.mode not in ("RGB", "L"):
            image = image.convert("RGB")
        out = io.BytesIO()
        image.save(out, format="JPEG", quality=QUALITY)
    return out.getvalue(), size


def main() -> int:
    dataset_root, candidate, dest_dir, manifest = (Path(a) for a in sys.argv[1:5])
    format_manifest = Path(sys.argv[5]) if len(sys.argv) > 5 else None
    rows = []
    format_rows = []
    with (candidate / "title_source_map.csv").open("r", encoding="utf-8", newline="") as handle:
        mapping = list(csv.DictReader(handle))
    for entry in sorted(mapping, key=lambda r: r["isbn"]):
        source = dataset_root / entry["cover_path"]
        data = source.read_bytes()
        dest = dest_dir / Path(entry["cover_image_url"]).name
        with Image.open(io.BytesIO(data)) as image:
            width, height = image.size
        if width >= MIN_W and height >= MIN_H:
            if data[:2] != b"\xff\xd8":
                final, size = reencode_to_jpeg(data)
                dest.write_bytes(final)
                format_rows.append({
                    "isbn": entry["isbn"], "source_width": size[0], "source_height": size[1],
                    "final_width": size[0], "final_height": size[1],
                    "source_sha256": sha256_bytes(data), "final_sha256": sha256_bytes(final),
                    "operation": REENCODE_OPERATION,
                })
                continue
            if not dest.exists() or dest.read_bytes() != data:
                dest.write_bytes(data)
            continue
        final, source_size, new_size = upscale(data)
        dest.write_bytes(final)
        rows.append({
            "isbn": entry["isbn"],
            "source_width": source_size[0], "source_height": source_size[1],
            "final_width": new_size[0], "final_height": new_size[1],
            "source_sha256": sha256_bytes(data), "final_sha256": sha256_bytes(final),
            "operation": OPERATION,
        })
    fieldnames = (
        "isbn", "source_width", "source_height", "final_width", "final_height",
        "source_sha256", "final_sha256", "operation")
    targets = [(manifest, rows)]
    if format_manifest is not None:
        targets.append((format_manifest, format_rows))
    for path, content in targets:
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
            writer.writeheader()
            writer.writerows(content)
    print(f"upscaled={len(rows)} reencoded={len(format_rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
