import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { vi, beforeEach } from 'vitest';
import { AdminService } from './admin.service';
import { AuthService } from './auth.service';
import { ConfigService } from './config.service';
import { Client, CreateClientRequest, UpdateClientRequest } from '../models/client.model';

beforeEach(() => {
  TestBed.resetTestingModule();
});

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;
  let authService: ReturnType<typeof vi.fn>;
  let configService: ReturnType<typeof vi.fn>;

  const mockApiUrl = 'http://localhost:8080/admin';
  const mockCredentials = { username: 'admin', password: 'admin123' };

  beforeEach(() => {
    TestBed.resetTestingModule();
    const authServiceSpy = {
      getCredentials: vi.fn()
    };
    const configServiceSpy = {
      getConfig: vi.fn()
    };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AdminService,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ConfigService, useValue: configServiceSpy }
      ]
    });

    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as any;
    configService = TestBed.inject(ConfigService) as any;

    authService.getCredentials.mockReturnValue(mockCredentials);
    configService.getConfig.mockReturnValue(of({ apiUrl: mockApiUrl, notificationApiUrl: '' }));
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('testAuth', () => {
    it('should make GET request to /clients with Basic auth', () => {
      const mockClients: Client[] = [];
      service.testAuth('user', 'pass').subscribe(response => {
        expect(response).toEqual(mockClients);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/clients`);
      expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toContain('Basic');
      req.flush(mockClients);
    });
  });

  describe('getClients', () => {
    it('should fetch clients with authentication headers', () => {
      const mockClients: Client[] = [{ id: 1, name: 'Test Client', apiKey: 'key123', active: true }];
      
      service.getClients().subscribe(clients => {
        expect(clients).toEqual(mockClients);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/clients`);
      expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toContain('Basic');
      req.flush(mockClients);
    });

    it('should throw error when not authenticated', () => {
      authService.getCredentials.mockReturnValue(null);

      expect(() => service.getClients().subscribe()).toThrow();
    });
  });

  describe('getClient', () => {
    it('should fetch a single client by id', () => {
      const mockClient: Client = { id: 1, name: 'Test Client', apiKey: 'key123', active: true };
      
      service.getClient(1).subscribe(client => {
        expect(client).toEqual(mockClient);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/clients/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockClient);
    });
  });

  describe('createClient', () => {
    it('should create a new client', () => {
      const createRequest: CreateClientRequest = { name: 'New Client', active: true };
      const mockClient: Client = { id: 1, name: 'New Client', apiKey: 'key123', active: true };
      
      service.createClient(createRequest).subscribe(client => {
        expect(client).toEqual(mockClient);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/clients`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(mockClient);
    });
  });

  describe('updateClient', () => {
    it('should update an existing client', () => {
      const updateRequest: UpdateClientRequest = { name: 'Updated Client' };
      const mockClient: Client = { id: 1, name: 'Updated Client', apiKey: 'key123', active: true };
      
      service.updateClient(1, updateRequest).subscribe(client => {
        expect(client).toEqual(mockClient);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/clients/1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(mockClient);
    });
  });

  describe('getSystemStatus', () => {
    it('should fetch system status', () => {
      const mockStatus = { status: 'ok', statistics: {} };
      
      service.getSystemStatus().subscribe(status => {
        expect(status).toEqual(mockStatus);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/status`);
      expect(req.request.method).toBe('GET');
      req.flush(mockStatus);
    });
  });

  describe('getSystemLimits', () => {
    it('should fetch system limits', () => {
      const mockLimits = [{ name: 'limit1', value: 100 }];
      
      service.getSystemLimits().subscribe(limits => {
        expect(limits).toEqual(mockLimits);
      });

      const req = httpMock.expectOne(`${mockApiUrl}/system-limits`);
      expect(req.request.method).toBe('GET');
      req.flush(mockLimits);
    });
  });
});

