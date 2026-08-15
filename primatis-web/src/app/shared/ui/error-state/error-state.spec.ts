import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorState } from './error-state';

describe('ErrorState', () => {
  let fixture: ComponentFixture<ErrorState>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ErrorState] });
    fixture = TestBed.createComponent(ErrorState);
    fixture.componentRef.setInput('message', 'Impossible de contacter le serveur. Veuillez réessayer.');
  });

  it('should render the provided message', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-state-message')?.textContent).toContain(
      'Impossible de contacter le serveur. Veuillez réessayer.',
    );
  });

  it('should render as an accessible alert region', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-state')?.getAttribute('role')).toBe('alert');
  });

  it('should never render technical details alongside the message', () => {
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Exception');
    expect(text).not.toContain('stack');
    expect(text).not.toContain('.java');
  });

  it('should not render a retry button by default', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-state-retry')).toBeNull();
  });

  it('should render a retry button when showRetry is true', () => {
    fixture.componentRef.setInput('showRetry', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-state-retry')).not.toBeNull();
  });

  it('should emit retry when the retry button is clicked', () => {
    fixture.componentRef.setInput('showRetry', true);
    fixture.detectChanges();

    let emitted = false;
    fixture.componentInstance.retry.subscribe(() => {
      emitted = true;
    });

    (fixture.nativeElement.querySelector('.error-state-retry') as HTMLButtonElement).click();

    expect(emitted).toBe(true);
  });
});
