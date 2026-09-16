"""Cover candidate validation (DEV-16.2 §8, DEC-16.3-06, DEV-16.3 Étape F).

Thresholds below were derived from observing the 30 real cover files
already committed under `primatis-web/public/covers/catalogue/`
(DEV-16.3 §H — `identify` run on every file):

    width  : 244-335 px  (all)
    height : 475 or 500 px (all)
    size   : 27871-47742 bytes (~27-47 KB)
    format : JPEG (100%)

Thresholds are set with headroom around these real observations rather
than as tight bounds around exactly what already exists — the 30 already
versioned covers are a very small, single-source (Open Library Search
API, `cover_i`) sample, not a claim about the full space of legitimate
Open Library cover images. No network dependency: this module only
inspects bytes already on disk/already downloaded — it never fetches
anything itself.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass

MIN_WIDTH = 120
MIN_HEIGHT = 160
MAX_WIDTH = 3000
MAX_HEIGHT = 3000
MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024  # 5 MB — the real sample tops out at ~47 KB
ACCEPTED_FORMATS = frozenset({"JPEG", "PNG"})  # PNG allowed even though 0/30 observed so far


@dataclass(frozen=True)
class CoverValidationIssue:
    code: str
    detail: str


@dataclass(frozen=True)
class CoverValidationResult:
    verdict: str  # "OK" | "REJECT"
    issues: tuple[CoverValidationIssue, ...]
    width: int | None = None
    height: int | None = None
    detected_format: str | None = None


def _detect_jpeg_dimensions(data: bytes) -> tuple[int, int] | None:
    if not data.startswith(b"\xff\xd8"):
        return None
    index = 2
    length = len(data)
    while index < length:
        if data[index] != 0xFF:
            index += 1
            continue
        marker = data[index + 1] if index + 1 < length else 0
        # SOFn markers (Start Of Frame) carry the real image dimensions.
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                      0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            if index + 9 > length:
                return None
            height = struct.unpack(">H", data[index + 5:index + 7])[0]
            width = struct.unpack(">H", data[index + 7:index + 9])[0]
            return width, height
        if marker in (0xD8, 0xD9):
            index += 2
            continue
        if index + 4 > length:
            return None
        segment_length = struct.unpack(">H", data[index + 2:index + 4])[0]
        index += 2 + segment_length
    return None


def _detect_png_dimensions(data: bytes) -> tuple[int, int] | None:
    signature = b"\x89PNG\r\n\x1a\n"
    if not data.startswith(signature) or len(data) < 24:
        return None
    width = struct.unpack(">I", data[16:20])[0]
    height = struct.unpack(">I", data[20:24])[0]
    return width, height


def _detect_format_and_dimensions(data: bytes) -> tuple[str | None, tuple[int, int] | None]:
    if data.startswith(b"\xff\xd8"):
        return "JPEG", _detect_jpeg_dimensions(data)
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "PNG", _detect_png_dimensions(data)
    return None, None


def validate_cover_candidate(data: bytes) -> CoverValidationResult:
    """Validates raw image bytes already read from disk/a downloaded
    payload — provenance, HTTP status, and download success are the
    caller's responsibility (this only judges the bytes themselves).
    """
    issues: list[CoverValidationIssue] = []

    if not data:
        return CoverValidationResult(
            "REJECT",
            (CoverValidationIssue("EMPTY_FILE", "Zero-byte file."),),
        )

    if len(data) > MAX_FILE_SIZE_BYTES:
        issues.append(CoverValidationIssue(
            "FILE_TOO_LARGE", f"{len(data)} bytes > {MAX_FILE_SIZE_BYTES} bytes.",
        ))

    detected_format, dimensions = _detect_format_and_dimensions(data)

    if detected_format is None:
        issues.append(CoverValidationIssue(
            "NOT_DECODABLE_IMAGE",
            "First bytes do not match a supported image signature "
            "(JPEG/PNG) — likely an HTML error page or a truncated download.",
        ))
        return CoverValidationResult("REJECT", tuple(issues), detected_format=None)

    if detected_format not in ACCEPTED_FORMATS:
        issues.append(CoverValidationIssue(
            "UNSUPPORTED_FORMAT", f"{detected_format!r} not in {sorted(ACCEPTED_FORMATS)}.",
        ))

    if dimensions is None:
        issues.append(CoverValidationIssue(
            "NOT_DECODABLE_IMAGE", "Signature recognized but dimensions could not be parsed.",
        ))
        return CoverValidationResult("REJECT", tuple(issues), detected_format=detected_format)

    width, height = dimensions
    if width < MIN_WIDTH or height < MIN_HEIGHT:
        issues.append(CoverValidationIssue(
            "IMAGE_TOO_SMALL", f"{width}x{height} below {MIN_WIDTH}x{MIN_HEIGHT}.",
        ))
    if width > MAX_WIDTH or height > MAX_HEIGHT:
        issues.append(CoverValidationIssue(
            "IMAGE_TOO_LARGE", f"{width}x{height} above {MAX_WIDTH}x{MAX_HEIGHT}.",
        ))

    verdict = "REJECT" if issues else "OK"
    return CoverValidationResult(verdict, tuple(issues), width=width, height=height, detected_format=detected_format)
