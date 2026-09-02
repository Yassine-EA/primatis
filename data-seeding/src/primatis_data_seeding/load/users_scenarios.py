from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from psycopg import Connection

from primatis_data_seeding.load.postgres import ADVISORY_LOCK_KEY


SEED_USER_EMAIL_SUFFIX = "@seed.primatis.invalid"
SEED_MEMBER_PREFIX = "M8"
SEED_COPY_PREFIX = "PRI-C-"


@dataclass(frozen=True)
class UsersScenarioExportPaths:
    localities: Path
    users: Path
    addresses: Path
    residences: Path
    loans: Path
    reservations: Path
    fines: Path
    notifications: Path
    copy_states: Path


@dataclass(frozen=True)
class UsersScenarioLoadSummary:
    users: int
    localities: int
    addresses: int
    residences: int
    loans: int
    reservations: int
    fines: int
    notifications: int
    applied: bool


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _none(value: str) -> str | None:
    return value if value != "" else None


def _required_paths(root: Path) -> UsersScenarioExportPaths:
    paths = UsersScenarioExportPaths(
        root / "bpost_localities.csv", root / "users.csv", root / "addresses.csv",
        root / "residences.csv", root / "loans.csv", root / "reservations.csv",
        root / "fines.csv", root / "notifications.csv", root / "copy_states.csv",
    )
    missing = [str(path) for path in paths.__dict__.values() if not path.is_file()]
    if missing:
        raise ValueError(f"Missing users/scenario export file(s): {', '.join(missing)}")
    return paths


def _create_stage_tables(conn: Connection) -> None:
    statements = (
        """CREATE TEMP TABLE seed_locality_stage (
            postal_code VARCHAR(20) NOT NULL,
            locality VARCHAR(255) NOT NULL,
            PRIMARY KEY(postal_code, locality)
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_user_stage (
            source_key TEXT PRIMARY KEY, email VARCHAR(255) NOT NULL,
            password_hash VARCHAR(255) NOT NULL, first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL, phone_number VARCHAR(30),
            account_status VARCHAR(20) NOT NULL, member_number VARCHAR(20) NOT NULL,
            member_status VARCHAR(20) NOT NULL, registration_date DATE NOT NULL,
            member_expiration_date DATE NOT NULL, blocked_reason VARCHAR(255),
            failed_login_count INTEGER NOT NULL, role_code VARCHAR(50) NOT NULL,
            resolved_id BIGINT
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_address_stage (
            source_key TEXT PRIMARY KEY, postal_code VARCHAR(20) NOT NULL,
            locality VARCHAR(255) NOT NULL, street VARCHAR(255) NOT NULL,
            street_number VARCHAR(20) NOT NULL, box_number VARCHAR(20),
            additional_info VARCHAR(255), resolved_city_id BIGINT, resolved_id BIGINT
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_residence_stage (
            user_source_key TEXT NOT NULL, address_source_key TEXT NOT NULL,
            start_date DATE NOT NULL, end_date DATE,
            PRIMARY KEY(user_source_key,address_source_key,start_date)
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_loan_stage (
            source_key TEXT PRIMARY KEY, user_source_key TEXT NOT NULL,
            inventory_code VARCHAR(50) NOT NULL, loan_date TIMESTAMPTZ NOT NULL,
            due_date DATE NOT NULL, return_date DATE, loan_status VARCHAR(20) NOT NULL,
            notes TEXT, resolved_id BIGINT
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_reservation_stage (
            source_key TEXT PRIMARY KEY, user_source_key TEXT NOT NULL,
            title_source_key TEXT NOT NULL, title_inventory_code VARCHAR(50) NOT NULL,
            assigned_inventory_code VARCHAR(50), fulfilled_by_loan_source_key TEXT,
            reservation_date TIMESTAMPTZ NOT NULL, expiration_date TIMESTAMPTZ,
            reservation_status VARCHAR(20) NOT NULL, resolved_id BIGINT
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_fine_stage (
            source_key TEXT PRIMARY KEY, loan_source_key TEXT NOT NULL,
            amount NUMERIC(10,2) NOT NULL, reason VARCHAR(255) NOT NULL,
            issued_at TIMESTAMPTZ NOT NULL, fine_status VARCHAR(20) NOT NULL,
            paid_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ, resolved_id BIGINT
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_notification_stage (
            source_key TEXT PRIMARY KEY, recipient_user_source_key TEXT NOT NULL,
            loan_source_key TEXT, reservation_source_key TEXT, fine_source_key TEXT,
            article_source_key TEXT, notification_type VARCHAR(30) NOT NULL,
            title VARCHAR(255) NOT NULL, message TEXT NOT NULL,
            notification_status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
            read_at TIMESTAMPTZ
        ) ON COMMIT DROP""",
        """CREATE TEMP TABLE seed_copy_state_stage (
            inventory_code VARCHAR(50) PRIMARY KEY,
            availability_status VARCHAR(20) NOT NULL
        ) ON COMMIT DROP""",
    )
    with conn.cursor() as cur:
        for statement in statements:
            cur.execute(statement)


