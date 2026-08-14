import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { provideApiConfiguration } from './core/api/api.providers';
import { PrimatisPreset } from './core/theme/primatis-preset';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
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
  ],
};
