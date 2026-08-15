import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoadingState } from './loading-state';

describe('LoadingState', () => {
  let fixture: ComponentFixture<LoadingState>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [LoadingState] });
    fixture = TestBed.createComponent(LoadingState);
  });

  it('should render an accessible status region', () => {
    fixture.detectChanges();

    const root = fixture.nativeElement.querySelector('.loading-state');
    expect(root?.getAttribute('role')).toBe('status');
    expect(root?.getAttribute('aria-live')).toBe('polite');
  });

  it('should render the default message when none is provided', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chargement en cours…');
  });

  it('should render a configurable message', () => {
    fixture.componentRef.setInput('message', 'Chargement des prêts…');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chargement des prêts…');
    expect(fixture.nativeElement.textContent).not.toContain('Chargement en cours…');
  });
});