def _copy_rows(conn: Connection, statement: str, rows) -> None:
    with conn.cursor() as cur:
        with cur.copy(statement) as copy:
            for row in rows:
                copy.write_row(row)


def _stage(conn: Connection, paths: UsersScenarioExportPaths) -> tuple[int, ...]:
    localities = _read_csv(paths.localities); users = _read_csv(paths.users)
    addresses = _read_csv(paths.addresses); residences = _read_csv(paths.residences)
    loans = _read_csv(paths.loans); reservations = _read_csv(paths.reservations)
    fines = _read_csv(paths.fines); notifications = _read_csv(paths.notifications)
    copy_states = _read_csv(paths.copy_states)

    _copy_rows(conn, "COPY seed_locality_stage(postal_code,locality) FROM STDIN",
               ((r["postal_code"], r["locality"]) for r in localities))
    _copy_rows(conn, """COPY seed_user_stage
        (source_key,email,password_hash,first_name,last_name,phone_number,account_status,
         member_number,member_status,registration_date,member_expiration_date,
         blocked_reason,failed_login_count,role_code) FROM STDIN""",
        ((r["source_key"],r["email"],r["password_hash"],r["first_name"],r["last_name"],
          _none(r["phone_number"]),r["account_status"],r["member_number"],r["member_status"],
          r["registration_date"],r["member_expiration_date"],_none(r["blocked_reason"]),
          int(r["failed_login_count"]),r["role_code"]) for r in users))
    _copy_rows(conn, """COPY seed_address_stage
        (source_key,postal_code,locality,street,street_number,box_number,additional_info)
        FROM STDIN""",
        ((r["source_key"],r["postal_code"],r["locality"],r["street"],r["street_number"],
          _none(r["box_number"]),_none(r["additional_info"])) for r in addresses))
    _copy_rows(conn, "COPY seed_residence_stage(user_source_key,address_source_key,start_date,end_date) FROM STDIN",
               ((r["user_source_key"],r["address_source_key"],r["start_date"],_none(r["end_date"])) for r in residences))
    _copy_rows(conn, """COPY seed_loan_stage
        (source_key,user_source_key,inventory_code,loan_date,due_date,return_date,loan_status,notes)
        FROM STDIN""",
        ((r["source_key"],r["user_source_key"],r["inventory_code"],r["loan_date"],r["due_date"],
          _none(r["return_date"]),r["loan_status"],_none(r["notes"])) for r in loans))
    _copy_rows(conn, """COPY seed_reservation_stage
        (source_key,user_source_key,title_source_key,title_inventory_code,assigned_inventory_code,
         fulfilled_by_loan_source_key,reservation_date,expiration_date,reservation_status)
        FROM STDIN""",
        ((r["source_key"],r["user_source_key"],r["title_source_key"],r["title_inventory_code"],
          _none(r["assigned_inventory_code"]),_none(r["fulfilled_by_loan_source_key"]),
          r["reservation_date"],_none(r["expiration_date"]),r["reservation_status"]) for r in reservations))
    _copy_rows(conn, """COPY seed_fine_stage
        (source_key,loan_source_key,amount,reason,issued_at,fine_status,paid_at,cancelled_at)
        FROM STDIN""",
        ((r["source_key"],r["loan_source_key"],r["amount"],r["reason"],r["issued_at"],
          r["fine_status"],_none(r["paid_at"]),_none(r["cancelled_at"])) for r in fines))
    _copy_rows(conn, """COPY seed_notification_stage
        (source_key,recipient_user_source_key,loan_source_key,reservation_source_key,fine_source_key,
         article_source_key,notification_type,title,message,notification_status,created_at,read_at)
        FROM STDIN""",
        ((r["source_key"],r["recipient_user_source_key"],_none(r["loan_source_key"]),
          _none(r["reservation_source_key"]),_none(r["fine_source_key"]),
          _none(r["article_source_key"]),r["notification_type"],r["title"],r["message"],
          r["notification_status"],r["created_at"],_none(r["read_at"])) for r in notifications))
    _copy_rows(conn, "COPY seed_copy_state_stage(inventory_code,availability_status) FROM STDIN",
               ((r["inventory_code"],r["availability_status"]) for r in copy_states))
    return (len(localities),len(users),len(addresses),len(residences),
            len(loans),len(reservations),len(fines),len(notifications))


