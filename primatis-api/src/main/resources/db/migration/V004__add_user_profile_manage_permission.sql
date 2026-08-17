-- PRIMATIS — V004 — Ajout de la permission USER_PROFILE_MANAGE
--
-- DEV-05.8 introduit la gestion d'adresse (Address/Residence) pour tout
-- AppUser, distincte de l'administration des comptes (USER_MANAGE). Une
-- nouvelle permission est nécessaire pour que ROLE_LIBRARIAN/ROLE_ADMIN
-- puissent modifier l'adresse d'un utilisateur cible sans détourner
-- USER_READ (lecture) ni élargir USER_MANAGE (administration de compte).
--
-- V001/V002/V003 restent immuables : cette migration n'altère aucune table,
-- séquence ni contrainte existante, elle insère uniquement de nouvelles
-- lignes dans le schéma déjà créé par V001 (permission, role_permission),
-- exactement comme V002.
--
-- Résolution par code (role.code, permission.code), jamais par identifiant
-- technique supposé — même convention que V002.
--
-- ROLE_MEMBER n'est volontairement pas concerné : DEV-05.8-DEC-08.

-- =============================================================
-- 1. Nouvelle permission canonique
-- =============================================================

INSERT INTO permission (name, code, created_at, updated_at)
VALUES
    ('Gestion du profil utilisateur', 'USER_PROFILE_MANAGE', now(), now());

-- =============================================================
-- 2. Attribution : ROLE_LIBRARIAN et ROLE_ADMIN uniquement
-- =============================================================

INSERT INTO role_permission (role_id, permission_id, assigned_at)
SELECT r.id, p.id, now()
FROM (VALUES
    ('ROLE_LIBRARIAN', 'USER_PROFILE_MANAGE'),
    ('ROLE_ADMIN', 'USER_PROFILE_MANAGE')
) AS matrix(role_code, permission_code)
JOIN role r ON r.code = matrix.role_code
JOIN permission p ON p.code = matrix.permission_code;
