-- PRIMATIS — V002 — Bootstrap initial RBAC
--
-- Insère les données obligatoires du contrôle d'accès basé sur les rôles :
-- les 3 rôles canoniques, les 19 permissions canoniques V1, et la matrice
-- RolePermission qui leur donne un sens (35 associations).
--
-- Source d'autorité : PRIMATIS_CONTEXT_DEV_v1.0 §6.3 (permissions), §6.4
-- (matrice initiale), §6.7-6.8 (capacités Librarian/Admin) — voir aussi
-- DEV-03.9 (RBAC / Method Security) et DEV-03.11 (ce bootstrap) dans
-- documentation-interne/tasks/DEV-03-authentication-rbac.md.
--
-- V001 reste immuable : cette migration n'altère aucune table, séquence ni
-- contrainte existante, elle insère uniquement des données dans le schéma
-- déjà créé par V001 (role, permission, role_permission).
--
-- Les associations RolePermission sont résolues par code (role.code,
-- permission.code), jamais par identifiant technique supposé : les IDs
-- proviennent normalement de role_seq/permission_seq (DEFAULT nextval,
-- inchangé), aucun nombre magique ne porte de signification RBAC ici.
--
-- role.name / permission.name (colonnes NOT NULL UNIQUE de V001) reçoivent
-- des libellés humains simples en français, dérivés directement du code
-- technique (READ -> Consultation, MANAGE -> Gestion, PUBLISH -> Publication)
-- — un choix de présentation/implémentation, pas une nouvelle règle métier.
-- description (nullable) est volontairement laissée à NULL : aucun texte
-- supplémentaire n'est requis par une source d'autorité.
--
-- Aucun AppUser, UserRole ni compte de démonstration n'est créé ici : ce
-- bootstrap couvre exclusivement role / permission / role_permission.

-- =============================================================
-- 1. Rôles canoniques (exactement 3, jamais ROLE_VISITOR)
-- =============================================================

INSERT INTO role (name, code, created_at, updated_at)
VALUES
    ('Membre', 'ROLE_MEMBER', now(), now()),
    ('Bibliothécaire', 'ROLE_LIBRARIAN', now(), now()),
    ('Administrateur', 'ROLE_ADMIN', now(), now());

-- =============================================================
-- 2. Permissions canoniques V1 (exactement 19, aucune *_SELF)
-- =============================================================

INSERT INTO permission (name, code, created_at, updated_at)
VALUES
    ('Consultation du catalogue', 'CATALOGUE_READ', now(), now()),
    ('Gestion du catalogue', 'CATALOGUE_MANAGE', now(), now()),
    ('Consultation des exemplaires', 'COPY_READ', now(), now()),
    ('Gestion des exemplaires', 'COPY_MANAGE', now(), now()),
    ('Consultation des prêts', 'LOAN_READ', now(), now()),
    ('Gestion des prêts', 'LOAN_MANAGE', now(), now()),
    ('Consultation des réservations', 'RESERVATION_READ', now(), now()),
    ('Gestion des réservations', 'RESERVATION_MANAGE', now(), now()),
    ('Consultation des amendes', 'FINE_READ', now(), now()),
    ('Gestion des amendes', 'FINE_MANAGE', now(), now()),
    ('Consultation des articles', 'ARTICLE_READ', now(), now()),
    ('Gestion des articles', 'ARTICLE_MANAGE', now(), now()),
    ('Publication des articles', 'ARTICLE_PUBLISH', now(), now()),
    ('Consultation des utilisateurs', 'USER_READ', now(), now()),
    ('Gestion des utilisateurs', 'USER_MANAGE', now(), now()),
    ('Consultation des rôles', 'ROLE_READ', now(), now()),
    ('Gestion des rôles', 'ROLE_MANAGE', now(), now()),
    ('Consultation des paramètres', 'SETTING_READ', now(), now()),
    ('Gestion des paramètres', 'SETTING_MANAGE', now(), now());

-- =============================================================
-- 3. Matrice Role -> Permission (exactement 35 associations)
--
-- ROLE_MEMBER (2)     : CATALOGUE_READ, ARTICLE_READ
-- ROLE_LIBRARIAN (14) : capacités opérationnelles de bibliothèque, sans
--                       administration (USER_MANAGE/ROLE_*/SETTING_*)
-- ROLE_ADMIN (19)     : les 19 permissions canoniques — aucun bypass Java,
--                       ROLE_ADMIN possède ces capacités uniquement parce
--                       que ces 19 lignes existent en base.
-- =============================================================

INSERT INTO role_permission (role_id, permission_id, assigned_at)
SELECT r.id, p.id, now()
FROM (VALUES
    ('ROLE_MEMBER', 'CATALOGUE_READ'),
    ('ROLE_MEMBER', 'ARTICLE_READ'),

    ('ROLE_LIBRARIAN', 'CATALOGUE_READ'),
    ('ROLE_LIBRARIAN', 'CATALOGUE_MANAGE'),
    ('ROLE_LIBRARIAN', 'COPY_READ'),
    ('ROLE_LIBRARIAN', 'COPY_MANAGE'),
    ('ROLE_LIBRARIAN', 'LOAN_READ'),
    ('ROLE_LIBRARIAN', 'LOAN_MANAGE'),
    ('ROLE_LIBRARIAN', 'RESERVATION_READ'),
    ('ROLE_LIBRARIAN', 'RESERVATION_MANAGE'),
    ('ROLE_LIBRARIAN', 'FINE_READ'),
    ('ROLE_LIBRARIAN', 'FINE_MANAGE'),
    ('ROLE_LIBRARIAN', 'ARTICLE_READ'),
    ('ROLE_LIBRARIAN', 'ARTICLE_MANAGE'),
    ('ROLE_LIBRARIAN', 'ARTICLE_PUBLISH'),
    ('ROLE_LIBRARIAN', 'USER_READ'),

    ('ROLE_ADMIN', 'CATALOGUE_READ'),
    ('ROLE_ADMIN', 'CATALOGUE_MANAGE'),
    ('ROLE_ADMIN', 'COPY_READ'),
    ('ROLE_ADMIN', 'COPY_MANAGE'),
    ('ROLE_ADMIN', 'LOAN_READ'),
    ('ROLE_ADMIN', 'LOAN_MANAGE'),
    ('ROLE_ADMIN', 'RESERVATION_READ'),
    ('ROLE_ADMIN', 'RESERVATION_MANAGE'),
    ('ROLE_ADMIN', 'FINE_READ'),
    ('ROLE_ADMIN', 'FINE_MANAGE'),
    ('ROLE_ADMIN', 'ARTICLE_READ'),
    ('ROLE_ADMIN', 'ARTICLE_MANAGE'),
    ('ROLE_ADMIN', 'ARTICLE_PUBLISH'),
    ('ROLE_ADMIN', 'USER_READ'),
    ('ROLE_ADMIN', 'USER_MANAGE'),
    ('ROLE_ADMIN', 'ROLE_READ'),
    ('ROLE_ADMIN', 'ROLE_MANAGE'),
    ('ROLE_ADMIN', 'SETTING_READ'),
    ('ROLE_ADMIN', 'SETTING_MANAGE')
) AS matrix(role_code, permission_code)
JOIN role r ON r.code = matrix.role_code
JOIN permission p ON p.code = matrix.permission_code;
