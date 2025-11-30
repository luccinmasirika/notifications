import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi, beforeEach } from 'vitest';
import { AuthGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

// Ensure TestBed is reset before each test
beforeEach(() => {
  TestBed.resetTestingModule();
});

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let authService: ReturnType<typeof vi.fn>;
  let router: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    const authServiceSpy = {
      isLoggedIn: vi.fn()
    };
    const routerSpy = {
      navigate: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        AuthGuard,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    guard = TestBed.inject(AuthGuard);
    authService = TestBed.inject(AuthService) as any;
    router = TestBed.inject(Router) as any;
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should allow activation when user is logged in', () => {
    authService.isLoggedIn.mockReturnValue(true);

    const result = guard.canActivate();

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should deny activation and redirect to login when user is not logged in', () => {
    authService.isLoggedIn.mockReturnValue(false);

    const result = guard.canActivate();

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});

