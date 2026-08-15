import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmptyState } from './empty-state';

describe('EmptyState', () => {
  let fixture: ComponentFixture<EmptyState>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EmptyState] });
    fixture = TestBed.createComponent(EmptyState);
  });

  it('should render default title and message when none are provided', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state-title')?.textContent).toContain('Aucun résultat');
    expect(fixture.nativeElement.querySelector('.empty-state-message')?.textContent).toContain(
      'Il n’y a rien à afficher pour le moment.',
    );
  });

  it('should render a configurable title and message', () => {
    fixture.componentRef.setInput('title', 'Aucun prêt en cours');
    fixture.componentRef.setInput('message', 'Vous n’avez aucun prêt actif actuellement.');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state-title')?.textContent).toContain('Aucun prêt en cours');
    expect(fixture.nativeElement.querySelector('.empty-state-message')?.textContent).toContain(
      'Vous n’avez aucun prêt actif actuellement.',
    );
  });
});
