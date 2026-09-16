--
-- PostgreSQL database dump
--

\restrict FtbgdohA0ugKbKlehCv84tmcsEFeDeKpCDoZuvkCm0Tfm2L6X9iHYDfVa2B4fwx

-- Dumped from database version 17.10
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: primatis
--

-- *not* creating schema, since initdb creates it


ALTER SCHEMA public OWNER TO primatis;

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: primatis
--

COMMENT ON SCHEMA public IS '';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: address; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.address (
    id bigint NOT NULL,
    city_id bigint NOT NULL,
    street character varying(255) NOT NULL,
    street_number character varying(20) NOT NULL,
    box_number character varying(20),
    additional_info character varying(255)
);


ALTER TABLE public.address OWNER TO primatis;

--
-- Name: address_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.address_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.address_seq OWNER TO primatis;

--
-- Name: address_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.address_seq OWNED BY public.address.id;


--
-- Name: app_user; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.app_user (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    phone_number character varying(30),
    account_status character varying(20) NOT NULL,
    member_number character varying(20),
    member_status character varying(20),
    registration_date date,
    member_expiration_date date,
    blocked_reason character varying(255),
    last_login_at timestamp with time zone,
    failed_login_count integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_app_user_account_status CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT ck_app_user_member_number_format CHECK (((member_number IS NULL) OR ((member_number)::text ~ '^M[0-9]{9}$'::text))),
    CONSTRAINT ck_app_user_member_status CHECK (((member_status IS NULL) OR ((member_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying, 'EXPIRED'::character varying])::text[]))))
);


ALTER TABLE public.app_user OWNER TO primatis;

--
-- Name: app_user_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.app_user_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.app_user_seq OWNER TO primatis;

--
-- Name: app_user_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.app_user_seq OWNED BY public.app_user.id;


--
-- Name: application_setting; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.application_setting (
    setting_id bigint NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value character varying(255) NOT NULL,
    value_type character varying(20) NOT NULL,
    description character varying(255) NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    updated_by_user_id bigint,
    CONSTRAINT ck_application_setting_value_type CHECK (((value_type)::text = ANY ((ARRAY['INTEGER'::character varying, 'DECIMAL'::character varying, 'BOOLEAN'::character varying, 'STRING'::character varying])::text[])))
);


ALTER TABLE public.application_setting OWNER TO primatis;

--
-- Name: application_setting_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.application_setting_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.application_setting_seq OWNER TO primatis;

--
-- Name: application_setting_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.application_setting_seq OWNED BY public.application_setting.setting_id;


