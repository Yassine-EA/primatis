-- PRIMATIS — V001 — Schéma relationnel initial
--
-- Crée les 23 tables de la baseline relationnelle PRIMATIS, leurs séquences,
-- clés primaires/étrangères, contraintes d'unicité et de domaine, index
-- uniques partiels d'intégrité, ainsi que le bootstrap obligatoire des
-- paramètres métier globaux (application_setting).
--
-- Source d'autorité des colonnes : PRIMATIS_DATA_DICTIONARY_v2.1.md,
-- consolidé avec PRIMATIS_CONTEXT_DEV_v1.0 et database-model.md.
-- Les longueurs VARCHAR, la précision NUMERIC et les noms physiques de
-- contraintes non fixés par le dictionnaire relèvent d'IMPLEMENTATION
-- FREEDOM (voir DEV-DEC-0004 dans decision-log.md).

-- =============================================================
-- 1. Référentiels géographiques : country, city, address
-- =============================================================

CREATE SEQUENCE country_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE country (
    id   BIGINT NOT NULL DEFAULT nextval('country_seq'),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(10) NOT NULL,
    CONSTRAINT pk_country PRIMARY KEY (id),
    CONSTRAINT uq_country_code UNIQUE (code)
);

ALTER SEQUENCE country_seq OWNED BY country.id;

CREATE SEQUENCE city_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE city (
    id          BIGINT NOT NULL DEFAULT nextval('city_seq'),
    name        VARCHAR(255) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country_id  BIGINT NOT NULL,
    CONSTRAINT pk_city PRIMARY KEY (id),
    CONSTRAINT fk_city_country_id FOREIGN KEY (country_id)
        REFERENCES country (id) ON DELETE RESTRICT
);

ALTER SEQUENCE city_seq OWNED BY city.id;
CREATE INDEX idx_city_country_id ON city (country_id);

CREATE SEQUENCE address_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE address (
    id              BIGINT NOT NULL DEFAULT nextval('address_seq'),
    city_id         BIGINT NOT NULL,
    street          VARCHAR(255) NOT NULL,
    street_number   VARCHAR(20) NOT NULL,
    box_number      VARCHAR(20),
    additional_info VARCHAR(255),
    CONSTRAINT pk_address PRIMARY KEY (id),
    CONSTRAINT fk_address_city_id FOREIGN KEY (city_id)
        REFERENCES city (id) ON DELETE RESTRICT
);

ALTER SEQUENCE address_seq OWNED BY address.id;
CREATE INDEX idx_address_city_id ON address (city_id);

-- =============================================================
-- 2. Utilisateurs, adhésion et RBAC
-- =============================================================

