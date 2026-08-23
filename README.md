# PRIMATIS

PRIMATIS est une application web de gestion de bibliothèque développée dans le cadre d’un projet de fin d’études.

L’application couvre les principaux besoins d’une bibliothèque : gestion du catalogue, des exemplaires, des adhérents, des prêts, des réservations, des amendes, des notifications et des articles.

## Stack technique

### Backend

- Java
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- JWT avec signature RS256
- JUnit

### Frontend

- Angular
- TypeScript
- PrimeNG
- RxJS
- Signals
- Vitest

## Fonctionnalités principales

- Authentification et autorisation par rôles et permissions
- Gestion des utilisateurs et des adhérents
- Gestion du catalogue
  - titres
  - auteurs
  - genres
  - exemplaires
- Gestion des prêts et retours
- Gestion des réservations
- Gestion des retards et amendes
- Notifications aux utilisateurs
- Consultation et gestion d’articles
- Interfaces dédiées aux membres et au personnel de la bibliothèque

## Architecture

Le projet est séparé en deux applications principales :

```text
primatis/
├── primatis-api/     # API REST Spring Boot
├── primatis-web/     # Frontend Angular
├── data-seeding/     # Génération / import de données
└── README.md
```

Le backend expose une API REST consommée par l’application Angular.

PostgreSQL est utilisé comme système de gestion de base de données et Flyway assure le versionnement du schéma.

## Sécurité

PRIMATIS utilise une authentification JWT avec signature RSA (`RS256`).

Trois rôles principaux sont prévus :

- `ROLE_MEMBER`
- `ROLE_LIBRARIAN`
- `ROLE_ADMIN`

Les accès aux fonctionnalités sont ensuite contrôlés par des permissions métier plus fines.

## Prérequis

- Java 21+
- Node.js
- npm
- PostgreSQL 17+
- Git

## Backend

```bash
cd primatis-api
```

Configurer l’environnement local, puis lancer :

```bash
./mvnw spring-boot:run
```

Pour exécuter les tests :

```bash
./mvnw test
```

## Frontend

```bash
cd primatis-web
npm install
npm start
```

Tests :

```bash
npm test
```

Build de production :

```bash
npm run build
```

## Base de données

Le schéma PostgreSQL est géré par Flyway.

Les migrations sont situées dans :

```text
primatis-api/src/main/resources/db/migration/
```

Elles sont appliquées automatiquement au démarrage de l’application.

## État du projet

PRIMATIS est actuellement en développement actif.

Les principaux modules métier sont progressivement intégrés et couverts par des tests automatisés backend et frontend.

## Auteur

**Yassine El Âboubi**

Projet de fin d’études — Informatique de gestion.

GitHub : [Yassine-EA](https://github.com/Yassine-EA)