def _validate_stage(conn: Connection) -> None:
    with conn.cursor() as cur:
        checks = [
            ("""SELECT COUNT(*) FROM seed_user_stage
                WHERE email NOT LIKE %s OR member_number NOT LIKE %s OR role_code <> 'ROLE_MEMBER'""",
             (f"%{SEED_USER_EMAIL_SUFFIX}", f"{SEED_MEMBER_PREFIX}%"), "Synthetic User namespace violation."),
            ("""SELECT COUNT(*) FROM seed_residence_stage sr
                LEFT JOIN seed_user_stage su ON su.source_key=sr.user_source_key
                LEFT JOIN seed_address_stage sa ON sa.source_key=sr.address_source_key
                WHERE su.source_key IS NULL OR sa.source_key IS NULL""", (), "Unresolved Residence reference."),
            ("""SELECT COUNT(*) FROM seed_address_stage sa
                LEFT JOIN seed_locality_stage sl
                  ON sl.postal_code=sa.postal_code AND sl.locality=sa.locality
                WHERE sl.postal_code IS NULL""", (), "Unknown Bpost locality."),
            ("""SELECT COUNT(*) FROM seed_loan_stage sl
                LEFT JOIN seed_user_stage su ON su.source_key=sl.user_source_key
                LEFT JOIN copy c ON c.inventory_code=sl.inventory_code
                WHERE su.source_key IS NULL OR c.id IS NULL
                   OR c.inventory_code NOT LIKE 'PRI-C-%'""", (), "Unresolved/non-seeded Loan Copy."),
            ("""SELECT COUNT(*) FROM seed_reservation_stage sr
                LEFT JOIN seed_user_stage su ON su.source_key=sr.user_source_key
                LEFT JOIN copy anchor ON anchor.inventory_code=sr.title_inventory_code
                LEFT JOIN copy assigned ON assigned.inventory_code=sr.assigned_inventory_code
                WHERE su.source_key IS NULL OR anchor.id IS NULL
                   OR anchor.inventory_code NOT LIKE 'PRI-C-%'
                   OR (assigned.id IS NOT NULL AND assigned.title_id <> anchor.title_id)
                   OR (sr.reservation_status='READY' AND assigned.id IS NULL)""",
             (), "Unresolved or inconsistent Reservation Title/Copy."),
            ("""SELECT COUNT(*) FROM seed_fine_stage sf
                LEFT JOIN seed_loan_stage sl ON sl.source_key=sf.loan_source_key
                WHERE sl.source_key IS NULL""", (), "Unresolved Fine Loan."),
        ]
        for query, params, message in checks:
            if params:
                cur.execute(query, params)
            else:
                cur.execute(query)

            count = int(cur.fetchone()[0])
            if count:
                raise ValueError(f"{message} count={count}.")


