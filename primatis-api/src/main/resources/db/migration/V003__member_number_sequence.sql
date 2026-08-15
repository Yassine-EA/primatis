-- PRIMATIS — V003 — Séquence de génération du numéro d'adhérent (memberNumber)
--
-- Ajoute la séquence PostgreSQL dédiée fournissant la valeur numérique
-- utilisée par le backend (DEV-05.5, MemberNumberGenerator) pour construire
-- app_user.member_number au format "M" + 9 chiffres (M000000001 ...
-- M999999999). Le backend ne calcule jamais ce numéro autrement
-- (interdiction explicite de SELECT MAX(member_number)+1, parsing du
-- dernier numéro, ou comptage de lignes) : nextval() garantit l'unicité et
-- la non-réutilisation même en cas d'accès concurrent, au prix de trous
-- possibles si une transaction échoue après avoir consommé une valeur —
-- accepté explicitement (DEV-05.5) : l'exigence est unicité +
-- non-réutilisation, pas continuité parfaite.
--
-- Pas de DEFAULT nextval('member_number_seq') sur app_user.member_number :
-- la colonne reste une VARCHAR construite explicitement par le backend
-- ("M" + zéros à gauche), pas un entier auto-incrémenté — donc pas de
-- ALTER SEQUENCE ... OWNED BY (réservé aux séquences pilotant directement
-- un DEFAULT de colonne, cf. V001).
--
-- La contrainte uq_app_user_member_number (V001) reste le filet de
-- sécurité final contre toute collision. Une contrainte CHECK de format est
-- ajoutée en complément : protection structurelle simple et proprement
-- exprimable en PostgreSQL (database.md "Structural integrity" — Service
-- valide la règle métier, PostgreSQL protège l'intégrité structurelle,
-- les deux protections restent pertinentes ici).
--
-- V001 et V002 restent immuables : cette migration n'altère aucune ligne ni
-- contrainte existante, elle ajoute uniquement une nouvelle séquence et une
-- nouvelle contrainte CHECK sur une colonne déjà existante de app_user.

CREATE SEQUENCE member_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_member_number_format
    CHECK (member_number IS NULL OR member_number ~ '^M[0-9]{9}$');
