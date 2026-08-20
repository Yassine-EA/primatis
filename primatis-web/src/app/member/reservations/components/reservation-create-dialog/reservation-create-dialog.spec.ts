import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { TitleResponse } from '../../../../catalogue/models/title-response';
import { CatalogueApiService } from '../../../../catalogue/services/catalogue-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { ReservationCreateDialog } from './reservation-create-dialog';

function buildTitle(overrides: Partial<TitleResponse> = {}): TitleResponse {
  return {
    id: 30,
    isbn: null,
    title: 'Les Misérables',
    subtitle: null,
    publicationYear: 1862,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    ...overrides,
  };
}

function buildReservation(overrides: Partial<ReservationResponse> = {}): ReservationResponse {
  return {
    id: 1,
    member: { id: 7, memberNumber: 'M000000001', firstName: 'Marie', lastName: 'Curie' },
    title: { id: 30, title: 'Les Misérables', isbn: null },
    assignedCopy: null,
    fulfilledByLoanId: null,
    reservationDate: '2026-08-19T09:00:00Z',
    expirationDate: null,
    reservationStatus: 'WAITING',
    createdAt: '2026-08-19T09:00:00Z',
    updatedAt: '2026-08-19T09:00:00Z',
    ...overrides,
  };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: 'Error',
      code,
      message,
      path: '/api/v1/me/reservations',
      fieldErrors: [],
    },
  });
}