def _ensure_country_and_cities(conn: Connection) -> None:
    with conn.cursor() as cur:
        cur.execute("""INSERT INTO country(id,name,code)
            SELECT nextval('country_seq'),'Belgique','BE'
            WHERE NOT EXISTS(SELECT 1 FROM country WHERE code='BE')""")
        cur.execute("SELECT id FROM country WHERE code='BE'")
        country_id = int(cur.fetchone()[0])
        cur.execute("""INSERT INTO city(id,name,postal_code,country_id)
            SELECT nextval('city_seq'),sl.locality,sl.postal_code,%s
            FROM seed_locality_stage sl
            WHERE NOT EXISTS(
                SELECT 1 FROM city c
                WHERE c.country_id=%s AND c.name=sl.locality AND c.postal_code=sl.postal_code)""",
            (country_id,country_id))
        cur.execute("""UPDATE seed_address_stage sa SET resolved_city_id=c.id
            FROM city c JOIN country co ON co.id=c.country_id
            WHERE co.code='BE' AND c.name=sa.locality AND c.postal_code=sa.postal_code""")
        cur.execute("SELECT COUNT(*) FROM seed_address_stage WHERE resolved_city_id IS NULL")
        if int(cur.fetchone()[0]):
            raise ValueError("Unable to resolve Belgian City.")


def _teardown_previous_seed(conn: Connection) -> None:
    with conn.cursor() as cur:
        cur.execute("""CREATE TEMP TABLE old_seed_users ON COMMIT DROP AS
            SELECT id FROM app_user
            WHERE email LIKE %s OR member_number LIKE %s""",
            (f"%{SEED_USER_EMAIL_SUFFIX}",f"{SEED_MEMBER_PREFIX}%"))
        cur.execute("""CREATE TEMP TABLE seeded_titles_now ON COMMIT DROP AS
            SELECT DISTINCT title_id FROM copy WHERE inventory_code LIKE 'PRI-C-%'""")
        cur.execute("""CREATE TEMP TABLE old_seed_loans ON COMMIT DROP AS
            SELECT l.id FROM loan l JOIN old_seed_users u ON u.id=l.user_id""")
        cur.execute("""CREATE TEMP TABLE old_seed_reservations ON COMMIT DROP AS
            SELECT r.id FROM reservation r JOIN old_seed_users u ON u.id=r.user_id""")
        cur.execute("""CREATE TEMP TABLE old_seed_fines ON COMMIT DROP AS
            SELECT f.id FROM fine f JOIN old_seed_loans l ON l.id=f.loan_id""")

        # Manual business dependencies on seeded catalogue => fail closed.
        cur.execute("""SELECT COUNT(*) FROM loan l
            JOIN copy c ON c.id=l.copy_id
            LEFT JOIN old_seed_users u ON u.id=l.user_id
            WHERE c.inventory_code LIKE 'PRI-C-%' AND u.id IS NULL""")
        if int(cur.fetchone()[0]):
            raise ValueError("Non-seeded user has Loan on seeded Copy; teardown aborted.")
        cur.execute("""SELECT COUNT(*) FROM reservation r
            LEFT JOIN old_seed_users u ON u.id=r.user_id
            WHERE r.title_id IN (SELECT title_id FROM seeded_titles_now) AND u.id IS NULL""")
        if int(cur.fetchone()[0]):
            raise ValueError("Non-seeded user has Reservation on seeded Title; teardown aborted.")

        # Manual Article notification to a seeded recipient must not be silently deleted.
        cur.execute("""SELECT COUNT(*) FROM notification n
            JOIN old_seed_users u ON u.id=n.recipient_user_id
            WHERE n.article_id IS NOT NULL""")
        if int(cur.fetchone()[0]):
            raise ValueError("Seeded user has Article notification; teardown aborted to preserve manual editorial data.")

        cur.execute("""DELETE FROM notification
            WHERE recipient_user_id IN (SELECT id FROM old_seed_users)
               OR loan_id IN (SELECT id FROM old_seed_loans)
               OR reservation_id IN (SELECT id FROM old_seed_reservations)
               OR fine_id IN (SELECT id FROM old_seed_fines)""")
        cur.execute("DELETE FROM fine WHERE id IN (SELECT id FROM old_seed_fines)")
        cur.execute("DELETE FROM reservation WHERE id IN (SELECT id FROM old_seed_reservations)")
        cur.execute("DELETE FROM loan WHERE id IN (SELECT id FROM old_seed_loans)")
        cur.execute("""UPDATE copy SET availability_status='AVAILABLE',updated_at=now()
            WHERE inventory_code LIKE 'PRI-C-%'""")
        cur.execute("DELETE FROM user_role WHERE user_id IN (SELECT id FROM old_seed_users)")
        cur.execute("DELETE FROM residence WHERE user_id IN (SELECT id FROM old_seed_users)")
        cur.execute("""DELETE FROM address a
            WHERE NOT EXISTS(SELECT 1 FROM residence r WHERE r.address_id=a.id)
              AND (a.street LIKE 'Rue Démo %%' OR a.street LIKE 'Avenue Exemple %%'
                OR a.street LIKE 'Chemin Test %%' OR a.street LIKE 'Allée Primatis %%'
                OR a.street LIKE 'Place Fictive %%')""")
        cur.execute("DELETE FROM app_user WHERE id IN (SELECT id FROM old_seed_users)")


