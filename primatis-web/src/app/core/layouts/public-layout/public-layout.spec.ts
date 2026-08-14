import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { PublicLayout } from './public-layout';

describe('PublicLayout', () => {
  let fixture: ComponentFixture<PublicLayout>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [PublicLayout],
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    });

    fixture = TestBed.createComponent(PublicLayout);
    fixture.detectChanges();
  });

  it('should reuse the shared Navigation component instead of a hardcoded header', () => {
    expect(fixture.nativeElement.querySelector('app-navigation')).not.toBeNull();
  });

  it('should render a router outlet for its children', () => {
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
