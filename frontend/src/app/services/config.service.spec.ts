import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { ConfigService, AppConfig } from './config.service';

beforeEach(() => {
  TestBed.resetTestingModule();
});

describe('ConfigService', () => {
  let service: ConfigService;
  let httpMock: HttpTestingController;

  const mockConfig: AppConfig = {
    apiUrl: 'http://localhost:8080/admin',
    notificationApiUrl: 'http://localhost:8080/api/notifications'
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ConfigService]
    });
    httpMock = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ConfigService);
    // The service constructor makes an HTTP call, so we need to flush it
    const req = httpMock.expectOne('/assets/config.json');
    req.flush(mockConfig);
  });

  afterEach(() => {
    if (httpMock) {
      httpMock.verify();
    }
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadConfig', () => {
    it('should load config from assets/config.json', (done) => {
      service.getConfig().subscribe(config => {
        expect(config).toEqual(mockConfig);
        done();
      });

      const req = httpMock.expectOne('/assets/config.json');
      expect(req.request.method).toBe('GET');
      req.flush(mockConfig);
    });

    it('should use default config when config.json fails to load', (done) => {
      service.getConfig().subscribe(config => {
        expect(config).toEqual({
          apiUrl: 'http://localhost:8080/admin',
          notificationApiUrl: 'http://localhost:8080/api/notifications'
        });
        done();
      });

      const req = httpMock.expectOne('/assets/config.json');
      req.error(new ErrorEvent('Network error'));
    });

    it('should cache config after first load', (done) => {
      service.getConfig().subscribe(() => {
        service.getConfig().subscribe(config => {
          expect(config).toEqual(mockConfig);
          done();
        });
      });

      const req = httpMock.expectOne('/assets/config.json');
      req.flush(mockConfig);
    });
  });

  describe('getApiUrl', () => {
    it('should return default apiUrl when config not loaded', () => {
      expect(service.getApiUrl()).toBe('http://localhost:8080/admin');
    });

    it('should return apiUrl from config after load', (done) => {
      service.getConfig().subscribe(() => {
        expect(service.getApiUrl()).toBe(mockConfig.apiUrl);
        done();
      });

      const req = httpMock.expectOne('/assets/config.json');
      req.flush(mockConfig);
    });
  });

  describe('getNotificationApiUrl', () => {
    it('should return default notificationApiUrl when config not loaded', () => {
      expect(service.getNotificationApiUrl()).toBe('http://localhost:8080/api/notifications');
    });

    it('should return notificationApiUrl from config after load', (done) => {
      service.getConfig().subscribe(() => {
        expect(service.getNotificationApiUrl()).toBe(mockConfig.notificationApiUrl);
        done();
      });

      const req = httpMock.expectOne('/assets/config.json');
      req.flush(mockConfig);
    });
  });

  describe('init', () => {
    it('should initialize and return config', async () => {
      const initPromise = service.init();

      const req = httpMock.expectOne('/assets/config.json');
      req.flush(mockConfig);

      const config = await initPromise;
      expect(config).toEqual(mockConfig);
    });
  });
});