def _insert_seed_users(conn: Connection) -> None:
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM role WHERE code='ROLE_MEMBER'")
        row = cur.fetchone()
        if row is None:
            raise ValueError("ROLE_MEMBER is missing.")
        role_id = int(row[0])

        cur.execute("UPDATE seed_user_stage SET resolved_id=nextval('app_user_seq')")
        cur.execute("""INSERT INTO app_user
            (id,email,password_hash,first_name,last_name,phone_number,account_status,
             member_number,member_status,registration_date,member_expiration_date,
             blocked_reason,failed_login_count,created_at,updated_at)
            SELECT resolved_id,email,password_hash,first_name,last_name,phone_number,
                   account_status,member_number,member_status,registration_date,
                   member_expiration_date,blocked_reason,failed_login_count,now(),now()
            FROM seed_user_stage ORDER BY source_key""")
        cur.execute("""INSERT INTO user_role(user_id,role_id,assigned_at,assigned_by)
            SELECT resolved_id,%s,now(),NULL FROM seed_user_stage""",(role_id,))

        cur.execute("UPDATE seed_address_stage SET resolved_id=nextval('address_seq')")
        cur.execute("""INSERT INTO address
            (id,city_id,street,street_number,box_number,additional_info)
            SELECT resolved_id,resolved_city_id,street,street_number,box_number,additional_info
            FROM seed_address_stage ORDER BY source_key""")
        cur.execute("""INSERT INTO residence(id,user_id,address_id,start_date,end_date)
            SELECT nextval('residence_seq'),su.resolved_id,sa.resolved_id,sr.start_date,sr.end_date
            FROM seed_residence_stage sr
            JOIN seed_user_stage su ON su.source_key=sr.user_source_key
            JOIN seed_address_stage sa ON sa.source_key=sr.address_source_key
            ORDER BY su.resolved_id""")


