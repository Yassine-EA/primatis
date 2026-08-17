import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { provideApiConfiguration } from './core/api/api.providers';
import { authInterceptor } from './core/http/auth.interceptor';
import { PrimatisPreset } from './core/theme/primatis-preset';

/**
 * `MessageService`/`ConfirmationService` enregistrés une seule fois ici
 * (DEV-05.12 Décision 9) : `<p-toast/>`/`<p-confirmdialog/>` vivent une seule
 * fois dans le composant racine `App`, jamais dupliqués par page/layout.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideApiConfiguration(),
    providePrimeNG({
      theme: {
        preset: PrimatisPreset,
        options: {
          darkModeSelector: false,
        },
      },
      ripple: true,
    }),
    MessageService,
    ConfirmationService,
  ],
};
