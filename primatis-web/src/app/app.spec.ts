import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
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
        MessageService,
        ConfirmationService,
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

  it('should render the global toast and confirm dialog once', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('p-toast')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('p-confirmdialog')).toBeTruthy();
  });
});
