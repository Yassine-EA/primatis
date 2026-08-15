import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { environment } from '../../../environments/environment';
import { API_BASE_URL } from './api-base-url.token';

export function provideApiConfiguration(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: API_BASE_URL,
      useValue: environment.apiBaseUrl,
    },
  ]);
}
