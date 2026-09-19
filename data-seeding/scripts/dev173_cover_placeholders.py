"""DEV-17.3 (HD-9) — remplacement des couvertures entièrement noires par le visuel PRIMATIS.

Rend `primatis-web/public/assets/fallbacks/cover.svg` (visuel institutionnel
existant, jamais modifié) en JPEG et l'écrit sous `<ISBN>.jpg` pour les 3 ISBN
dont la couverture d'origine est un aplat noir (aucune alternative locale,
DEV-17.2 §I). Aucune fausse couverture n'est fabriquée.

Rendu déterministe : `rsvg-convert`
puis ImageMagick (`magick`, JPEG qualité 95, métadonnées retirées) — outils système,
hors dépendances du projet.
Idempotent : une couverture déjà égale au placeholder n'est pas réécrite ; un
fichier qui n'est ni noir/uniforme ni égal au placeholder est REFUSÉ (jamais
d'écrasement d'une vraie couverture).
"""

from __future__ import annotations

import csv
import io
import subprocess
from pathlib import Path

BLACK_COVER_ISBNS = ("9780750944021", "9781843121022", "9781904587798")
OPERATION = "PLACEHOLDER_INSTITUTIONAL"
SOURCE_ASSET = "primatis-web/public/assets/fallbacks/cover.svg"
COVER_DIR = "primatis-web/public/covers/catalogue"
QUALITY = 95
UNIFORM_STDDEV = 4.0
WIDTH = 360


def _run(command: list[str], data: bytes | None = None) -> bytes:
    return subprocess.run(command, input=data, check=True, capture_output=True).stdout


def render_placeholder_jpeg(svg_path: Path) -> bytes:
    png = _run(["rsvg-convert", "--format=png", f"--width={WIDTH}", "--background-color=white", str(svg_path)])
    return _run([
        "magick", "png:-", "-background", "white", "-alpha", "remove", "-alpha", "off",
        "-strip", "-quality", str(QUALITY), "jpg:-",
    ], png)


def is_uniform(data: bytes) -> bool:
    """Écart-type de luminance (échelle 0–255) < 4 : aplat noir/uniforme."""
    out = _run(
        ["magick", "jpg:-", "-colorspace", "Gray", "-format", "%[fx:standard_deviation*255]", "info:"],
        data,
    )
    return float(out.decode("ascii").strip()) < UNIFORM_STDDEV


def apply_placeholders(repo_root: Path) -> list[dict[str, str]]:
    svg = repo_root / SOURCE_ASSET
    placeholder = render_placeholder_jpeg(svg)
    rows: list[dict[str, str]] = []
    for isbn in BLACK_COVER_ISBNS:
        target = repo_root / COVER_DIR / f"{isbn}.jpg"
        current = target.read_bytes()
        if current != placeholder:
            if not is_uniform(current):
                raise ValueError(f"Refusing to overwrite a non-uniform cover: {target.name}")
            target.write_bytes(placeholder)
        rows.append({
            "isbn": isbn, "operation": OPERATION, "source_asset": SOURCE_ASSET,
            "output_path": f"{COVER_DIR}/{isbn}.jpg",
        })
    return rows


def write_manifest(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("isbn", "operation", "source_asset", "output_path"))
        writer.writeheader()
        writer.writerows(rows)