--
-- Name: article; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.article (
    id bigint NOT NULL,
    author_user_id bigint NOT NULL,
    last_modified_by_user_id bigint,
    title character varying(255) NOT NULL,
    content text NOT NULL,
    summary text,
    slug character varying(255) NOT NULL,
    article_status character varying(20) NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_article_published_at_consistency CHECK (((((article_status)::text = 'DRAFT'::text) AND (published_at IS NULL)) OR (((article_status)::text = ANY ((ARRAY['PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])) AND (published_at IS NOT NULL) AND (published_at >= created_at)))),
    CONSTRAINT ck_article_status CHECK (((article_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


ALTER TABLE public.article OWNER TO primatis;

--
-- Name: article_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.article_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.article_seq OWNER TO primatis;

--
-- Name: article_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.article_seq OWNED BY public.article.id;


--
-- Name: article_tag; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.article_tag (
    article_id bigint NOT NULL,
    tag_id bigint NOT NULL
);


ALTER TABLE public.article_tag OWNER TO primatis;

--
-- Name: author; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.author (
    id bigint NOT NULL,
    full_name character varying(255) NOT NULL,
    birth_date date,
    death_date date,
    nationality character varying(100),
    biography text,
    CONSTRAINT ck_author_dates CHECK (((birth_date IS NULL) OR (death_date IS NULL) OR (death_date >= birth_date)))
);


ALTER TABLE public.author OWNER TO primatis;

--
-- Name: author_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.author_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.author_seq OWNER TO primatis;

--
-- Name: author_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.author_seq OWNED BY public.author.id;


--
-- Name: city; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.city (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    postal_code character varying(20) NOT NULL,
    country_id bigint NOT NULL
);


ALTER TABLE public.city OWNER TO primatis;

--
-- Name: city_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.city_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.city_seq OWNER TO primatis;

--
-- Name: city_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.city_seq OWNED BY public.city.id;


--
-- Name: copy; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.copy (
    id bigint NOT NULL,
    title_id bigint NOT NULL,
    inventory_code character varying(50) NOT NULL,
    location character varying(255),
    copy_condition character varying(20) NOT NULL,
    availability_status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_copy_availability_status CHECK (((availability_status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'ON_LOAN'::character varying, 'RESERVED'::character varying, 'UNAVAILABLE'::character varying])::text[]))),
    CONSTRAINT ck_copy_condition CHECK (((copy_condition)::text = ANY ((ARRAY['GOOD'::character varying, 'DAMAGED'::character varying, 'LOST'::character varying, 'OUT_OF_SERVICE'::character varying])::text[]))),
    CONSTRAINT ck_copy_condition_availability CHECK ((((copy_condition)::text <> ALL ((ARRAY['LOST'::character varying, 'OUT_OF_SERVICE'::character varying])::text[])) OR ((availability_status)::text = 'UNAVAILABLE'::text)))
);


ALTER TABLE public.copy OWNER TO primatis;

--
-- Name: copy_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.copy_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.copy_seq OWNER TO primatis;

--
-- Name: copy_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.copy_seq OWNED BY public.copy.id;


--
-- Name: country; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.country (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(10) NOT NULL
);


ALTER TABLE public.country OWNER TO primatis;

--
-- Name: country_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.country_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.country_seq OWNER TO primatis;

--
-- Name: country_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.country_seq OWNED BY public.country.id;


--
-- Name: fine; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.fine (
    id bigint NOT NULL,
    loan_id bigint NOT NULL,
    amount numeric(10,2) NOT NULL,
    reason character varying(255) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    fine_status character varying(20) NOT NULL,
    paid_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    CONSTRAINT ck_fine_amount_positive CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_fine_cancelled_after_issued CHECK (((cancelled_at IS NULL) OR (cancelled_at >= issued_at))),
    CONSTRAINT ck_fine_paid_after_issued CHECK (((paid_at IS NULL) OR (paid_at >= issued_at))),
    CONSTRAINT ck_fine_status CHECK (((fine_status)::text = ANY ((ARRAY['UNPAID'::character varying, 'PAID'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_fine_status_consistency CHECK (((((fine_status)::text = 'UNPAID'::text) AND (paid_at IS NULL) AND (cancelled_at IS NULL)) OR (((fine_status)::text = 'PAID'::text) AND (paid_at IS NOT NULL) AND (cancelled_at IS NULL)) OR (((fine_status)::text = 'CANCELLED'::text) AND (cancelled_at IS NOT NULL) AND (paid_at IS NULL))))
);


ALTER TABLE public.fine OWNER TO primatis;

--
-- Name: fine_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.fine_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.fine_seq OWNER TO primatis;

--
-- Name: fine_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.fine_seq OWNED BY public.fine.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO primatis;

--
-- Name: genre; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.genre (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    label character varying(100) NOT NULL,
    description character varying(255)
);


ALTER TABLE public.genre OWNER TO primatis;

--
-- Name: genre_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.genre_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.genre_seq OWNER TO primatis;

--
-- Name: genre_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.genre_seq OWNED BY public.genre.id;


--
-- Name: loan; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.loan (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    copy_id bigint NOT NULL,
    loan_date timestamp with time zone NOT NULL,
    due_date date NOT NULL,
    return_date date,
    loan_status character varying(20) NOT NULL,
    notes text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_loan_due_date_after_loan_date CHECK ((due_date >= ((loan_date AT TIME ZONE 'UTC'::text))::date)),
    CONSTRAINT ck_loan_return_date_after_loan_date CHECK (((return_date IS NULL) OR (return_date >= ((loan_date AT TIME ZONE 'UTC'::text))::date))),
    CONSTRAINT ck_loan_status CHECK (((loan_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'OVERDUE'::character varying, 'RETURNED'::character varying])::text[])))
);


ALTER TABLE public.loan OWNER TO primatis;

--
-- Name: loan_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.loan_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.loan_seq OWNER TO primatis;

--
-- Name: loan_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.loan_seq OWNED BY public.loan.id;


--
-- Name: member_number_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.member_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_number_seq OWNER TO primatis;

--
-- Name: notification; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    recipient_user_id bigint NOT NULL,
    loan_id bigint,
    reservation_id bigint,
    fine_id bigint,
    article_id bigint,
    notification_type character varying(30) NOT NULL,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    notification_status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    read_at timestamp with time zone,
    CONSTRAINT ck_notification_exactly_one_origin CHECK (((((
CASE
    WHEN (loan_id IS NOT NULL) THEN 1
    ELSE 0
END +
CASE
    WHEN (reservation_id IS NOT NULL) THEN 1
    ELSE 0
END) +
CASE
    WHEN (fine_id IS NOT NULL) THEN 1
    ELSE 0
END) +
CASE
    WHEN (article_id IS NOT NULL) THEN 1
    ELSE 0
END) = 1)),
    CONSTRAINT ck_notification_read_consistency CHECK (((((notification_status)::text = 'UNREAD'::text) AND (read_at IS NULL)) OR (((notification_status)::text = 'READ'::text) AND (read_at IS NOT NULL) AND (read_at >= created_at)))),
    CONSTRAINT ck_notification_status CHECK (((notification_status)::text = ANY ((ARRAY['UNREAD'::character varying, 'READ'::character varying])::text[]))),
    CONSTRAINT ck_notification_type CHECK (((notification_type)::text = ANY ((ARRAY['LOAN_DUE_SOON'::character varying, 'LOAN_OVERDUE'::character varying, 'LOAN_RETURNED'::character varying, 'RESERVATION_CREATED'::character varying, 'RESERVATION_READY'::character varying, 'RESERVATION_EXPIRED'::character varying, 'RESERVATION_CANCELLED'::character varying, 'FINE_ISSUED'::character varying, 'FINE_PAID'::character varying, 'FINE_CANCELLED'::character varying, 'ARTICLE_PUBLISHED'::character varying])::text[])))
);


ALTER TABLE public.notification OWNER TO primatis;

--
-- Name: notification_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.notification_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.notification_seq OWNER TO primatis;

--
-- Name: notification_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.notification_seq OWNED BY public.notification.id;


--
-- Name: permission; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.permission (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    code character varying(50) NOT NULL,
    description character varying(255),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE public.permission OWNER TO primatis;

--
-- Name: permission_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.permission_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.permission_seq OWNER TO primatis;

--
-- Name: permission_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.permission_seq OWNED BY public.permission.id;


--
-- Name: reservation; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.reservation (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    fulfilled_by_loan_id bigint,
    assigned_copy_id bigint,
    title_id bigint NOT NULL,
    reservation_date timestamp with time zone NOT NULL,
    expiration_date timestamp with time zone,
    reservation_status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_reservation_status CHECK (((reservation_status)::text = ANY ((ARRAY['WAITING'::character varying, 'READY'::character varying, 'FULFILLED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT ck_reservation_status_consistency CHECK (((((reservation_status)::text = 'WAITING'::text) AND (assigned_copy_id IS NULL)) OR (((reservation_status)::text = 'READY'::text) AND (assigned_copy_id IS NOT NULL) AND (expiration_date IS NOT NULL)) OR (((reservation_status)::text = 'FULFILLED'::text) AND (fulfilled_by_loan_id IS NOT NULL)) OR ((reservation_status)::text = ANY ((ARRAY['CANCELLED'::character varying, 'EXPIRED'::character varying])::text[]))))
);


ALTER TABLE public.reservation OWNER TO primatis;

--
-- Name: reservation_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.reservation_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.reservation_seq OWNER TO primatis;

--
-- Name: reservation_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.reservation_seq OWNED BY public.reservation.id;


--
-- Name: residence; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.residence (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    address_id bigint NOT NULL,
    start_date date NOT NULL,
    end_date date,
    CONSTRAINT ck_residence_dates CHECK (((end_date IS NULL) OR (end_date >= start_date)))
);


ALTER TABLE public.residence OWNER TO primatis;

--
-- Name: residence_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.residence_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.residence_seq OWNER TO primatis;

--
-- Name: residence_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.residence_seq OWNED BY public.residence.id;


--
-- Name: role; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.role (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    code character varying(50) NOT NULL,
    description character varying(255),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE public.role OWNER TO primatis;

--
-- Name: role_permission; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    assigned_at timestamp with time zone NOT NULL
);


ALTER TABLE public.role_permission OWNER TO primatis;

--
-- Name: role_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.role_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.role_seq OWNER TO primatis;

--
-- Name: role_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.role_seq OWNED BY public.role.id;


--
-- Name: tag; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.tag (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    label character varying(100) NOT NULL,
    description character varying(255)
);


ALTER TABLE public.tag OWNER TO primatis;

--
-- Name: tag_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.tag_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tag_seq OWNER TO primatis;

--
-- Name: tag_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.tag_seq OWNED BY public.tag.id;


--
-- Name: title; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.title (
    id bigint NOT NULL,
    isbn character varying(20),
    title character varying(500) NOT NULL,
    subtitle character varying(500),
    summary text,
    publication_year integer,
    language character varying(5) NOT NULL,
    page_count integer,
    publisher character varying(255),
    cover_image_url character varying(500),
    title_status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_title_language CHECK (((language)::text = ANY ((ARRAY['FR'::character varying, 'EN'::character varying, 'NL'::character varying, 'DE'::character varying, 'ES'::character varying, 'IT'::character varying, 'LA'::character varying])::text[]))),
    CONSTRAINT ck_title_page_count_positive CHECK (((page_count IS NULL) OR (page_count > 0))),
    CONSTRAINT ck_title_status CHECK (((title_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'WITHDRAWN'::character varying])::text[])))
);


ALTER TABLE public.title OWNER TO primatis;

--
-- Name: title_author; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.title_author (
    title_id bigint NOT NULL,
    author_id bigint NOT NULL
);


ALTER TABLE public.title_author OWNER TO primatis;

--
-- Name: title_genre; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.title_genre (
    genre_id bigint NOT NULL,
    title_id bigint NOT NULL
);


ALTER TABLE public.title_genre OWNER TO primatis;

--
-- Name: title_seq; Type: SEQUENCE; Schema: public; Owner: primatis
--

CREATE SEQUENCE public.title_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.title_seq OWNER TO primatis;

--
-- Name: title_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: primatis
--

ALTER SEQUENCE public.title_seq OWNED BY public.title.id;


--
-- Name: user_role; Type: TABLE; Schema: public; Owner: primatis
--

CREATE TABLE public.user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    assigned_at timestamp with time zone NOT NULL,
    assigned_by bigint
);


ALTER TABLE public.user_role OWNER TO primatis;

--
-- Name: address id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.address ALTER COLUMN id SET DEFAULT nextval('public.address_seq'::regclass);


--
-- Name: app_user id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.app_user ALTER COLUMN id SET DEFAULT nextval('public.app_user_seq'::regclass);


--
-- Name: application_setting setting_id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.application_setting ALTER COLUMN setting_id SET DEFAULT nextval('public.application_setting_seq'::regclass);


--
-- Name: article id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article ALTER COLUMN id SET DEFAULT nextval('public.article_seq'::regclass);


--
-- Name: author id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.author ALTER COLUMN id SET DEFAULT nextval('public.author_seq'::regclass);


--
-- Name: city id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.city ALTER COLUMN id SET DEFAULT nextval('public.city_seq'::regclass);


--
-- Name: copy id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.copy ALTER COLUMN id SET DEFAULT nextval('public.copy_seq'::regclass);


--
-- Name: country id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.country ALTER COLUMN id SET DEFAULT nextval('public.country_seq'::regclass);


--
-- Name: fine id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.fine ALTER COLUMN id SET DEFAULT nextval('public.fine_seq'::regclass);


--
-- Name: genre id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.genre ALTER COLUMN id SET DEFAULT nextval('public.genre_seq'::regclass);


--
-- Name: loan id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.loan ALTER COLUMN id SET DEFAULT nextval('public.loan_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_seq'::regclass);


--
-- Name: permission id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.permission ALTER COLUMN id SET DEFAULT nextval('public.permission_seq'::regclass);


--
-- Name: reservation id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation ALTER COLUMN id SET DEFAULT nextval('public.reservation_seq'::regclass);


--
-- Name: residence id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.residence ALTER COLUMN id SET DEFAULT nextval('public.residence_seq'::regclass);


--
-- Name: role id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role ALTER COLUMN id SET DEFAULT nextval('public.role_seq'::regclass);


--
-- Name: tag id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.tag ALTER COLUMN id SET DEFAULT nextval('public.tag_seq'::regclass);


--
-- Name: title id; Type: DEFAULT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title ALTER COLUMN id SET DEFAULT nextval('public.title_seq'::regclass);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: address pk_address; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.address
    ADD CONSTRAINT pk_address PRIMARY KEY (id);


--
-- Name: app_user pk_app_user; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT pk_app_user PRIMARY KEY (id);


--
-- Name: application_setting pk_application_setting; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.application_setting
    ADD CONSTRAINT pk_application_setting PRIMARY KEY (setting_id);


--
-- Name: article pk_article; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT pk_article PRIMARY KEY (id);


--
-- Name: article_tag pk_article_tag; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article_tag
    ADD CONSTRAINT pk_article_tag PRIMARY KEY (article_id, tag_id);


--
-- Name: author pk_author; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.author
    ADD CONSTRAINT pk_author PRIMARY KEY (id);


--
-- Name: city pk_city; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.city
    ADD CONSTRAINT pk_city PRIMARY KEY (id);


--
-- Name: copy pk_copy; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.copy
    ADD CONSTRAINT pk_copy PRIMARY KEY (id);


--
-- Name: country pk_country; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.country
    ADD CONSTRAINT pk_country PRIMARY KEY (id);


--
-- Name: fine pk_fine; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.fine
    ADD CONSTRAINT pk_fine PRIMARY KEY (id);


--
-- Name: genre pk_genre; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.genre
    ADD CONSTRAINT pk_genre PRIMARY KEY (id);


--
-- Name: loan pk_loan; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.loan
    ADD CONSTRAINT pk_loan PRIMARY KEY (id);


--
-- Name: notification pk_notification; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT pk_notification PRIMARY KEY (id);


--
-- Name: permission pk_permission; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT pk_permission PRIMARY KEY (id);


--
-- Name: reservation pk_reservation; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation
    ADD CONSTRAINT pk_reservation PRIMARY KEY (id);


--
-- Name: residence pk_residence; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.residence
    ADD CONSTRAINT pk_residence PRIMARY KEY (id);


--
-- Name: role pk_role; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT pk_role PRIMARY KEY (id);


--
-- Name: role_permission pk_role_permission; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id);


--
-- Name: tag pk_tag; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.tag
    ADD CONSTRAINT pk_tag PRIMARY KEY (id);


--
-- Name: title pk_title; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title
    ADD CONSTRAINT pk_title PRIMARY KEY (id);


--
-- Name: title_author pk_title_author; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title_author
    ADD CONSTRAINT pk_title_author PRIMARY KEY (title_id, author_id);


--
-- Name: title_genre pk_title_genre; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title_genre
    ADD CONSTRAINT pk_title_genre PRIMARY KEY (genre_id, title_id);


--
-- Name: user_role pk_user_role; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id);


--
-- Name: app_user uq_app_user_email; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT uq_app_user_email UNIQUE (email);


--
-- Name: app_user uq_app_user_member_number; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT uq_app_user_member_number UNIQUE (member_number);


--
-- Name: application_setting uq_application_setting_key; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.application_setting
    ADD CONSTRAINT uq_application_setting_key UNIQUE (setting_key);


--
-- Name: article uq_article_slug; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT uq_article_slug UNIQUE (slug);


--
-- Name: copy uq_copy_inventory_code; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.copy
    ADD CONSTRAINT uq_copy_inventory_code UNIQUE (inventory_code);


--
-- Name: country uq_country_code; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.country
    ADD CONSTRAINT uq_country_code UNIQUE (code);


--
-- Name: fine uq_fine_loan_id; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.fine
    ADD CONSTRAINT uq_fine_loan_id UNIQUE (loan_id);


--
-- Name: genre uq_genre_code; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.genre
    ADD CONSTRAINT uq_genre_code UNIQUE (code);


--
-- Name: genre uq_genre_label; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.genre
    ADD CONSTRAINT uq_genre_label UNIQUE (label);


--
-- Name: permission uq_permission_code; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT uq_permission_code UNIQUE (code);


--
-- Name: permission uq_permission_name; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT uq_permission_name UNIQUE (name);


--
-- Name: reservation uq_reservation_fulfilled_by_loan_id; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation
    ADD CONSTRAINT uq_reservation_fulfilled_by_loan_id UNIQUE (fulfilled_by_loan_id);


--
-- Name: role uq_role_code; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT uq_role_code UNIQUE (code);


--
-- Name: role uq_role_name; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT uq_role_name UNIQUE (name);


--
-- Name: tag uq_tag_code; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.tag
    ADD CONSTRAINT uq_tag_code UNIQUE (code);


--
-- Name: title uq_title_isbn; Type: CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title
    ADD CONSTRAINT uq_title_isbn UNIQUE (isbn);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_address_city_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_address_city_id ON public.address USING btree (city_id);


--
-- Name: idx_article_author_user_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_article_author_user_id ON public.article USING btree (author_user_id);


--
-- Name: idx_article_tag_tag_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_article_tag_tag_id ON public.article_tag USING btree (tag_id);


--
-- Name: idx_city_country_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_city_country_id ON public.city USING btree (country_id);


--
-- Name: idx_copy_title_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_copy_title_id ON public.copy USING btree (title_id);


--
-- Name: idx_loan_user_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_loan_user_id ON public.loan USING btree (user_id);


--
-- Name: idx_notification_recipient_user_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_notification_recipient_user_id ON public.notification USING btree (recipient_user_id);


--
-- Name: idx_reservation_title_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_reservation_title_id ON public.reservation USING btree (title_id);


--
-- Name: idx_reservation_user_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_reservation_user_id ON public.reservation USING btree (user_id);


--
-- Name: idx_residence_address_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_residence_address_id ON public.residence USING btree (address_id);


--
-- Name: idx_residence_user_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_residence_user_id ON public.residence USING btree (user_id);


--
-- Name: idx_role_permission_permission_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_role_permission_permission_id ON public.role_permission USING btree (permission_id);


--
-- Name: idx_title_author_author_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_title_author_author_id ON public.title_author USING btree (author_id);


--
-- Name: idx_title_genre_title_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_title_genre_title_id ON public.title_genre USING btree (title_id);


--
-- Name: idx_user_role_role_id; Type: INDEX; Schema: public; Owner: primatis
--

CREATE INDEX idx_user_role_role_id ON public.user_role USING btree (role_id);


--
-- Name: ux_loan_open_copy; Type: INDEX; Schema: public; Owner: primatis
--

CREATE UNIQUE INDEX ux_loan_open_copy ON public.loan USING btree (copy_id) WHERE ((loan_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'OVERDUE'::character varying])::text[]));


--
-- Name: ux_notification_loan_due_soon; Type: INDEX; Schema: public; Owner: primatis
--

CREATE UNIQUE INDEX ux_notification_loan_due_soon ON public.notification USING btree (loan_id) WHERE ((notification_type)::text = 'LOAN_DUE_SOON'::text);


--
-- Name: ux_reservation_active_user_title; Type: INDEX; Schema: public; Owner: primatis
--

CREATE UNIQUE INDEX ux_reservation_active_user_title ON public.reservation USING btree (user_id, title_id) WHERE ((reservation_status)::text = ANY ((ARRAY['WAITING'::character varying, 'READY'::character varying])::text[]));


--
-- Name: ux_reservation_ready_assigned_copy; Type: INDEX; Schema: public; Owner: primatis
--

CREATE UNIQUE INDEX ux_reservation_ready_assigned_copy ON public.reservation USING btree (assigned_copy_id) WHERE ((reservation_status)::text = 'READY'::text);


--
-- Name: ux_residence_current_per_user; Type: INDEX; Schema: public; Owner: primatis
--

CREATE UNIQUE INDEX ux_residence_current_per_user ON public.residence USING btree (user_id) WHERE (end_date IS NULL);


--
-- Name: address fk_address_city_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.address
    ADD CONSTRAINT fk_address_city_id FOREIGN KEY (city_id) REFERENCES public.city(id) ON DELETE RESTRICT;


--
-- Name: application_setting fk_application_setting_updated_by_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.application_setting
    ADD CONSTRAINT fk_application_setting_updated_by_user_id FOREIGN KEY (updated_by_user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: article fk_article_author_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT fk_article_author_user_id FOREIGN KEY (author_user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: article fk_article_last_modified_by_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT fk_article_last_modified_by_user_id FOREIGN KEY (last_modified_by_user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: article_tag fk_article_tag_article_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article_tag
    ADD CONSTRAINT fk_article_tag_article_id FOREIGN KEY (article_id) REFERENCES public.article(id) ON DELETE RESTRICT;


--
-- Name: article_tag fk_article_tag_tag_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.article_tag
    ADD CONSTRAINT fk_article_tag_tag_id FOREIGN KEY (tag_id) REFERENCES public.tag(id) ON DELETE RESTRICT;


--
-- Name: city fk_city_country_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.city
    ADD CONSTRAINT fk_city_country_id FOREIGN KEY (country_id) REFERENCES public.country(id) ON DELETE RESTRICT;


--
-- Name: copy fk_copy_title_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.copy
    ADD CONSTRAINT fk_copy_title_id FOREIGN KEY (title_id) REFERENCES public.title(id) ON DELETE RESTRICT;


--
-- Name: fine fk_fine_loan_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.fine
    ADD CONSTRAINT fk_fine_loan_id FOREIGN KEY (loan_id) REFERENCES public.loan(id) ON DELETE RESTRICT;


--
-- Name: loan fk_loan_copy_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.loan
    ADD CONSTRAINT fk_loan_copy_id FOREIGN KEY (copy_id) REFERENCES public.copy(id) ON DELETE RESTRICT;


--
-- Name: loan fk_loan_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.loan
    ADD CONSTRAINT fk_loan_user_id FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: notification fk_notification_article_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_article_id FOREIGN KEY (article_id) REFERENCES public.article(id) ON DELETE RESTRICT;


--
-- Name: notification fk_notification_fine_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_fine_id FOREIGN KEY (fine_id) REFERENCES public.fine(id) ON DELETE RESTRICT;


--
-- Name: notification fk_notification_loan_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_loan_id FOREIGN KEY (loan_id) REFERENCES public.loan(id) ON DELETE RESTRICT;


--
-- Name: notification fk_notification_recipient_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_recipient_user_id FOREIGN KEY (recipient_user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: notification fk_notification_reservation_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_reservation_id FOREIGN KEY (reservation_id) REFERENCES public.reservation(id) ON DELETE RESTRICT;


--
-- Name: reservation fk_reservation_assigned_copy_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation
    ADD CONSTRAINT fk_reservation_assigned_copy_id FOREIGN KEY (assigned_copy_id) REFERENCES public.copy(id) ON DELETE RESTRICT;


--
-- Name: reservation fk_reservation_fulfilled_by_loan_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation
    ADD CONSTRAINT fk_reservation_fulfilled_by_loan_id FOREIGN KEY (fulfilled_by_loan_id) REFERENCES public.loan(id) ON DELETE RESTRICT;


--
-- Name: reservation fk_reservation_title_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation
    ADD CONSTRAINT fk_reservation_title_id FOREIGN KEY (title_id) REFERENCES public.title(id) ON DELETE RESTRICT;


--
-- Name: reservation fk_reservation_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.reservation
    ADD CONSTRAINT fk_reservation_user_id FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: residence fk_residence_address_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.residence
    ADD CONSTRAINT fk_residence_address_id FOREIGN KEY (address_id) REFERENCES public.address(id) ON DELETE RESTRICT;


--
-- Name: residence fk_residence_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.residence
    ADD CONSTRAINT fk_residence_user_id FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: role_permission fk_role_permission_permission_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fk_role_permission_permission_id FOREIGN KEY (permission_id) REFERENCES public.permission(id) ON DELETE RESTRICT;


--
-- Name: role_permission fk_role_permission_role_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fk_role_permission_role_id FOREIGN KEY (role_id) REFERENCES public.role(id) ON DELETE RESTRICT;


--
-- Name: title_author fk_title_author_author_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title_author
    ADD CONSTRAINT fk_title_author_author_id FOREIGN KEY (author_id) REFERENCES public.author(id) ON DELETE RESTRICT;


--
-- Name: title_author fk_title_author_title_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title_author
    ADD CONSTRAINT fk_title_author_title_id FOREIGN KEY (title_id) REFERENCES public.title(id) ON DELETE RESTRICT;


--
-- Name: title_genre fk_title_genre_genre_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title_genre
    ADD CONSTRAINT fk_title_genre_genre_id FOREIGN KEY (genre_id) REFERENCES public.genre(id) ON DELETE RESTRICT;


--
-- Name: title_genre fk_title_genre_title_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.title_genre
    ADD CONSTRAINT fk_title_genre_title_id FOREIGN KEY (title_id) REFERENCES public.title(id) ON DELETE RESTRICT;


--
-- Name: user_role fk_user_role_assigned_by; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fk_user_role_assigned_by FOREIGN KEY (assigned_by) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: user_role fk_user_role_role_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fk_user_role_role_id FOREIGN KEY (role_id) REFERENCES public.role(id) ON DELETE RESTRICT;


--
-- Name: user_role fk_user_role_user_id; Type: FK CONSTRAINT; Schema: public; Owner: primatis
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE RESTRICT;


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: primatis
--

REVOKE USAGE ON SCHEMA public FROM PUBLIC;


--
-- PostgreSQL database dump complete
--

\unrestrict FtbgdohA0ugKbKlehCv84tmcsEFeDeKpCDoZuvkCm0Tfm2L6X9iHYDfVa2B4fwx

