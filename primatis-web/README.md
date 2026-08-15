# PrimatisWeb

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.21.

## Développement avec le backend PRIMATIS (DEV-04.13)

L'API frontend cible toujours `/api/v1` (`src/environments/environment.ts`) — aucune
URL de backend n'est codée en dur côté Angular.

Pour le développement local, un proxy Angular (`proxy.conf.json`) redirige `/api`
vers `http://localhost:8080` (backend Spring Boot local). Il est automatiquement
utilisé par `ng serve`/`npm start` — voir `angular.json` (`architect.serve.options.proxyConfig`).

Démarrage complet en local (deux terminaux) :

```bash
# Terminal 1 — backend (voir primatis-api/README.md pour les variables requises,
# notamment SPRING_DATASOURCE_PASSWORD et les clés JWT — jamais versionnées)
cd primatis-api
set -a && source .env.local && set +a
export PRIMATIS_JWT_PRIVATE_KEY_PATH=file:/chemin/local/jwt-private.pem
export PRIMATIS_JWT_PUBLIC_KEY_PATH=file:/chemin/local/jwt-public.pem
./mvnw spring-boot:run   # http://localhost:8080

# Terminal 2 — frontend
cd primatis-web
npm start                # http://localhost:4200, proxy /api -> :8080
```

Cette configuration de proxy ne concerne que `ng serve` : elle n'affecte jamais
`ng build` (production), qui continue de cibler `apiBaseUrl = /api/v1` tel quel.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
