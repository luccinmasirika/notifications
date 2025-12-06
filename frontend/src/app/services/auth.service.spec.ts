import { TestBed } from '@angular/core/testing';
import { beforeEach } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthService);
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    it('should store credentials and set authenticated to true', () => {
      const username = 'testuser';
      const password = 'testpass';

      const result = service.login(username, password);

      expect(result).toBe(true);
      expect(service.isLoggedIn()).toBe(true);
      expect(service.getCredentials()).toEqual({ username, password });
    });

    it('should emit authenticated state change', async () => {
      return new Promise<void>((resolve) => {
        service.isAuthenticated$.subscribe(isAuth => {
          if (isAuth) {
            expect(isAuth).toBe(true);
            resolve();
          }
        });

        service.login('user', 'pass');
      });
    });
  });

  describe('logout', () => {
    it('should remove credentials and set authenticated to false', () => {
      service.login('user', 'pass');
      expect(service.isLoggedIn()).toBe(true);

      service.logout();

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getCredentials()).toBeNull();
    });

    it('should emit authenticated state change to false', async () => {
      service.login('user', 'pass');
      
      return new Promise<void>((resolve) => {
        service.isAuthenticated$.subscribe(isAuth => {
          if (!isAuth) {
            expect(isAuth).toBe(false);
            resolve();
          }
        });

        service.logout();
      });
    });
  });

  describe('isLoggedIn', () => {
    it('should return false when not logged in', () => {
      expect(service.isLoggedIn()).toBe(false);
    });

    it('should return true when logged in', () => {
      service.login('user', 'pass');
      expect(service.isLoggedIn()).toBe(true);
    });
  });

  describe('getCredentials', () => {
    it('should return null when not logged in', () => {
      expect(service.getCredentials()).toBeNull();
    });

    it('should return credentials when logged in', () => {
      const username = 'testuser';
      const password = 'testpass';
      service.login(username, password);

      const credentials = service.getCredentials();
      expect(credentials).toEqual({ username, password });
    });
  });

  describe('getUsername', () => {
    it('should return null when not logged in', () => {
      expect(service.getUsername()).toBeNull();
    });

    it('should return username when logged in', () => {
      const username = 'testuser';
      service.login(username, 'pass');

      expect(service.getUsername()).toBe(username);
    });
  });

  describe('getPassword', () => {
    it('should return null when not logged in', () => {
      expect(service.getPassword()).toBeNull();
    });

    it('should return password when logged in', () => {
      const password = 'testpass';
      service.login('user', password);

      expect(service.getPassword()).toBe(password);
    });
  });
});

