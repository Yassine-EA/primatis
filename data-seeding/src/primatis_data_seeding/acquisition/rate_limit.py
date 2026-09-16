"""Explicit, configurable rate limiting for individual Open Library HTTP
fetches (DEV-16.2 §16.2 point 2, DEV-16.3 Étape/§12).

`acquire_records()` (openlibrary_details.py) previously issued one HTTP
request per missing key with no delay and no concurrency limit at all —
identified in DEV-16.1 §12 as a real risk of hitting Open Library
rate-limiting at large/full scale (up to ~30 000 sequential requests).

This module wraps any single-key fetch callable (`fetch_record`,
`fetch_search_payload`, ...) with a minimum interval between calls.
Priority order, per the DEV-16.3 cadrage §12: reliability + respecting
the source + resumability, before raw speed — so this is a simple
sequential throttle, never a concurrent/parallel fetcher.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Callable, TypeVar

T = TypeVar("T")


@dataclass
class RateLimiter:
    """Sequential throttle: guarantees at least `min_interval_seconds`
    between the START of two consecutive calls made through `wrap()`.

    `sleep_fn`/`clock_fn` are injectable so tests never actually sleep.
    """

    min_interval_seconds: float
    clock_fn: Callable[[], float] = time.monotonic
    sleep_fn: Callable[[float], None] = time.sleep
    _last_call_at: float | None = field(default=None, init=False, repr=False)

    def __post_init__(self) -> None:
        if self.min_interval_seconds < 0:
            raise ValueError("min_interval_seconds must be >= 0.")

    def wait(self) -> None:
        if self.min_interval_seconds == 0:
            return
        now = self.clock_fn()
        if self._last_call_at is not None:
            elapsed = now - self._last_call_at
            remaining = self.min_interval_seconds - elapsed
            if remaining > 0:
                self.sleep_fn(remaining)
                now = self.clock_fn()
        self._last_call_at = now

    def wrap(self, fn: Callable[..., T]) -> Callable[..., T]:
        def throttled(*args, **kwargs) -> T:
            self.wait()
            return fn(*args, **kwargs)

        return throttled


def with_retries(
    fn: Callable[..., T],
    *,
    max_attempts: int = 3,
    backoff_seconds: float = 5.0,
    sleep_fn: Callable[[float], None] = time.sleep,
    retry_on: tuple[type[BaseException], ...] = (OSError,),
) -> Callable[..., T]:
    """Wraps a callable with a simple fixed-backoff retry (DEV-16.4 §4.4
    — real transient network failures — connection reset, TLS handshake
    reset, timeout — were observed live against Open Library during
    DEV-16.4 pre-gate pilots; `urllib`'s `URLError`/`socket.timeout` are
    both `OSError` subclasses, the default `retry_on`).

    `max_attempts` counts the FIRST try — `max_attempts=3` means up to 2
    retries after an initial failure. The last failure is re-raised
    unchanged if every attempt fails. `sleep_fn` is injectable so tests
    never actually sleep.
    """
    if max_attempts < 1:
        raise ValueError("max_attempts must be >= 1.")

    def retrying(*args, **kwargs) -> T:
        last_exc: BaseException | None = None
        for attempt in range(max_attempts):
            try:
                return fn(*args, **kwargs)
            except retry_on as exc:
                last_exc = exc
                if attempt < max_attempts - 1:
                    sleep_fn(backoff_seconds)
        assert last_exc is not None
        raise last_exc

    return retrying
