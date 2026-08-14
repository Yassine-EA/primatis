import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { API_BASE_URL } from './api-base-url.token';
import { provideApiConfiguration } from './api.providers';

describe('API configuration', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideApiConfiguration()],
    });
  });

  it('should expose the configured API base URL', () => {
    expect(TestBed.inject(API_BASE_URL)).toBe(environment.apiBaseUrl);
  });

  it('should use the versioned PRIMATIS API path', () => {
    expect(TestBed.inject(API_BASE_URL)).toBe('/api/v1');
  });
});
