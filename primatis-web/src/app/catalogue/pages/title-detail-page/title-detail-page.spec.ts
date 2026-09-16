import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { ReservationResponse } from '../../../reservations/models/reservation-response';
import { ReservationApiService } from '../../../reservations/services/reservation-api.service';
import { TitleDetailResponse } from '../../models/title-detail-response';
import { CatalogueApiService } from '../../services/catalogue-api.service';
import { TitleDetailPage } from './title-detail-page';

function buildTitleDetail(overrides: Partial<TitleDetailResponse> = {}): TitleDetailResponse {
  return {
    id: 1,
    isbn: '9782070409181',
    title: 'Les Misérables',
    subtitle: null,
    summary: 'Une fresque sociale du XIXe siècle.',
    publicationYear: 1862,
    language: 'FR',
    pageCount: 1500,
    publisher: 'Gallimard',
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    authors: [{ id: 1, fullName: 'Victor Hugo', birthDate: null, deathDate: null, nationality: null, biography: null }],
    genres: [{ id: 1, code: 'CLASSIC', label: 'Classique', description: null }],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function buildReservation(overrides: Partial<ReservationResponse> = {}): ReservationResponse {
  return {
    id: 1,
    member: { id: 1, memberNumber: 'M-0001', firstName: 'Prénom', lastName: 'Nom' },
    title: { id: 1, title: 'Les Misérables', isbn: null },
    assignedCopy: null,
    fulfilledByLoanId: null,
    reservationDate: '2026-01-01T00:00:00Z',
    expirationDate: null,
    reservationStatus: 'WAITING',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function apiHttpError(code: string, message: string, status = 404): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: 'Error',
      code,
      message,
      path: '/api/v1/titles/999',
      fieldErrors: [],
    },
  });
}

