import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';

import { App } from './app';
import { PrimatisPreset } from './core/theme/primatis-preset';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
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

  it('should render the PRIMATIS foundation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toContain('PRIMATIS');
    expect(compiled.textContent).toContain('PrimeNG opérationnel');
  });
});
