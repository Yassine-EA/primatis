from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import psycopg
from psycopg import Connection

from primatis_data_seeding.export.catalogue_csv import CatalogueExportPaths
from primatis_data_seeding.load.guard import (
    require_apply_confirmation,
    validate_live_database,
)


SEED_INVENTORY_PREFIX = "PRI-C-"
ADVISORY_LOCK_KEY = 1_374_139_009


@dataclass(frozen=True)
class LoadSummary:
    database: str
    authors: int
    genres: int
    titles: int
    title_authors: int
    title_genres: int
    copies: int
    previous_seed_titles: int
    applied: bool


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _none(value: str) -> str | None:
    return value if value != "" else None


def _int_or_none(value: str) -> int | None:
    return int(value) if value != "" else None


def _required_export_paths(root: Path) -> CatalogueExportPaths:
    paths = CatalogueExportPaths(
        root=root,
        authors=root / "authors.csv",
        genres=root / "genres.csv",
        titles=root / "titles.csv",
        title_authors=root / "title_authors.csv",
        title_genres=root / "title_genres.csv",
        copies=root / "copies.csv",
    )
    missing = [
        str(path)
        for path in (
            paths.authors,
            paths.genres,
            paths.titles,
            paths.title_authors,
            paths.title_genres,
            paths.copies,
        )
        if not path.is_file()
    ]
    if missing:
        raise ValueError(f"Missing catalogue export file(s): {', '.join(missing)}")
    return paths


def _live_database(conn: Connection) -> str:
    with conn.cursor() as cur:
        cur.execute("SELECT current_database()")
        row = cur.fetchone()
    if row is None:
        raise RuntimeError("Unable to resolve current PostgreSQL database.")
    return str(row[0])