def _insert_scenarios(conn: Connection) -> None:
    with conn.cursor() as cur:
        cur.execute("UPDATE seed_loan_stage SET resolved_id=nextval('loan_seq')")
        cur.execute("""INSERT INTO loan
            (id,user_id,copy_id,loan_date,due_date,return_date,loan_status,notes,created_at,updated_at)
            SELECT sl.resolved_id,su.resolved_id,c.id,sl.loan_date,sl.due_date,
                   sl.return_date,sl.loan_status,sl.notes,now(),now()
            FROM seed_loan_stage sl
            JOIN seed_user_stage su ON su.source_key=sl.user_source_key
            JOIN copy c ON c.inventory_code=sl.inventory_code
            ORDER BY sl.resolved_id""")

        cur.execute("UPDATE seed_reservation_stage SET resolved_id=nextval('reservation_seq')")
        cur.execute("""INSERT INTO reservation
            (id,user_id,fulfilled_by_loan_id,assigned_copy_id,title_id,
             reservation_date,expiration_date,reservation_status,created_at,updated_at)
            SELECT sr.resolved_id,su.resolved_id,fl.resolved_id,assigned.id,anchor.title_id,
                   sr.reservation_date,sr.expiration_date,sr.reservation_status,now(),now()
            FROM seed_reservation_stage sr
            JOIN seed_user_stage su ON su.source_key=sr.user_source_key
            JOIN copy anchor ON anchor.inventory_code=sr.title_inventory_code
            LEFT JOIN copy assigned ON assigned.inventory_code=sr.assigned_inventory_code
            LEFT JOIN seed_loan_stage fl ON fl.source_key=sr.fulfilled_by_loan_source_key
            ORDER BY sr.resolved_id""")

        cur.execute("UPDATE seed_fine_stage SET resolved_id=nextval('fine_seq')")
        cur.execute("""INSERT INTO fine
            (id,loan_id,amount,reason,issued_at,fine_status,paid_at,cancelled_at)
            SELECT sf.resolved_id,sl.resolved_id,sf.amount,sf.reason,sf.issued_at,
                   sf.fine_status,sf.paid_at,sf.cancelled_at
            FROM seed_fine_stage sf
            JOIN seed_loan_stage sl ON sl.source_key=sf.loan_source_key
            ORDER BY sf.resolved_id""")

        cur.execute("""UPDATE copy c SET availability_status=s.availability_status,updated_at=now()
            FROM seed_copy_state_stage s WHERE c.inventory_code=s.inventory_code""")

        cur.execute("""INSERT INTO notification
            (id,recipient_user_id,loan_id,reservation_id,fine_id,article_id,
             notification_type,title,message,notification_status,created_at,read_at)
            SELECT nextval('notification_seq'),su.resolved_id,sl.resolved_id,
                   sr.resolved_id,sf.resolved_id,NULL,sn.notification_type,
                   sn.title,sn.message,sn.notification_status,sn.created_at,sn.read_at
            FROM seed_notification_stage sn
            JOIN seed_user_stage su ON su.source_key=sn.recipient_user_source_key
            LEFT JOIN seed_loan_stage sl ON sl.source_key=sn.loan_source_key
            LEFT JOIN seed_reservation_stage sr ON sr.source_key=sn.reservation_source_key
            LEFT JOIN seed_fine_stage sf ON sf.source_key=sn.fine_source_key
            ORDER BY sn.source_key""")


def load_users_and_scenarios(*, conn: Connection, export_dir: Path, apply: bool) -> UsersScenarioLoadSummary:
    paths = _required_paths(export_dir)
    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute("SELECT pg_advisory_xact_lock(%s)", (ADVISORY_LOCK_KEY,))
        _create_stage_tables(conn)
        counts = _stage(conn, paths)
        _validate_stage(conn)
        if apply:
            _teardown_previous_seed(conn)
            _ensure_country_and_cities(conn)
            _insert_seed_users(conn)
            _insert_scenarios(conn)

    return UsersScenarioLoadSummary(
        localities=counts[0], users=counts[1], addresses=counts[2],
        residences=counts[3], loans=counts[4], reservations=counts[5],
        fines=counts[6], notifications=counts[7], applied=apply,
    )
