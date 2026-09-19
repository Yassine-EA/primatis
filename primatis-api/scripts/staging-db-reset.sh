#!/usr/bin/env bash
#
# PRIMATIS — Reset de la base staging dédiée (DEV-16.G3.5).
#
# Remet `primatis_staging` dans un état vide et propre : DROP SCHEMA public
# CASCADE + CREATE SCHEMA public. Flyway (via le prochain démarrage de
# l'application sous le profil "staging", ou via une exécution directe des
# migrations) reconstruit ensuite le schéma à l'identique de primatis_dev/
# primatis_test/primatis_e2e, puis le pipeline data-seeding (profil "full_consolidated")
# recharge le catalogue et les scénarios.
#
# Mécanisme identique et déjà validé pour primatis_e2e (DEV-14.2,
# primatis-api/scripts/e2e-db-reset.sh) : ce script ne fait JAMAIS
# DROP/CREATE DATABASE (le rôle applicatif "primatis" n'a pas l'attribut
# CREATEDB, comme primatis_dev/primatis_test/primatis_e2e — création
# initiale = opération manuelle superutilisateur, hors de ce script).
# Le rôle applicatif "primatis", propriétaire de primatis_staging, peut en
# revanche DROP/CREATE le schéma "public" qu'elle contient (PostgreSQL 15+ :
# le schéma public appartient au pseudo-rôle pg_database_owner, dont tout
# propriétaire de base est membre implicite) — aucun privilège
# superutilisateur n'est donc nécessaire pour ce reset.
#
# Garde-fou : la cible est strictement littérale ("primatis_staging"),
# jamais paramétrable en argument — ce script ne peut pas être détourné
# pour vider accidentellement primatis_dev/primatis_test/primatis_e2e.

set -euo pipefail

readonly STAGING_DB_NAME="primatis_staging"

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

echo "Reset de la base staging dédiée : ${STAGING_DB_NAME} (hôte ${PGHOST_LOCAL}:${PGPORT_LOCAL}, rôle ${PGUSER_LOCAL})"

# Garde-fou explicite : refuse de continuer si la connexion n'aboutit pas
# exactement à la base attendue (défense en profondeur, indépendante des
# arguments passés à psql ci-dessous).
actual_db="$(psql -h "${PGHOST_LOCAL}" -p "${PGPORT_LOCAL}" -U "${PGUSER_LOCAL}" -d "${STAGING_DB_NAME}" -tAc "SELECT current_database();")"
if [[ "${actual_db}" != "${STAGING_DB_NAME}" ]]; then
  echo "ERREUR : connexion inattendue à '${actual_db}' au lieu de '${STAGING_DB_NAME}' — abandon." >&2
  exit 1
fi

psql -h "${PGHOST_LOCAL}" -p "${PGPORT_LOCAL}" -U "${PGUSER_LOCAL}" -d "${STAGING_DB_NAME}" -v ON_ERROR_STOP=1 <<SQL
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
SQL

echo "primatis_staging : schéma public vidé. Prochaine étape : Flyway (mvn -Dspring-boot.run.profiles=staging) reconstruit le schéma, puis data-seeding (profil full_consolidated) recharge catalogue + scénarios."
