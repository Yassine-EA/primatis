import pytest

from primatis_data_seeding.acquisition.rate_limit import RateLimiter, with_retries


class FakeClock:
    def __init__(self, start: float = 0.0):
        self.now = start
        self.sleeps: list[float] = []

    def time(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


def test_zero_interval_never_sleeps():
    clock = FakeClock()
    limiter = RateLimiter(0.0, clock_fn=clock.time, sleep_fn=clock.sleep)
    limiter.wait()
    limiter.wait()
    limiter.wait()
    assert clock.sleeps == []


def test_first_call_never_sleeps():
    clock = FakeClock()
    limiter = RateLimiter(1.0, clock_fn=clock.time, sleep_fn=clock.sleep)
    limiter.wait()
    assert clock.sleeps == []


def test_second_call_sleeps_remaining_interval():
    clock = FakeClock()
    limiter = RateLimiter(1.0, clock_fn=clock.time, sleep_fn=clock.sleep)
    limiter.wait()
    clock.now += 0.4
    limiter.wait()
    assert clock.sleeps == [0.6]


def test_call_after_interval_already_elapsed_does_not_sleep():
    clock = FakeClock()
    limiter = RateLimiter(1.0, clock_fn=clock.time, sleep_fn=clock.sleep)
    limiter.wait()
    clock.now += 5.0
    limiter.wait()
    assert clock.sleeps == []


def test_negative_interval_is_rejected():
    import pytest

    with pytest.raises(ValueError):
        RateLimiter(-1.0)


def test_wrap_throttles_a_fetch_function():
    clock = FakeClock()
    limiter = RateLimiter(1.0, clock_fn=clock.time, sleep_fn=clock.sleep)
    calls: list[str] = []

    def fetch(key: str, *, contact: str) -> dict:
        calls.append(key)
        return {"key": key}

    throttled = limiter.wrap(fetch)
    throttled("/works/OL1W", contact="dev@primatis.local")
    clock.now += 0.2
    throttled("/works/OL2W", contact="dev@primatis.local")

    assert calls == ["/works/OL1W", "/works/OL2W"]
    assert clock.sleeps == [0.8]


def test_wrap_composes_with_acquire_records_fetcher_parameter(tmp_path):
    # acquire_records() already accepts an injectable `fetcher` callable —
    # RateLimiter composes with it directly, no change to
    # openlibrary_details.py needed.
    from primatis_data_seeding.acquisition.openlibrary_details import acquire_records

    clock = FakeClock()
    limiter = RateLimiter(1.0, clock_fn=clock.time, sleep_fn=clock.sleep)
    calls: list[str] = []

    def fake_fetch(key: str, *, contact: str) -> dict:
        calls.append(key)
        clock.now += 0.1
        return {"key": key, "title": f"Title for {key}"}

    records, manifest = acquire_records(
        {"/works/OL1W", "/works/OL2W"},
        cache_dir=tmp_path,
        contact="dev@primatis.local",
        fetcher=limiter.wrap(fake_fetch),
    )

    assert len(records) == 2
    assert calls == ["/works/OL1W", "/works/OL2W"]
    # one throttle applied between the two real fetches
    assert clock.sleeps == [0.9]


# --- with_retries (DEV-16.4 §4.4: real transient network failures) -----


def test_with_retries_succeeds_first_try_without_sleeping():
    sleeps: list[float] = []
    calls: list[int] = []

    def fn():
        calls.append(1)
        return "ok"

    wrapped = with_retries(fn, max_attempts=3, backoff_seconds=5.0, sleep_fn=sleeps.append)
    assert wrapped() == "ok"
    assert len(calls) == 1
    assert sleeps == []


def test_with_retries_recovers_after_transient_failures():
    sleeps: list[float] = []
    attempts: list[int] = []

    def flaky():
        attempts.append(1)
        if len(attempts) < 3:
            raise ConnectionResetError("transient")
        return "ok"

    wrapped = with_retries(flaky, max_attempts=3, backoff_seconds=5.0, sleep_fn=sleeps.append)
    assert wrapped() == "ok"
    assert len(attempts) == 3
    assert sleeps == [5.0, 5.0]


def test_with_retries_reraises_after_exhausting_attempts():
    attempts: list[int] = []

    def always_fails():
        attempts.append(1)
        raise TimeoutError("still down")

    wrapped = with_retries(always_fails, max_attempts=3, backoff_seconds=1.0, sleep_fn=lambda s: None)
    with pytest.raises(TimeoutError):
        wrapped()
    assert len(attempts) == 3


def test_with_retries_does_not_catch_unrelated_exceptions():
    def fn():
        raise ValueError("not a network error")

    wrapped = with_retries(fn, max_attempts=3, retry_on=(OSError,), sleep_fn=lambda s: None)
    with pytest.raises(ValueError):
        wrapped()


def test_with_retries_rejects_non_positive_max_attempts():
    with pytest.raises(ValueError):
        with_retries(lambda: None, max_attempts=0)


def test_with_retries_passes_through_arguments_and_kwargs():
    def fn(a, *, b):
        return a + b

    wrapped = with_retries(fn, sleep_fn=lambda s: None)
    assert wrapped(1, b=2) == 3
