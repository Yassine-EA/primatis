import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { AdminLayout } from './admin-layout';

describe('AdminLayout', () => {
  let fixture: ComponentFixture<AdminLayout>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(true),
      roles: vi.fn().mockReturnValue(['ROLE_ADMIN']),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [AdminLayout],
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    });

    fixture = TestBed.createComponent(AdminLayout);
    fixture.detectChanges();
  });

  it('should reuse the shared Navigation component instead of a hardcoded header', () => {
    expect(fixture.nativeElement.querySelector('app-navigation')).not.toBeNull();
  });

  it('should render a router outlet for its children', () => {
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
