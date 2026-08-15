import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { provideApiConfiguration } from './core/api/api.providers';
import { authInterceptor } from './core/http/auth.interceptor';
import { PrimatisPreset } from './core/theme/primatis-preset';

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
  ],
};
