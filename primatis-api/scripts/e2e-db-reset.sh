#!/usr/bin/env bash
#
# PRIMATIS — Reset de la base E2E dédiée (DEV-DEC-0069).
#
# Remet `primatis_e2e` dans un état vide et propre : DROP SCHEMA public
# CASCADE + CREATE SCHEMA public. Flyway (via le prochain démarrage de
# l'application sous le profil "e2e") reconstruit ensuite le schéma à
# l'identique de primatis_dev/primatis_test, puis E2eBaselineProvisioner
# provisionne la baseline de comptes (DEV-DEC-0067).
#
# Ce script ne fait JAMAIS DROP/CREATE DATABASE : la création initiale de
# `primatis_e2e` nécessite un rôle PostgreSQL superutilisateur (le rôle
# applicatif "primatis" n'a pas l'attribut CREATEDB, comme primatis_dev/
# primatis_test) et reste une opération manuelle ponctuelle, hors de ce
# script — voir primatis-api/README.md « Base de données locale ».
#
# Le rôle applicatif "primatis", propriétaire de primatis_e2e une fois
# celle-ci créée, peut en revanche DROP/CREATE le schéma "public" qu'il
# contient (PostgreSQL 15+ : le schéma public appartient au pseudo-rôle
# pg_database_owner, dont tout propriétaire de base est membre implicite)
# — aucun privilège superutilisateur n'est donc nécessaire pour un reset
# répété.
#
# Garde-fou : la cible est strictement littérale ("primatis_e2e"), jamais
# paramétrable en argument — ce script ne peut pas être détourné pour
# vider accidentellement primatis_dev/primatis_test/primatis_preview.

set -euo pipefail

readonly E2E_DB_NAME="primatis_e2e"

readonly PGHOST_LOCAL="${PGHOST:-localhost}"
readonly PGPORT_LOCAL="${PGPORT:-5432}"
readonly PGUSER_LOCAL="${SPRING_DATASOURCE_USERNAME:-primatis}"

if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
  echo "ERREUR : SPRING_DATASOURCE_PASSWORD n'est pas défini." >&2
  echo "Charger primatis-api/.env.local avant d'exécuter ce script :" >&2
  echo "  set -a && source primatis-api/.env.local && set +a" >&2
  exit 1
fi

export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD}"

echo "Reset de la base E2E dédiée : ${E2E_DB_NAME} (hôte ${PGHOST_LOCAL}:${PGPORT_LOCAL}, rôle ${PGUSER_LOCAL})"

# Garde-fou explicite : refuse de continuer si la connexion n'aboutit pas
# exactement à la base attendue (défense en profondeur, indépendante des
# arguments passés à psql ci-dessous).
actual_db="$(psql -h "${PGHOST_LOCAL}" -p "${PGPORT_LOCAL}" -U "${PGUSER_LOCAL}" -d "${E2E_DB_NAME}" -tAc "SELECT current_database();")"
if [[ "${actual_db}" != "${E2E_DB_NAME}" ]]; then
  echo "ERREUR : connexion inattendue à '${actual_db}' au lieu de '${E2E_DB_NAME}' — abandon." >&2
  exit 1
fi

psql -h "${PGHOST_LOCAL}" -p "${PGPORT_LOCAL}" -U "${PGUSER_LOCAL}" -d "${E2E_DB_NAME}" -v ON_ERROR_STOP=1 <<SQL
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
SQL

echo "primatis_e2e : schéma public vidé. Prochain démarrage backend (profil e2e) : Flyway reconstruit le schéma + E2eBaselineProvisioner provisionne la baseline de comptes."
