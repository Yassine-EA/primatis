import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ApiErrorResponse } from '../../../core/models/api-error-response';
import { AuthService } from '../../services/auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let authServiceMock: { login: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    authServiceMock = { login: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });

  function setFormValue(email: string, password: string): void {
    component.form.setValue({ email, password });
  }

  it('should not call AuthService.login when the form is invalid', () => {
    setFormValue('', '');

    component.submit();

    expect(authServiceMock.login).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBe(true);
    expect(component.form.controls.password.touched).toBe(true);
  });

  it('should reject an invalid email format', () => {
    setFormValue('not-an-email', 'Correct-Password-2026!');

    component.submit();

    expect(authServiceMock.login).not.toHaveBeenCalled();
    expect(component.form.controls.email.invalid).toBe(true);
  });

  it('should call AuthService.login with the entered credentials', () => {
    authServiceMock.login.mockReturnValue(of(undefined));
    setFormValue('librarian@primatis.test', 'Correct-Password-2026!');

    component.submit();

    expect(authServiceMock.login).toHaveBeenCalledWith('librarian@primatis.test', 'Correct-Password-2026!');
  });

  it('should reflect a loading state while the login request is pending', () => {
    const pending = new Subject<void>();
    authServiceMock.login.mockReturnValue(pending.asObservable());
    setFormValue('librarian@primatis.test', 'Correct-Password-2026!');

    component.submit();
    expect(component.submitting()).toBe(true);

    pending.next();
    pending.complete();
    expect(component.submitting()).toBe(false);
  });

  it('should navigate to "/" after a successful login', () => {
    authServiceMock.login.mockReturnValue(of(undefined));
    setFormValue('librarian@primatis.test', 'Correct-Password-2026!');

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(component.errorMessage()).toBeNull();
  });

  it('should display a user-facing message for INVALID_CREDENTIALS without exposing backend details', () => {
    authServiceMock.login.mockReturnValue(throwError(() => httpError('INVALID_CREDENTIALS', 401)));
    setFormValue('librarian@primatis.test', 'Wrong-Password');

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Adresse e-mail ou mot de passe incorrect.');
    expect(component.submitting()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Adresse e-mail ou mot de passe incorrect.');
  });

  it('should display a user-facing message for ACCOUNT_TEMPORARILY_LOCKED', () => {
    authServiceMock.login.mockReturnValue(throwError(() => httpError('ACCOUNT_TEMPORARILY_LOCKED', 401)));
    setFormValue('librarian@primatis.test', 'Wrong-Password');

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe(
      'Compte temporairement verrouillé suite à plusieurs échecs de connexion. Réessayez plus tard.',
    );
  });

  it('should fall back to a generic message for unexpected/network errors', () => {
    authServiceMock.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    setFormValue('librarian@primatis.test', 'Correct-Password-2026!');

    component.submit();

    expect(component.errorMessage()).toBe('Une erreur est survenue. Veuillez réessayer.');
  });
});

function httpError(code: string, status: number): HttpErrorResponse {
  const body: ApiErrorResponse = {
    timestamp: '2026-08-15T12:00:00Z',
    status,
    error: 'Unauthorized',
    code,
    message: 'Message backend non affiché tel quel.',
    path: '/api/v1/auth/login',
    fieldErrors: [],
  };
  return new HttpErrorResponse({ error: body, status, statusText: 'Unauthorized' });
}
