from pathlib import Path

import pytest

from primatis_data_seeding.normalization.cover_validation import (
    MAX_FILE_SIZE_BYTES,
    validate_cover_candidate,
)

_REAL_COVERS_DIR = (
    Path(__file__).resolve().parents[2]
    / "primatis-web" / "public" / "covers" / "catalogue"
)


def test_empty_bytes_is_rejected():
    result = validate_cover_candidate(b"")
    assert result.verdict == "REJECT"
    assert result.issues[0].code == "EMPTY_FILE"


def test_html_error_page_disguised_as_jpg_is_rejected():
    # The realistic failure mode this guards against: a CDN returning an
    # HTTP 200 with an HTML error body instead of the actual image.
    html_body = b"<html><body>404 Not Found</body></html>"
    result = validate_cover_candidate(html_body)
    assert result.verdict == "REJECT"
    assert result.issues[0].code == "NOT_DECODABLE_IMAGE"


def test_truncated_jpeg_header_is_rejected():
    truncated = b"\xff\xd8\xff"
    result = validate_cover_candidate(truncated)
    assert result.verdict == "REJECT"


def test_oversized_file_is_rejected():
    # Not a real image, but large enough to trip the size gate before
    # even reaching format detection is irrelevant here — this checks
    # the size gate fires independently by using a real small JPEG
    # padded past the size limit is impractical; instead assert the
    # gate itself using a byte string that is not a real image at all
    # sized to exceed MAX_FILE_SIZE_BYTES, and confirm FILE_TOO_LARGE is
    # among the reported issues even though NOT_DECODABLE_IMAGE also
    # fires (both issues are legitimate for this payload).
    oversized_garbage = b"x" * (MAX_FILE_SIZE_BYTES + 1)
    result = validate_cover_candidate(oversized_garbage)
    assert result.verdict == "REJECT"
    codes = {issue.code for issue in result.issues}
    assert "FILE_TOO_LARGE" in codes


@pytest.mark.skipif(
    not _REAL_COVERS_DIR.is_dir(), reason="real covers directory not present"
)
def test_every_real_committed_cover_passes_validation():
    # DEV-16.3 §H/§F: validates the thresholds against the 30 real cover
    # files already committed and served by primatis-web (read-only
    # inspection only — this test never writes to primatis-web/).
    files = sorted(_REAL_COVERS_DIR.glob("*.jpg"))
    assert len(files) >= 1, "expected the real committed cover files to be present"

    for path in files:
        data = path.read_bytes()
        result = validate_cover_candidate(data)
        assert result.verdict == "OK", f"{path.name}: {result.issues}"
        assert result.detected_format == "JPEG"
        assert result.width is not None and result.height is not None
