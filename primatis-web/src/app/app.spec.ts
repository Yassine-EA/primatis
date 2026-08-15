import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { App } from './app';
import { routes } from './app.routes';
import { PrimatisPreset } from './core/theme/primatis-preset';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
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
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should expose the router outlet', () => {
    const fixture = TestBed.createComponent(App);

    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  });
});
