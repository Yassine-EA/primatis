import { defineConfig, devices } from '@playwright/test';

/**
 * Configuration Playwright E2E PRIMATIS (DEV-14.2).
 *
 * Baseline DEV-DEC-0069 : exécution strictement sérielle (workers = 1,
 * fullyParallel = false) tant qu'un parallélisme sûr n'est pas démontré —
 * aucun scénario ne doit dépendre de l'ordre d'exécution, mais l'exécution
 * elle-même reste séquentielle par prudence à ce stade.
 *
 * webServer démarre backend (profil Spring "e2e" → primatis_e2e,
 * DEV-DEC-0066) et frontend (proxy Angular existant vers :8080) ensemble ;
 * `reuseExistingServer` (hors CI) permet de relancer les tests sans
 * redémarrer les deux process à chaque fois pendant le développement des
 * scénarios E2E.
 */
export default defineConfig({
  testDir: './e2e/tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['html', { outputFolder: 'e2e/report', open: 'never' }], ['list']],

  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: [
    {
      // Backend Spring Boot, profil "e2e" → primatis_e2e (DEV-DEC-0066).
      // Charge primatis-api/.env.local (identifiants locaux non versionnés,
      // README primatis-api §Configuration locale) avant de démarrer.
      command:
        "bash -c 'set -a && source .env.local && set +a && SPRING_PROFILES_ACTIVE=e2e ./mvnw spring-boot:run'",
      cwd: '../primatis-api',
      url: 'http://localhost:8080/api/v1/titles',
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      // Frontend Angular, proxy.conf.json existant (→ backend :8080).
      command: 'npm run start',
      cwd: '.',
      url: 'http://localhost:4200/login',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