def _validate_schema(conn: Connection) -> None:
    required_tables = {
        "author",
        "genre",
        "title",
        "title_author",
        "title_genre",
        "copy",
    }

    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_name = ANY(%s)
            """,
            (sorted(required_tables),),
        )
        present = {str(row[0]) for row in cur.fetchall()}

        missing = required_tables - present
        if missing:
            raise ValueError(
                "PRIMATIS catalogue schema is incomplete; Flyway must migrate it first. "
                f"Missing: {', '.join(sorted(missing))}."
            )

        cur.execute("SELECT to_regclass('flyway_schema_history')")
        flyway = cur.fetchone()
        if flyway is None or flyway[0] is None:
            raise ValueError(
                "flyway_schema_history is missing; Flyway must remain schema authority."
            )

        cur.execute(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE"
        )
        failed = int(cur.fetchone()[0])
        if failed:
            raise ValueError(
                f"Flyway schema history contains {failed} failed migration(s)."
            )


def _create_stage_tables(conn: Connection) -> None:
    statements = (
        """
        CREATE TEMP TABLE seed_author_stage (
            source_key TEXT PRIMARY KEY,
            full_name VARCHAR(255) NOT NULL,
            birth_date DATE,
            death_date DATE,
            nationality VARCHAR(100),
            biography TEXT,
            resolved_id BIGINT
        ) ON COMMIT DROP
        """,
        """
        CREATE TEMP TABLE seed_genre_stage (
            code VARCHAR(50) PRIMARY KEY,
            label VARCHAR(100) NOT NULL,
            description VARCHAR(255)
        ) ON COMMIT DROP
        """,
        """
        CREATE TEMP TABLE seed_title_stage (
            source_key TEXT PRIMARY KEY,
            isbn VARCHAR(20),
            title VARCHAR(500) NOT NULL,
            subtitle VARCHAR(500),
            summary TEXT,
            publication_year INTEGER,
            language VARCHAR(5) NOT NULL,
            page_count INTEGER,
            publisher VARCHAR(255),
            cover_image_url VARCHAR(500),
            title_status VARCHAR(20) NOT NULL,
            resolved_id BIGINT
        ) ON COMMIT DROP
        """,
        """
        CREATE TEMP TABLE seed_title_author_stage (
            title_source_key TEXT NOT NULL,
            author_source_key TEXT NOT NULL,
            PRIMARY KEY (title_source_key, author_source_key)
        ) ON COMMIT DROP
        """,
        """
        CREATE TEMP TABLE seed_title_genre_stage (
            title_source_key TEXT NOT NULL,
            genre_code VARCHAR(50) NOT NULL,
            PRIMARY KEY (title_source_key, genre_code)
        ) ON COMMIT DROP
        """,
        """
        CREATE TEMP TABLE seed_copy_stage (
            title_source_key TEXT NOT NULL,
            inventory_code VARCHAR(50) PRIMARY KEY,
            location VARCHAR(255),
            copy_condition VARCHAR(20) NOT NULL,
            availability_status VARCHAR(20) NOT NULL
        ) ON COMMIT DROP
        """,
    )
    with conn.cursor() as cur:
        for statement in statements:
            cur.execute(statement)


def _copy_rows(
    conn: Connection,
    statement: str,
    rows: Iterable[tuple[object, ...]],
) -> None:
    with conn.cursor() as cur:
        with cur.copy(statement) as copy:
            for row in rows:
                copy.write_row(row)


def _stage_export(conn: Connection, paths: CatalogueExportPaths) -> tuple[int, ...]:
    authors = _read_csv(paths.authors)
    genres = _read_csv(paths.genres)
    titles = _read_csv(paths.titles)
    title_authors = _read_csv(paths.title_authors)
    title_genres = _read_csv(paths.title_genres)
    copies = _read_csv(paths.copies)

    _copy_rows(
        conn,
        """
        COPY seed_author_stage
            (source_key, full_name, birth_date, death_date, nationality, biography)
        FROM STDIN
        """,
        (
            (
                row["source_key"],
                row["full_name"],
                _none(row["birth_date"]),
                _none(row["death_date"]),
                _none(row["nationality"]),
                _none(row["biography"]),
            )
            for row in authors
        ),
    )
    _copy_rows(
        conn,
        "COPY seed_genre_stage (code, label, description) FROM STDIN",
        (
            (row["code"], row["label"], _none(row["description"]))
            for row in genres
        ),
    )
    _copy_rows(
        conn,
        """
        COPY seed_title_stage
            (source_key, isbn, title, subtitle, summary, publication_year,
             language, page_count, publisher, cover_image_url, title_status)
        FROM STDIN
        """,
        (
            (
                row["source_key"],
                _none(row["isbn"]),
                row["title"],
                _none(row["subtitle"]),
                _none(row["summary"]),
                _int_or_none(row["publication_year"]),
                row["language"],
                _int_or_none(row["page_count"]),
                _none(row["publisher"]),
                _none(row["cover_image_url"]),
                row["title_status"],
            )
            for row in titles
        ),
    )
    _copy_rows(
        conn,
        """
        COPY seed_title_author_stage
            (title_source_key, author_source_key)
        FROM STDIN
        """,
        (
            (row["title_source_key"], row["author_source_key"])
            for row in title_authors
        ),
    )
    _copy_rows(
        conn,
        "COPY seed_title_genre_stage (title_source_key, genre_code) FROM STDIN",
        (
            (row["title_source_key"], row["genre_code"])
            for row in title_genres
        ),
    )
    _copy_rows(
        conn,
        """
        COPY seed_copy_stage
            (title_source_key, inventory_code, location,
             copy_condition, availability_status)
        FROM STDIN
        """,
        (
            (
                row["title_source_key"],
                row["inventory_code"],
                _none(row["location"]),
                row["copy_condition"],
                row["availability_status"],
            )
            for row in copies
        ),
    )

    return (
        len(authors),
        len(genres),
        len(titles),
        len(title_authors),
        len(title_genres),
        len(copies),
    )


def _validate_stage(conn: Connection) -> None:
    checks = (
        (
            """
            SELECT COUNT(*)
            FROM seed_title_author_stage sta
            LEFT JOIN seed_title_stage st
              ON st.source_key = sta.title_source_key
            LEFT JOIN seed_author_stage sa
              ON sa.source_key = sta.author_source_key
            WHERE st.source_key IS NULL OR sa.source_key IS NULL
            """,
            "Unresolved staged TitleAuthor reference(s).",
        ),
        (
            """
            SELECT COUNT(*)
            FROM seed_title_genre_stage stg
            LEFT JOIN seed_title_stage st
              ON st.source_key = stg.title_source_key
            LEFT JOIN seed_genre_stage sg
              ON sg.code = stg.genre_code
            WHERE st.source_key IS NULL OR sg.code IS NULL
            """,
            "Unresolved staged TitleGenre reference(s).",
        ),
        (
            """
            SELECT COUNT(*)
            FROM seed_copy_stage sc
            LEFT JOIN seed_title_stage st
              ON st.source_key = sc.title_source_key
            WHERE st.source_key IS NULL
            """,
            "Unresolved staged Copy→Title reference(s).",
        ),
        (
            """
            SELECT COUNT(*)
            FROM seed_title_stage st
            WHERE NOT EXISTS (
                SELECT 1
                FROM seed_title_author_stage sta
                WHERE sta.title_source_key = st.source_key
            )
            """,
            "Staged Title without Author.",
        ),
        (
            """
            SELECT COUNT(*)
            FROM seed_copy_stage
            WHERE inventory_code NOT LIKE 'PRI-C-%'
            """,
            "Seeder Copy outside reserved PRI-C- inventory namespace.",
        ),
    )

    with conn.cursor() as cur:
        for query, message in checks:
            cur.execute(query)
            count = int(cur.fetchone()[0])
            if count:
                raise ValueError(f"{message} count={count}.")


def _prepare_old_seed_graph(conn: Connection) -> int:
    with conn.cursor() as cur:
        cur.execute(
            """
            CREATE TEMP TABLE old_seed_titles ON COMMIT DROP AS
            SELECT DISTINCT c.title_id
            FROM copy c
            WHERE c.inventory_code LIKE %s
            """,
            (f"{SEED_INVENTORY_PREFIX}%",),
        )
        cur.execute(
            """
            CREATE TEMP TABLE old_seed_authors ON COMMIT DROP AS
            SELECT DISTINCT ta.author_id
            FROM title_author ta
            JOIN old_seed_titles ost ON ost.title_id = ta.title_id
            """
        )
        cur.execute("SELECT COUNT(*) FROM old_seed_titles")
        previous_seed_titles = int(cur.fetchone()[0])

        # Catalogue replacement must not delete business data.
        cur.execute(
            """
            SELECT COUNT(*)
            FROM loan l
            JOIN copy c ON c.id = l.copy_id
            JOIN old_seed_titles ost ON ost.title_id = c.title_id
            """
        )
        if int(cur.fetchone()[0]):
            raise ValueError(
                "Existing seeded Copies are referenced by Loan rows. "
                "Scenario teardown must run before catalogue replacement."
            )

        cur.execute(
            """
            SELECT COUNT(*)
            FROM reservation r
            JOIN old_seed_titles ost ON ost.title_id = r.title_id
            """
        )
        if int(cur.fetchone()[0]):
            raise ValueError(
                "Existing seeded Titles are referenced by Reservation rows. "
                "Scenario teardown must run before catalogue replacement."
            )

        # An Author used by both seeded and manual/non-seeded Titles is a
        # cross-ownership dependency. Abort instead of creating a duplicate or
        # deleting manual catalogue data.
        cur.execute(
            """
            SELECT COUNT(*)
            FROM old_seed_authors osa
            WHERE EXISTS (
                SELECT 1
                FROM title_author ta
                LEFT JOIN old_seed_titles ost ON ost.title_id = ta.title_id
                WHERE ta.author_id = osa.author_id
                  AND ost.title_id IS NULL
            )
            """
        )
        if int(cur.fetchone()[0]):
            raise ValueError(
                "Seeded Author is shared with non-seeded Title(s). "
                "Catalogue replacement aborted to preserve manual data."
            )

    return previous_seed_titles


def _replace_catalogue(conn: Connection) -> None:
    with conn.cursor() as cur:
        # Preserve Genre rows already present. Only reject a label collision
        # owned by a different code, because genre.label is UNIQUE.
        cur.execute(
            """
            SELECT COUNT(*)
            FROM seed_genre_stage sg
            JOIN genre g ON g.label = sg.label AND g.code <> sg.code
            """
        )
        if int(cur.fetchone()[0]):
            raise ValueError(
                "Controlled Genre label conflicts with an existing different Genre code."
            )

        cur.execute(
            """
            DELETE FROM copy c
            USING old_seed_titles ost
            WHERE c.title_id = ost.title_id
            """
        )
        cur.execute(
            """
            DELETE FROM title_genre tg
            USING old_seed_titles ost
            WHERE tg.title_id = ost.title_id
            """
        )
        cur.execute(
            """
            DELETE FROM title_author ta
            USING old_seed_titles ost
            WHERE ta.title_id = ost.title_id
            """
        )
        cur.execute(
            """
            DELETE FROM title t
            USING old_seed_titles ost
            WHERE t.id = ost.title_id
            """
        )
        cur.execute(
            """
            DELETE FROM author a
            USING old_seed_authors osa
            WHERE a.id = osa.author_id
              AND NOT EXISTS (
                  SELECT 1 FROM title_author ta WHERE ta.author_id = a.id
              )
            """
        )

        cur.execute(
            """
            INSERT INTO genre (code, label, description)
            SELECT sg.code, sg.label, sg.description
            FROM seed_genre_stage sg
            WHERE NOT EXISTS (
                SELECT 1 FROM genre g WHERE g.code = sg.code
            )
            """
        )

        cur.execute(
            "UPDATE seed_author_stage SET resolved_id = nextval('author_seq')"
        )
        cur.execute(
            """
            INSERT INTO author
                (id, full_name, birth_date, death_date, nationality, biography)
            SELECT resolved_id, full_name, birth_date, death_date, nationality, biography
            FROM seed_author_stage
            ORDER BY source_key
            """
        )

        cur.execute(
            "UPDATE seed_title_stage SET resolved_id = nextval('title_seq')"
        )
        cur.execute(
            """
            INSERT INTO title
                (id, isbn, title, subtitle, summary, publication_year,
                 language, page_count, publisher, cover_image_url,
                 title_status, created_at, updated_at)
            SELECT resolved_id, isbn, title, subtitle, summary, publication_year,
                   language, page_count, publisher, cover_image_url,
                   title_status, now(), now()
            FROM seed_title_stage
            ORDER BY source_key
            """
        )

        cur.execute(
            """
            INSERT INTO title_author (title_id, author_id)
            SELECT st.resolved_id, sa.resolved_id
            FROM seed_title_author_stage sta
            JOIN seed_title_stage st
              ON st.source_key = sta.title_source_key
            JOIN seed_author_stage sa
              ON sa.source_key = sta.author_source_key
            ORDER BY st.resolved_id, sa.resolved_id
            """
        )
        cur.execute(
            """
            INSERT INTO title_genre (genre_id, title_id)
            SELECT g.id, st.resolved_id
            FROM seed_title_genre_stage stg
            JOIN seed_title_stage st
              ON st.source_key = stg.title_source_key
            JOIN genre g
              ON g.code = stg.genre_code
            ORDER BY st.resolved_id, g.id
            """
        )
        cur.execute(
            """
            INSERT INTO copy
                (title_id, inventory_code, location, copy_condition,
                 availability_status, created_at, updated_at)
            SELECT st.resolved_id, sc.inventory_code, sc.location,
                   sc.copy_condition, sc.availability_status, now(), now()
            FROM seed_copy_stage sc
            JOIN seed_title_stage st
              ON st.source_key = sc.title_source_key
            ORDER BY sc.inventory_code
            """
        )


def load_catalogue_export(
    *,
    export_dir: Path,
    profile: str,
    requested_database: str,
    apply: bool,
    confirmation: str | None = None,
    conninfo: str = "",
) -> LoadSummary:
    require_apply_confirmation(
        apply=apply,
        confirmation=confirmation,
        database=requested_database,
    )
    paths = _required_export_paths(export_dir)

    with psycopg.connect(conninfo) as conn:
        live_database = _live_database(conn)
        validate_live_database(profile, requested_database, live_database)
        _validate_schema(conn)

        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute("SELECT pg_advisory_xact_lock(%s)", (ADVISORY_LOCK_KEY,))

            _create_stage_tables(conn)
            counts = _stage_export(conn, paths)
            _validate_stage(conn)
            previous_seed_titles = _prepare_old_seed_graph(conn)

            if apply:
                _replace_catalogue(conn)
            else:
                # CHECK mode is intentionally read-only with respect to
                # persistent tables. TEMP staging disappears at transaction end.
                pass

        return LoadSummary(
            database=live_database,
            authors=counts[0],
            genres=counts[1],
            titles=counts[2],
            title_authors=counts[3],
            title_genres=counts[4],
            copies=counts[5],
            previous_seed_titles=previous_seed_titles,
            applied=apply,
        )