describe('ReservationCreateDialog', () => {
  let fixture: ComponentFixture<ReservationCreateDialog>;
  let component: ReservationCreateDialog;
  let catalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let staffCatalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let reservationApiServiceMock: {
    createOwnReservation: ReturnType<typeof vi.fn>;
    createReservation: ReturnType<typeof vi.fn>;
  };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    catalogueApiServiceMock = {
      searchTitles: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    };
    // Jamais utilisé par ce dialog self-service (staff-only, 403 pour un
    // membre) — présent uniquement pour prouver qu'il n'est jamais appelé.
    staffCatalogueApiServiceMock = { searchTitles: vi.fn() };
    reservationApiServiceMock = { createOwnReservation: vi.fn(), createReservation: vi.fn() };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ReservationCreateDialog],
      providers: [
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: ReservationApiService, useValue: reservationApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(visible = true): void {
    fixture = TestBed.createComponent(ReservationCreateDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    configure();
  });

  afterEach(() => vi.useRealTimers());

  // ---------------------------------------------------------------
  // Recherche Title
  // ---------------------------------------------------------------

  it('should not search titles when the input is empty', () => {
    createComponent();

    component.onTitleSearchInput('');
    vi.advanceTimersByTime(300);

    expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
  });

  it('should debounce the title search and call the PUBLIC CatalogueApiService.searchTitles with q/page/size', () => {
    createComponent();

    component.onTitleSearchInput('misérables');
    vi.advanceTimersByTime(299);
    expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(catalogueApiServiceMock.searchTitles).toHaveBeenCalledWith({ q: 'misérables', page: 0, size: 20 });
  });

  it('should never call the staff-only StaffCatalogueApiService.searchTitles (403 for a member)', () => {
    createComponent();

    component.onTitleSearchInput('misérables');
    vi.advanceTimersByTime(300);

    expect(staffCatalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
  });

  it('should display title results', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      of({ content: [buildTitle({ title: 'Notre-Dame de Paris' })], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    );
    createComponent();

    component.onTitleSearchInput('notre');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Notre-Dame de Paris');
  });

  it('should show "Aucun titre trouvé." when the search returns no result', () => {
    createComponent();

    component.onTitleSearchInput('inconnu');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun titre trouvé.');
  });

  it('should show an error state when the title search fails', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();

    component.onTitleSearchInput('x');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  // ---------------------------------------------------------------
  // Sélection
  // ---------------------------------------------------------------

  it('should select a title and keep its real numeric id', () => {
    createComponent();

    component.selectTitle(buildTitle({ id: 77 }));

    expect(component.selectedTitle()?.id).toBe(77);
  });

  it('should never render a raw numeric titleId input', () => {
    createComponent();

    const numberInputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
    expect(numberInputs.length).toBe(0);
  });

  it('should reset stale state whenever the dialog becomes visible again', () => {
    createComponent(false);
    component.selectTitle(buildTitle());
    expect(component.selectedTitle()).not.toBeNull();

    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();

    expect(component.selectedTitle()).toBeNull();
    expect(component.titleSearchTerm()).toBe('');
  });

  it('should emit closed when cancel is called', () => {
    createComponent();
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.cancel();

    expect(closedSpy).toHaveBeenCalledTimes(1);
  });

  // ---------------------------------------------------------------
  // Soumission
  // ---------------------------------------------------------------

  it('should keep submit disabled without a selected title', () => {
    createComponent();
    expect(component.canSubmit).toBe(false);

    component.selectTitle(buildTitle());
    expect(component.canSubmit).toBe(true);
  });

  it('should do nothing when submit is called without a selection', () => {
    createComponent();

    component.submit();

    expect(reservationApiServiceMock.createOwnReservation).not.toHaveBeenCalled();
  });

  it('should send exactly { titleId }, no userId, no other field', () => {
    reservationApiServiceMock.createOwnReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectTitle(buildTitle({ id: 30 }));

    component.submit();

    expect(reservationApiServiceMock.createOwnReservation).toHaveBeenCalledWith({ titleId: 30 });
    const [request] = reservationApiServiceMock.createOwnReservation.mock.calls[0];
    expect(Object.keys(request)).toEqual(['titleId']);
  });

  it('should never call the staff createReservation endpoint', () => {
    reservationApiServiceMock.createOwnReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectTitle(buildTitle());

    component.submit();

    expect(reservationApiServiceMock.createReservation).not.toHaveBeenCalled();
  });

  it('should prevent a double submit while the first request is pending', () => {
    reservationApiServiceMock.createOwnReservation.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();
    component.selectTitle(buildTitle());

    component.submit();
    component.submit();

    expect(reservationApiServiceMock.createOwnReservation).toHaveBeenCalledTimes(1);
  });

  it('should show a success toast on a successful createOwnReservation', () => {
    reservationApiServiceMock.createOwnReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectTitle(buildTitle());

    component.submit();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should emit saved with exactly the backend ReservationResponse — never a locally fabricated one', () => {
    const backendReservation = buildReservation({ id: 999 });
    reservationApiServiceMock.createOwnReservation.mockReturnValue(of(backendReservation));
    createComponent();
    component.selectTitle(buildTitle());
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(backendReservation);
  });

  it('should reset all selections after a successful submit', () => {
    reservationApiServiceMock.createOwnReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectTitle(buildTitle());

    component.submit();

    expect(component.selectedTitle()).toBeNull();
    expect(component.submitting()).toBe(false);
  });

  // ---------------------------------------------------------------
  // Erreurs métier — le backend reste l'autorité, rien n'est prédit
  // ---------------------------------------------------------------

  it('should treat RESERVATION_COPY_AVAILABLE as a plain error — no local Reservation, dialog stays open, no false success', () => {
    reservationApiServiceMock.createOwnReservation.mockReturnValue(
      throwError(() =>
        apiHttpError(409, 'RESERVATION_COPY_AVAILABLE', 'Un exemplaire de ce titre est immédiatement disponible.'),
      ),
    );
    createComponent();
    const title = buildTitle();
    component.selectTitle(title);
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).not.toHaveBeenCalled();
    expect(component.selectedTitle()).toEqual(title);
    expect(component.submitting()).toBe(false);
    expect(component.submitError()).toBe('Un exemplaire de ce titre est immédiatement disponible.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Un exemplaire de ce titre est immédiatement disponible.' }),
    );
    expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  const businessErrorCodes: Array<{ code: string; message: string }> = [
    { code: 'RESERVATION_ALREADY_ACTIVE', message: 'Cet adhérent a déjà une réservation active pour ce titre.' },
    { code: 'RESERVATION_LIMIT_REACHED', message: 'Cet adhérent a atteint le nombre maximal de réservations actives.' },
    { code: 'NOT_A_MEMBER', message: "Cet utilisateur n'est pas adhérent." },
    { code: 'MEMBER_BLOCKED', message: 'Cet adhérent est bloqué.' },
    { code: 'MEMBER_EXPIRED', message: "L'adhésion de cet adhérent est expirée." },
    { code: 'TITLE_NOT_FOUND', message: 'Aucun titre pour cet identifiant.' },
  ];

  for (const { code, message } of businessErrorCodes) {
    it(`should surface ${code} as a plain error, dialog remains open for a retry`, () => {
      reservationApiServiceMock.createOwnReservation.mockReturnValue(
        throwError(() => apiHttpError(409, code, message)),
      );
      createComponent();
      const title = buildTitle();
      component.selectTitle(title);

      component.submit();

      expect(component.selectedTitle()).toEqual(title);
      expect(component.submitting()).toBe(false);
      expect(messageServiceMock.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', detail: message }),
      );
      expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });
  }
});
