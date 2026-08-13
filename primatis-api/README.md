# PRIMATIS API

Backend Spring Boot de PRIMATIS.

## Prérequis

- Java 21 LTS
- Maven Wrapper (fourni, `./mvnw`)
- PostgreSQL 17 démarré en local

## Base de données locale

Le schéma est entièrement géré par Flyway (`spring.jpa.hibernate.ddl-auto=validate`).

Deux bases locales sont utilisées :

```text
primatis_dev
→ développement (src/main/resources/application.yml)

primatis_test
→ tests d'intégration (src/test/resources/application-test.yml, profil "test")
```

Création du rôle applicatif et des bases (à exécuter une fois, en tant que superutilisateur PostgreSQL) :

```bash
sudo -u postgres psql <<'SQL'
CREATE ROLE primatis WITH LOGIN PASSWORD 'choisis-un-mot-de-passe-local';
CREATE DATABASE primatis_dev  OWNER primatis;
CREATE DATABASE primatis_test OWNER primatis;
SQL
```

Aucun mot de passe réel n'est versionné dans le repository.

## Configuration locale

Les identifiants de connexion sont fournis par variables d'environnement (jamais codés en dur) :

```text
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

L'URL de connexion (hôte/port/nom de base) n'est pas un secret : elle est fixée directement par profil dans `application.yml` (dev) et `application-test.yml` (test), et n'est volontairement pas surchargeable par variable d'environnement, car les variables d'environnement Spring Boot priment sur les fichiers `application*.yml` — un `SPRING_DATASOURCE_URL` global écraserait aussi bien la configuration dev que celle de test.

Exemple de fichier local non versionné (`primatis-api/.env.local`, couvert par `.gitignore`) :

```bash
export SPRING_DATASOURCE_USERNAME=primatis
export SPRING_DATASOURCE_PASSWORD=ton-mot-de-passe-choisi
```

À charger avant toute commande Maven :

```bash
set -a && source .env.local && set +a
```

## Commandes utiles

```bash
./mvnw compile        # compilation
./mvnw test           # tests (utilise primatis_test, profil "test")
./mvnw package         # build complet + jar exécutable
./mvnw spring-boot:run # démarrage local (utilise primatis_dev)
```

## Migrations Flyway

```text
src/main/resources/db/migration/
```

Convention : `V001__description.sql`, `V002__description.sql`, etc.

Aucune migration métier n'existe encore à ce stade (DEV-02.2). La migration initiale (23 tables du modèle relationnel PRIMATIS) sera créée en DEV-02.3.