CREATE SEQUENCE app_user_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE app_user (
    id                     BIGINT NOT NULL DEFAULT nextval('app_user_seq'),
    email                  VARCHAR(255) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    first_name             VARCHAR(100) NOT NULL,
    last_name              VARCHAR(100) NOT NULL,
    phone_number           VARCHAR(30),
    account_status         VARCHAR(20) NOT NULL,
    member_number          VARCHAR(20),
    member_status          VARCHAR(20),
    registration_date      DATE,
    member_expiration_date DATE,
    blocked_reason         VARCHAR(255),
    last_login_at          TIMESTAMPTZ,
    failed_login_count     INTEGER NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_member_number UNIQUE (member_number),
    CONSTRAINT ck_app_user_account_status
        CHECK (account_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_user_member_status
        CHECK (member_status IS NULL OR member_status IN ('ACTIVE', 'BLOCKED', 'EXPIRED'))
);

ALTER SEQUENCE app_user_seq OWNED BY app_user.id;

CREATE SEQUENCE residence_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE residence (
    id         BIGINT NOT NULL DEFAULT nextval('residence_seq'),
    user_id    BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date   DATE,
    CONSTRAINT pk_residence PRIMARY KEY (id),
    CONSTRAINT fk_residence_user_id FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_residence_address_id FOREIGN KEY (address_id)
        REFERENCES address (id) ON DELETE RESTRICT,
    CONSTRAINT ck_residence_dates
        CHECK (end_date IS NULL OR end_date >= start_date)
);

ALTER SEQUENCE residence_seq OWNED BY residence.id;
CREATE INDEX idx_residence_user_id ON residence (user_id);
CREATE INDEX idx_residence_address_id ON residence (address_id);

-- Au plus une résidence courante (end_date NULL) par utilisateur.
-- La non-superposition des périodes historiques reste une validation Service
-- (cf. database-model.md §6/§24.3 — pas de contrainte PostgreSQL avancée sans besoin démontré).
CREATE UNIQUE INDEX ux_residence_current_per_user
    ON residence (user_id)
    WHERE end_date IS NULL;

CREATE SEQUENCE role_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE role (
    id          BIGINT NOT NULL DEFAULT nextval('role_seq'),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role_name UNIQUE (name),
    CONSTRAINT uq_role_code UNIQUE (code)
);

ALTER SEQUENCE role_seq OWNED BY role.id;

CREATE SEQUENCE permission_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE permission (
    id          BIGINT NOT NULL DEFAULT nextval('permission_seq'),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_permission PRIMARY KEY (id),
    CONSTRAINT uq_permission_name UNIQUE (name),
    CONSTRAINT uq_permission_code UNIQUE (code)
);

ALTER SEQUENCE permission_seq OWNED BY permission.id;

CREATE TABLE user_role (
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by BIGINT,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role_role_id FOREIGN KEY (role_id)
        REFERENCES role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES app_user (id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_role_role_id ON user_role (role_id);

CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    assigned_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role_id FOREIGN KEY (role_id)
        REFERENCES role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permission_permission_id FOREIGN KEY (permission_id)
        REFERENCES permission (id) ON DELETE RESTRICT
);

CREATE INDEX idx_role_permission_permission_id ON role_permission (permission_id);

-- =============================================================
-- 3. Catalogue : author, genre, title, title_author, title_genre, copy
-- =============================================================

CREATE SEQUENCE author_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE author (
    id          BIGINT NOT NULL DEFAULT nextval('author_seq'),
    full_name   VARCHAR(255) NOT NULL,
    birth_date  DATE,
    death_date  DATE,
    nationality VARCHAR(100),
    biography   TEXT,
    CONSTRAINT pk_author PRIMARY KEY (id),
    CONSTRAINT ck_author_dates
        CHECK (birth_date IS NULL OR death_date IS NULL OR death_date >= birth_date)
);

ALTER SEQUENCE author_seq OWNED BY author.id;

CREATE SEQUENCE genre_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE genre (
    id          BIGINT NOT NULL DEFAULT nextval('genre_seq'),
    code        VARCHAR(50) NOT NULL,
    label       VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT pk_genre PRIMARY KEY (id),
    CONSTRAINT uq_genre_code UNIQUE (code),
    CONSTRAINT uq_genre_label UNIQUE (label)
);

ALTER SEQUENCE genre_seq OWNED BY genre.id;

CREATE SEQUENCE title_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE title (
    id                BIGINT NOT NULL DEFAULT nextval('title_seq'),
    isbn              VARCHAR(20),
    title             VARCHAR(500) NOT NULL,
    subtitle          VARCHAR(500),
    summary           TEXT,
    publication_year  INTEGER,
    language          VARCHAR(5) NOT NULL,
    page_count        INTEGER,
    publisher         VARCHAR(255),
    cover_image_url   VARCHAR(500),
    title_status      VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_title PRIMARY KEY (id),
    CONSTRAINT uq_title_isbn UNIQUE (isbn),
    CONSTRAINT ck_title_page_count_positive
        CHECK (page_count IS NULL OR page_count > 0),
    -- Contexte maître PRIMATIS_CONTEXT_DEV_v1.0 §15.18 : l'enum Language
    -- reste compatible avec FR/EN/NL/DE/ES/IT/LA (liste réduite du
    -- dictionnaire de données abandonnée pour ce point précis).
    CONSTRAINT ck_title_language
        CHECK (language IN ('FR', 'EN', 'NL', 'DE', 'ES', 'IT', 'LA')),
    CONSTRAINT ck_title_status
        CHECK (title_status IN ('ACTIVE', 'WITHDRAWN'))
);

ALTER SEQUENCE title_seq OWNED BY title.id;

-- Un Title doit posséder au moins un Author : contrainte structurelle
-- (title_author non vide) qui reste une responsabilité de workflow Service,
-- une FK/UNIQUE seule ne pouvant garantir un minimum-un-enfant après coup
-- (cf. database-model.md §9.5).
CREATE TABLE title_author (
    title_id  BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    CONSTRAINT pk_title_author PRIMARY KEY (title_id, author_id),
    CONSTRAINT fk_title_author_title_id FOREIGN KEY (title_id)
        REFERENCES title (id) ON DELETE RESTRICT,
    CONSTRAINT fk_title_author_author_id FOREIGN KEY (author_id)
        REFERENCES author (id) ON DELETE RESTRICT
);

CREATE INDEX idx_title_author_author_id ON title_author (author_id);

CREATE TABLE title_genre (
    genre_id BIGINT NOT NULL,
    title_id BIGINT NOT NULL,
    CONSTRAINT pk_title_genre PRIMARY KEY (genre_id, title_id),
    CONSTRAINT fk_title_genre_genre_id FOREIGN KEY (genre_id)
        REFERENCES genre (id) ON DELETE RESTRICT,
    CONSTRAINT fk_title_genre_title_id FOREIGN KEY (title_id)
        REFERENCES title (id) ON DELETE RESTRICT
);

CREATE INDEX idx_title_genre_title_id ON title_genre (title_id);

CREATE SEQUENCE copy_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE copy (
    id                  BIGINT NOT NULL DEFAULT nextval('copy_seq'),
    title_id            BIGINT NOT NULL,
    inventory_code      VARCHAR(50) NOT NULL,
    location            VARCHAR(255),
    copy_condition      VARCHAR(20) NOT NULL,
    availability_status VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_copy PRIMARY KEY (id),
    CONSTRAINT uq_copy_inventory_code UNIQUE (inventory_code),
    CONSTRAINT fk_copy_title_id FOREIGN KEY (title_id)
        REFERENCES title (id) ON DELETE RESTRICT,
    CONSTRAINT ck_copy_condition
        CHECK (copy_condition IN ('GOOD', 'DAMAGED', 'LOST', 'OUT_OF_SERVICE')),
    CONSTRAINT ck_copy_availability_status
        CHECK (availability_status IN ('AVAILABLE', 'ON_LOAN', 'RESERVED', 'UNAVAILABLE')),
    -- LOST et OUT_OF_SERVICE impliquent UNAVAILABLE ; DAMAGED ne l'implique pas.
    CONSTRAINT ck_copy_condition_availability
        CHECK (copy_condition NOT IN ('LOST', 'OUT_OF_SERVICE') OR availability_status = 'UNAVAILABLE')
);

ALTER SEQUENCE copy_seq OWNED BY copy.id;
CREATE INDEX idx_copy_title_id ON copy (title_id);

-- =============================================================
-- 4. Prêts : loan
-- =============================================================

CREATE SEQUENCE loan_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE loan (
    id          BIGINT NOT NULL DEFAULT nextval('loan_seq'),
    user_id     BIGINT NOT NULL,
    copy_id     BIGINT NOT NULL,
    loan_date   TIMESTAMPTZ NOT NULL,
    due_date    DATE NOT NULL,
    return_date DATE,
    loan_status VARCHAR(20) NOT NULL,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_loan PRIMARY KEY (id),
    CONSTRAINT fk_loan_user_id FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_copy_id FOREIGN KEY (copy_id)
        REFERENCES copy (id) ON DELETE RESTRICT,
    CONSTRAINT ck_loan_status
        CHECK (loan_status IN ('ACTIVE', 'OVERDUE', 'RETURNED')),
    CONSTRAINT ck_loan_due_date_after_loan_date
        CHECK (due_date >= loan_date::date),
    CONSTRAINT ck_loan_return_date_after_loan_date
        CHECK (return_date IS NULL OR return_date >= loan_date::date)
);

ALTER SEQUENCE loan_seq OWNED BY loan.id;
CREATE INDEX idx_loan_user_id ON loan (user_id);

-- Un Copy ne peut avoir qu'un seul Loan ouvert (ACTIVE ou OVERDUE) à la fois.
CREATE UNIQUE INDEX ux_loan_open_copy
    ON loan (copy_id)
    WHERE loan_status IN ('ACTIVE', 'OVERDUE');

-- =============================================================
-- 5. Réservations : reservation
-- =============================================================

CREATE SEQUENCE reservation_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE reservation (
    id                   BIGINT NOT NULL DEFAULT nextval('reservation_seq'),
    user_id              BIGINT NOT NULL,
    fulfilled_by_loan_id BIGINT,
    assigned_copy_id     BIGINT,
    title_id             BIGINT NOT NULL,
    reservation_date     TIMESTAMPTZ NOT NULL,
    expiration_date      TIMESTAMPTZ,
    reservation_status   VARCHAR(20) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reservation PRIMARY KEY (id),
    CONSTRAINT uq_reservation_fulfilled_by_loan_id UNIQUE (fulfilled_by_loan_id),
    CONSTRAINT fk_reservation_user_id FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_title_id FOREIGN KEY (title_id)
        REFERENCES title (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_assigned_copy_id FOREIGN KEY (assigned_copy_id)
        REFERENCES copy (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_fulfilled_by_loan_id FOREIGN KEY (fulfilled_by_loan_id)
        REFERENCES loan (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reservation_status
        CHECK (reservation_status IN ('WAITING', 'READY', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_reservation_status_consistency
        CHECK (
            (reservation_status = 'WAITING' AND assigned_copy_id IS NULL)
            OR (reservation_status = 'READY' AND assigned_copy_id IS NOT NULL AND expiration_date IS NOT NULL)
            OR (reservation_status = 'FULFILLED' AND fulfilled_by_loan_id IS NOT NULL)
            OR (reservation_status IN ('CANCELLED', 'EXPIRED'))
        )
);

ALTER SEQUENCE reservation_seq OWNED BY reservation.id;
CREATE INDEX idx_reservation_user_id ON reservation (user_id);
CREATE INDEX idx_reservation_title_id ON reservation (title_id);

-- Une seule Reservation active (WAITING ou READY) par couple (user, title).
CREATE UNIQUE INDEX ux_reservation_active_user_title
    ON reservation (user_id, title_id)
    WHERE reservation_status IN ('WAITING', 'READY');

-- Un même Copy ne peut être affecté qu'à une seule Reservation READY.
CREATE UNIQUE INDEX ux_reservation_ready_assigned_copy
    ON reservation (assigned_copy_id)
    WHERE reservation_status = 'READY';

-- =============================================================
-- 6. Amendes : fine
-- =============================================================

CREATE SEQUENCE fine_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE fine (
    id           BIGINT NOT NULL DEFAULT nextval('fine_seq'),
    loan_id      BIGINT NOT NULL,
    amount       NUMERIC(10, 2) NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL,
    fine_status  VARCHAR(20) NOT NULL,
    paid_at      TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT pk_fine PRIMARY KEY (id),
    CONSTRAINT uq_fine_loan_id UNIQUE (loan_id),
    CONSTRAINT fk_fine_loan_id FOREIGN KEY (loan_id)
        REFERENCES loan (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fine_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_fine_status
        CHECK (fine_status IN ('UNPAID', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_fine_status_consistency
        CHECK (
            (fine_status = 'UNPAID' AND paid_at IS NULL AND cancelled_at IS NULL)
            OR (fine_status = 'PAID' AND paid_at IS NOT NULL AND cancelled_at IS NULL)
            OR (fine_status = 'CANCELLED' AND cancelled_at IS NOT NULL AND paid_at IS NULL)
        ),
    CONSTRAINT ck_fine_paid_after_issued
        CHECK (paid_at IS NULL OR paid_at >= issued_at),
    CONSTRAINT ck_fine_cancelled_after_issued
        CHECK (cancelled_at IS NULL OR cancelled_at >= issued_at)
);

ALTER SEQUENCE fine_seq OWNED BY fine.id;

-- =============================================================
-- 7. Articles et tags : article, tag, article_tag
-- =============================================================

CREATE SEQUENCE article_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE article (
    id                        BIGINT NOT NULL DEFAULT nextval('article_seq'),
    author_user_id            BIGINT NOT NULL,
    last_modified_by_user_id  BIGINT,
    title                     VARCHAR(255) NOT NULL,
    content                   TEXT NOT NULL,
    summary                   TEXT,
    slug                      VARCHAR(255) NOT NULL,
    article_status            VARCHAR(20) NOT NULL,
    published_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_article PRIMARY KEY (id),
    CONSTRAINT uq_article_slug UNIQUE (slug),
    CONSTRAINT fk_article_author_user_id FOREIGN KEY (author_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_article_last_modified_by_user_id FOREIGN KEY (last_modified_by_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_article_status
        CHECK (article_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_article_published_at_consistency
        CHECK (
            (article_status = 'DRAFT' AND published_at IS NULL)
            OR (article_status IN ('PUBLISHED', 'ARCHIVED') AND published_at IS NOT NULL AND published_at >= created_at)
        )
);

ALTER SEQUENCE article_seq OWNED BY article.id;
CREATE INDEX idx_article_author_user_id ON article (author_user_id);

CREATE SEQUENCE tag_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE tag (
    id          BIGINT NOT NULL DEFAULT nextval('tag_seq'),
    code        VARCHAR(50) NOT NULL,
    label       VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT pk_tag PRIMARY KEY (id),
    CONSTRAINT uq_tag_code UNIQUE (code)
);

ALTER SEQUENCE tag_seq OWNED BY tag.id;

CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    CONSTRAINT pk_article_tag PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_article_tag_article_id FOREIGN KEY (article_id)
        REFERENCES article (id) ON DELETE RESTRICT,
    CONSTRAINT fk_article_tag_tag_id FOREIGN KEY (tag_id)
        REFERENCES tag (id) ON DELETE RESTRICT
);

CREATE INDEX idx_article_tag_tag_id ON article_tag (tag_id);

-- =============================================================
-- 8. Notifications : notification
-- =============================================================

CREATE SEQUENCE notification_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE notification (
    id                  BIGINT NOT NULL DEFAULT nextval('notification_seq'),
    recipient_user_id   BIGINT NOT NULL,
    loan_id             BIGINT,
    reservation_id      BIGINT,
    fine_id             BIGINT,
    article_id          BIGINT,
    notification_type   VARCHAR(30) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    message             TEXT NOT NULL,
    notification_status VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    read_at             TIMESTAMPTZ,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient_user_id FOREIGN KEY (recipient_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_loan_id FOREIGN KEY (loan_id)
        REFERENCES loan (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_reservation_id FOREIGN KEY (reservation_id)
        REFERENCES reservation (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_fine_id FOREIGN KEY (fine_id)
        REFERENCES fine (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_article_id FOREIGN KEY (article_id)
        REFERENCES article (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_type
        CHECK (notification_type IN (
            'LOAN_DUE_SOON', 'LOAN_OVERDUE', 'LOAN_RETURNED',
            'RESERVATION_CREATED', 'RESERVATION_READY', 'RESERVATION_EXPIRED', 'RESERVATION_CANCELLED',
            'FINE_ISSUED', 'FINE_PAID', 'FINE_CANCELLED',
            'ARTICLE_PUBLISHED'
        )),
    CONSTRAINT ck_notification_status
        CHECK (notification_status IN ('UNREAD', 'READ')),
    -- Exactement une origine métier non nulle parmi loan/reservation/fine/article.
    CONSTRAINT ck_notification_exactly_one_origin
        CHECK (
            (CASE WHEN loan_id IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN reservation_id IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN fine_id IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN article_id IS NOT NULL THEN 1 ELSE 0 END) = 1
        ),
    CONSTRAINT ck_notification_read_consistency
        CHECK (
            (notification_status = 'UNREAD' AND read_at IS NULL)
            OR (notification_status = 'READ' AND read_at IS NOT NULL AND read_at >= created_at)
        )
);

ALTER SEQUENCE notification_seq OWNED BY notification.id;
CREATE INDEX idx_notification_recipient_user_id ON notification (recipient_user_id);

-- =============================================================
-- 9. Paramètres métier globaux : application_setting
-- =============================================================

CREATE SEQUENCE application_setting_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE application_setting (
    setting_id         BIGINT NOT NULL DEFAULT nextval('application_setting_seq'),
    setting_key        VARCHAR(100) NOT NULL,
    setting_value      VARCHAR(255) NOT NULL,
    value_type         VARCHAR(20) NOT NULL,
    description        VARCHAR(255) NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    updated_by_user_id BIGINT,
    CONSTRAINT pk_application_setting PRIMARY KEY (setting_id),
    CONSTRAINT uq_application_setting_key UNIQUE (setting_key),
    CONSTRAINT fk_application_setting_updated_by_user_id FOREIGN KEY (updated_by_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_application_setting_value_type
        CHECK (value_type IN ('INTEGER', 'DECIMAL', 'BOOLEAN', 'STRING'))
);

ALTER SEQUENCE application_setting_seq OWNED BY application_setting.setting_id;

-- Bootstrap obligatoire des 4 paramètres métier V1 (PRIMATIS_DATA_DICTIONARY_v2.1 §10.1).
INSERT INTO application_setting (setting_key, setting_value, value_type, description, updated_at)
VALUES
    ('LOAN_DURATION_DAYS', '21', 'INTEGER',
     'Durée standard d''un prêt, en jours, avant échéance.', now()),
    ('MAX_ACTIVE_RESERVATIONS_PER_MEMBER', '10', 'INTEGER',
     'Nombre maximum de réservations actives simultanées autorisées par membre.', now()),
    ('RESERVATION_READY_HOLD_HOURS', '48', 'INTEGER',
     'Durée, en heures, pendant laquelle un exemplaire reste affecté à une réservation READY avant expiration.', now()),
    ('LOAN_DUE_SOON_DAYS', '3', 'INTEGER',
     'Nombre de jours avant échéance déclenchant la notification LOAN_DUE_SOON.', now());