describe('TitleDetailPage', () => {
  let fixture: ComponentFixture<TitleDetailPage>;
  let component: TitleDetailPage;
  let catalogueApiServiceMock: { getTitleById: ReturnType<typeof vi.fn> };
  let reservationApiServiceMock: { createOwnReservation: ReturnType<typeof vi.fn> };
  let authServiceMock: { authenticated: ReturnType<typeof vi.fn>; hasRole: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  function configure(initialId = '1'): void {
    paramMap$ = new BehaviorSubject(convertToParamMap({ id: initialId }));
    catalogueApiServiceMock = { getTitleById: vi.fn().mockReturnValue(of(buildTitleDetail())) };
    reservationApiServiceMock = { createOwnReservation: vi.fn().mockReturnValue(of(buildReservation())) };
    authServiceMock = { authenticated: vi.fn().mockReturnValue(false), hasRole: vi.fn().mockReturnValue(false) };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [TitleDetailPage],
      providers: [
        provideRouter([]),
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        { provide: ReservationApiService, useValue: reservationApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(TitleDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function asMember(): void {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.hasRole.mockImplementation((role: string) => role === 'ROLE_MEMBER');
  }

  function asStaff(): void {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.hasRole.mockReturnValue(false);
  }

  it('should call getTitleById with the numeric id from the route', () => {
    configure('42');
    createComponent();

    expect(catalogueApiServiceMock.getTitleById).toHaveBeenCalledWith(42);
  });

  it('should render the loaded Title detail', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Les Misérables');
    expect(fixture.nativeElement.textContent).toContain('Une fresque sociale du XIXe siècle.');
  });

  it('should render authors', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Victor Hugo');
  });

  it('should render genres', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Classique');
  });

  it('should omit nullable fields instead of rendering null/undefined', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(
      of(buildTitleDetail({ subtitle: null, isbn: null, publisher: null, pageCount: null, summary: null })),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('null');
    expect(fixture.nativeElement.textContent).not.toContain('undefined');
  });

  it('should not call the API when the route id is not numeric', () => {
    configure('abc');
    createComponent();

    expect(catalogueApiServiceMock.getTitleById).not.toHaveBeenCalled();
  });

  it('should show an ErrorState when the route id is not numeric', () => {
    configure('abc');
    createComponent();

    expect(component.error()?.message).toBe('Identifiant de titre invalide.');
    expect(fixture.nativeElement.textContent).toContain('Identifiant de titre invalide.');
  });

  it('should show an ErrorState (not an empty state) on a 404 TITLE_NOT_FOUND', () => {
    configure('999');
    catalogueApiServiceMock.getTitleById.mockReturnValue(
      throwError(() => apiHttpError('TITLE_NOT_FOUND', 'Aucun titre disponible pour l’identifiant 999.')),
    );
    createComponent();

    expect(component.error()?.message).toBe('Aucun titre disponible pour l’identifiant 999.');
    expect(fixture.nativeElement.querySelector('.error-state')).not.toBeNull();
  });

  it('should show a generic ErrorState message on a network error', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(throwError(() => new Error('boom')));
    createComponent();

    expect(component.error()?.message).toBe('Une erreur est survenue. Veuillez réessayer.');
  });

  it('should show the loading state before the response arrives', () => {
    configure();
    // Observable never emits synchronously: keeps the component in its initial loading state.
    catalogueApiServiceMock.getTitleById.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should render a link back to the public Catalogue (DEV-15.5)', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/catalogue"]')).not.toBeNull();
  });

  it('should set the document title to "<Title> — PRIMATIS" once loaded (DEV-15.5)', () => {
    configure();
    createComponent();

    expect(document.title).toBe('Les Misérables — PRIMATIS');
  });

  it('should show a fallback cover when coverImageUrl is null', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.querySelector('.title-detail-cover .cover-fallback')).not.toBeNull();
  });

  it('should render the real cover image when coverImageUrl is present', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(
      of(buildTitleDetail({ coverImageUrl: 'https://covers.example/1.jpg' })),
    );
    createComponent();

    const img: HTMLImageElement | null = fixture.nativeElement.querySelector('.title-detail-cover img');
    expect(img?.getAttribute('src')).toBe('https://covers.example/1.jpg');
  });

  it('should render a breadcrumb with a link to Home and to the Catalogue', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/catalogue"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe(
      'Les Misérables',
    );
  });

  it('should display the language as a French label rather than the raw code', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Français');
    expect(fixture.nativeElement.textContent).not.toContain('>FR<');
  });

  it('should show an honest empty copy when no summary is available (never a fabricated summary)', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(of(buildTitleDetail({ summary: null })));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun résumé disponible pour cet ouvrage.');
  });

  it('should render every real genre as a tag, in the order returned by the backend', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(
      of(
        buildTitleDetail({
          genres: [
            { id: 2, code: 'ESSAY', label: 'Essai', description: null },
            { id: 5, code: 'SCIENCE', label: 'Sciences', description: null },
          ],
        }),
      ),
    );
    createComponent();

    const tags = fixture.nativeElement.querySelectorAll('.title-detail-genres p-tag');
    expect(tags.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Essai');
    expect(fixture.nativeElement.textContent).toContain('Sciences');
  });

  it('should never invent an availability indicator (forbidden by contract, DEV-15.5/B2)', () => {
    configure();
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Disponible');
    expect(text).not.toContain('exemplaire');
  });

  describe('Reservation CTA (DEV-15.5)', () => {
    it('should show "Se connecter pour réserver" with a returnUrl for an anonymous visitor', () => {
      configure();
      createComponent();

      const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('.title-detail-cta-login');
      expect(link?.textContent?.trim()).toBe('Se connecter pour réserver');
      expect(link?.getAttribute('href')).toContain('/login');
      expect(link?.getAttribute('href')).toContain('returnUrl');
    });

    it('should show no CTA at all for an authenticated Staff/Admin user', () => {
      configure();
      asStaff();
      createComponent();

      expect(fixture.nativeElement.querySelector('.title-detail-cta-login')).toBeNull();
      expect(fixture.nativeElement.querySelector('.title-detail-cta button')).toBeNull();
    });

    it('should show a "Réserver" button for an authenticated ROLE_MEMBER', () => {
      configure();
      asMember();
      createComponent();

      const button: HTMLButtonElement | null = fixture.nativeElement.querySelector('.title-detail-cta button');
      expect(button?.textContent?.trim()).toBe('Réserver');
    });

    it('should call createOwnReservation with the titleId and show success on click', () => {
      configure();
      asMember();
      createComponent();

      (fixture.nativeElement.querySelector('.title-detail-cta button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(reservationApiServiceMock.createOwnReservation).toHaveBeenCalledWith({ titleId: 1 });
      expect(component.reservationState()).toBe('success');
      expect(fixture.nativeElement.textContent).toContain('Réservation créée.');
      expect(messageServiceMock.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'success' }),
      );
    });

    it('should never call the API twice for a rapid double click (no double submit)', () => {
      configure();
      asMember();
      reservationApiServiceMock.createOwnReservation.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
      createComponent();

      const button: HTMLButtonElement = fixture.nativeElement.querySelector('.title-detail-cta button');
      button.click();
      fixture.detectChanges();
      button.click();

      expect(reservationApiServiceMock.createOwnReservation).toHaveBeenCalledTimes(1);
    });

    it('should show the backend business error message and allow retrying', () => {
      configure();
      asMember();
      reservationApiServiceMock.createOwnReservation.mockReturnValue(
        throwError(() => apiHttpError('RESERVATION_ALREADY_ACTIVE', 'Une réservation active existe déjà.', 409)),
      );
      createComponent();

      (fixture.nativeElement.querySelector('.title-detail-cta button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(component.reservationState()).toBe('error');
      expect(fixture.nativeElement.textContent).toContain('Une réservation active existe déjà.');
      expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      // Le bouton "Réserver" reste disponible pour une nouvelle tentative.
      expect(fixture.nativeElement.querySelector('.title-detail-cta button')).not.toBeNull();
    });
  });
});
