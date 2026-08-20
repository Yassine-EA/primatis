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
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';
import { StaffReservationCreateDialog } from './staff-reservation-create-dialog';

function buildUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 7,
    email: 'marie.curie@primatis.test',
    firstName: 'Marie',
    lastName: 'Curie',
    phoneNumber: null,
    accountStatus: 'ACTIVE',
    memberNumber: 'M000000001',
    memberStatus: 'ACTIVE',
    registrationDate: '2026-01-01',
    memberExpirationDate: '2027-01-01',
    blockedReason: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

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
      path: '/api/v1/reservations',
      fieldErrors: [],
    },
  });
}

describe('StaffReservationCreateDialog', () => {
  let fixture: ComponentFixture<StaffReservationCreateDialog>;
  let component: StaffReservationCreateDialog;
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };
  let staffCatalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let catalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let reservationApiServiceMock: {
    createOwnReservation: ReturnType<typeof vi.fn>;
    createReservation: ReturnType<typeof vi.fn>;
  };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    userApiServiceMock = {
      listUsers: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    };
    staffCatalogueApiServiceMock = {
      searchTitles: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    };
    // Jamais utilisé par ce dialog staff (endpoint public, sans intérêt
    // ici) — présent uniquement pour prouver qu'il n'est jamais appelé
    // (§3 : le choix StaffCatalogueApiService est délibéré et vérifié).
    catalogueApiServiceMock = { searchTitles: vi.fn() };
    reservationApiServiceMock = { createOwnReservation: vi.fn(), createReservation: vi.fn() };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffReservationCreateDialog],
      providers: [
        { provide: UserApiService, useValue: userApiServiceMock },
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        { provide: ReservationApiService, useValue: reservationApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(visible = true): void {
    fixture = TestBed.createComponent(StaffReservationCreateDialog);
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
  // Membre
  // ---------------------------------------------------------------

  it('should not search members when the input is empty', () => {
    createComponent();

    component.onMemberSearchInput('');
    vi.advanceTimersByTime(300);

    expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();
  });

  it('should debounce the member search and call listUsers(0, 20, q)', () => {
    createComponent();

    component.onMemberSearchInput('martin');
    vi.advanceTimersByTime(299);
    expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, 'martin');
  });

  it('should display member identity and memberNumber in search results', () => {
    userApiServiceMock.listUsers.mockReturnValue(
      of({
        content: [buildUser({ firstName: 'Ada', lastName: 'Lovelace', memberNumber: 'M000000042' })],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    createComponent();

    component.onMemberSearchInput('ada');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('M000000042');
  });

  it('should show "Aucun utilisateur trouvé." when the member search returns no result', () => {
    createComponent();

    component.onMemberSearchInput('inconnu');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun utilisateur trouvé.');
  });

  it('should show an error state when the member search fails', () => {
    userApiServiceMock.listUsers.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();

    component.onMemberSearchInput('x');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should mark a result without memberNumber as "Non adhérent" and refuse to select it', () => {
    const nonMember = buildUser({ id: 55, memberNumber: null, firstName: 'Non', lastName: 'Adherent' });
    userApiServiceMock.listUsers.mockReturnValue(
      of({ content: [nonMember], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    );
    createComponent();

    component.onMemberSearchInput('non');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Non adhérent');
    component.selectMember(nonMember);
    expect(component.selectedMember()).toBeNull();
  });

  it('should select a member and keep its real numeric id', () => {
    createComponent();

    component.selectMember(buildUser({ id: 42, memberNumber: 'M000000042' }));

    expect(component.selectedMember()?.id).toBe(42);
  });

  it('should reset the member search state on changeMember', () => {
    createComponent();
    component.selectMember(buildUser());

    component.changeMember();

    expect(component.selectedMember()).toBeNull();
    expect(component.memberSearchTerm()).toBe('');
  });

  // ---------------------------------------------------------------
  // Titre
  // ---------------------------------------------------------------

  it('should not search titles when the input is empty', () => {
    createComponent();

    component.onTitleSearchInput('');
    vi.advanceTimersByTime(300);

    expect(staffCatalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
  });

  it('should debounce the title search and call the STAFF StaffCatalogueApiService.searchTitles with q/page/size', () => {
    createComponent();

    component.onTitleSearchInput('misérables');
    vi.advanceTimersByTime(299);
    expect(staffCatalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(staffCatalogueApiServiceMock.searchTitles).toHaveBeenCalledWith({ q: 'misérables', page: 0, size: 20 });
  });

  it('should never call the public CatalogueApiService.searchTitles (staff context, RESERVATION_MANAGE implies CATALOGUE_MANAGE)', () => {
    createComponent();

    component.onTitleSearchInput('misérables');
    vi.advanceTimersByTime(300);

    expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
  });

  it('should display title results', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(
      of({ content: [buildTitle({ title: 'Notre-Dame de Paris' })], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    );
    createComponent();

    component.onTitleSearchInput('notre');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Notre-Dame de Paris');
  });

  it('should show "Aucun titre trouvé." when the title search returns no result', () => {
    createComponent();

    component.onTitleSearchInput('inconnu');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun titre trouvé.');
  });

  it('should show an error state when the title search fails', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();

    component.onTitleSearchInput('x');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should select a title and keep its real numeric id', () => {
    createComponent();

    component.selectTitle(buildTitle({ id: 77 }));

    expect(component.selectedTitle()?.id).toBe(77);
  });

  it('should reset the title search state on changeTitle', () => {
    createComponent();
    component.selectTitle(buildTitle());

    component.changeTitle();

    expect(component.selectedTitle()).toBeNull();
    expect(component.titleSearchTerm()).toBe('');
  });

  it('should never render a raw numeric userId/titleId input', () => {
    createComponent();

    const numberInputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
    expect(numberInputs.length).toBe(0);
  });

  it('should reset stale state whenever the dialog becomes visible again', () => {
    createComponent(false);
    component.selectMember(buildUser());
    component.selectTitle(buildTitle());
    expect(component.selectedMember()).not.toBeNull();

    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();

    expect(component.selectedMember()).toBeNull();
    expect(component.selectedTitle()).toBeNull();
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

  it('should keep submit disabled until both a member and a title are selected', () => {
    createComponent();
    expect(component.canSubmit).toBe(false);

    component.selectMember(buildUser());
    expect(component.canSubmit).toBe(false);

    component.selectTitle(buildTitle());
    expect(component.canSubmit).toBe(true);
  });

  it('should do nothing when submit is called without both selections', () => {
    createComponent();

    component.submit();

    expect(reservationApiServiceMock.createReservation).not.toHaveBeenCalled();
  });

  it('should send exactly { userId, titleId }, no other field', () => {
    reservationApiServiceMock.createReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectMember(buildUser({ id: 7 }));
    component.selectTitle(buildTitle({ id: 30 }));

    component.submit();

    expect(reservationApiServiceMock.createReservation).toHaveBeenCalledWith({ userId: 7, titleId: 30 });
    const [request] = reservationApiServiceMock.createReservation.mock.calls[0];
    expect(Object.keys(request)).toEqual(['userId', 'titleId']);
  });

  it('should never call the self-service createOwnReservation endpoint', () => {
    reservationApiServiceMock.createReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectMember(buildUser());
    component.selectTitle(buildTitle());

    component.submit();

    expect(reservationApiServiceMock.createOwnReservation).not.toHaveBeenCalled();
  });

  it('should prevent a double submit while the first request is pending', () => {
    reservationApiServiceMock.createReservation.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();
    component.selectMember(buildUser());
    component.selectTitle(buildTitle());

    component.submit();
    component.submit();

    expect(reservationApiServiceMock.createReservation).toHaveBeenCalledTimes(1);
  });

  it('should show a success toast on a successful createReservation', () => {
    reservationApiServiceMock.createReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectMember(buildUser());
    component.selectTitle(buildTitle());

    component.submit();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should emit saved with exactly the backend ReservationResponse — never a locally fabricated one', () => {
    const backendReservation = buildReservation({ id: 999 });
    reservationApiServiceMock.createReservation.mockReturnValue(of(backendReservation));
    createComponent();
    component.selectMember(buildUser());
    component.selectTitle(buildTitle());
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(backendReservation);
  });

  it('should reset all selections after a successful submit', () => {
    reservationApiServiceMock.createReservation.mockReturnValue(of(buildReservation()));
    createComponent();
    component.selectMember(buildUser());
    component.selectTitle(buildTitle());

    component.submit();

    expect(component.selectedMember()).toBeNull();
    expect(component.selectedTitle()).toBeNull();
    expect(component.submitting()).toBe(false);
  });

  // ---------------------------------------------------------------
  // Erreurs métier — le backend reste l'autorité, rien n'est prédit
  // ---------------------------------------------------------------

  it('should treat RESERVATION_COPY_AVAILABLE as a plain error — no local Reservation, dialog stays open, no false success', () => {
    reservationApiServiceMock.createReservation.mockReturnValue(
      throwError(() =>
        apiHttpError(409, 'RESERVATION_COPY_AVAILABLE', 'Un exemplaire de ce titre est immédiatement disponible.'),
      ),
    );
    createComponent();
    const member = buildUser();
    const title = buildTitle();
    component.selectMember(member);
    component.selectTitle(title);
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).not.toHaveBeenCalled();
    expect(component.selectedMember()).toEqual(member);
    expect(component.selectedTitle()).toEqual(title);
    expect(component.submitting()).toBe(false);
    expect(component.submitError()).toBe('Un exemplaire de ce titre est immédiatement disponible.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Un exemplaire de ce titre est immédiatement disponible.' }),
    );
    expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  const businessErrorCodes: Array<{ code: string; message: string }> = [
    { code: 'USER_NOT_FOUND', message: 'Utilisateur introuvable pour cet identifiant.' },
    { code: 'TITLE_NOT_FOUND', message: 'Aucun titre pour cet identifiant.' },
    { code: 'NOT_A_MEMBER', message: "Cet utilisateur n'est pas adhérent." },
    { code: 'MEMBER_BLOCKED', message: 'Cet adhérent est bloqué.' },
    { code: 'MEMBER_EXPIRED', message: "L'adhésion de cet adhérent est expirée." },
    { code: 'RESERVATION_ALREADY_ACTIVE', message: 'Cet adhérent a déjà une réservation active pour ce titre.' },
    { code: 'RESERVATION_LIMIT_REACHED', message: 'Cet adhérent a atteint le nombre maximal de réservations actives.' },
  ];

  for (const { code, message } of businessErrorCodes) {
    it(`should surface ${code} as a plain error, dialog remains open for a retry`, () => {
      reservationApiServiceMock.createReservation.mockReturnValue(
        throwError(() => apiHttpError(409, code, message)),
      );
      createComponent();
      const member = buildUser();
      const title = buildTitle();
      component.selectMember(member);
      component.selectTitle(title);

      component.submit();

      expect(component.selectedMember()).toEqual(member);
      expect(component.selectedTitle()).toEqual(title);
      expect(component.submitting()).toBe(false);
      expect(messageServiceMock.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', detail: message }),
      );
      expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });
  }
});
