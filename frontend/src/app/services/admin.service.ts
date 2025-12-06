import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Client, ClientLimit, CreateClientRequest, UpdateClientRequest, ClientLimitRequest, ClientDetailsResponse, ClientResponse, SystemLimit, SystemLimitRequest, ApiSecretResponse } from '../models/client.model';
import { AuthService } from './auth.service';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private apiUrl = '';

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private configService: ConfigService
  ) {
    this.configService.getConfig().subscribe(config => {
      this.apiUrl = config.apiUrl;
    });
  }

  private getHeaders(): HttpHeaders {
    const credentials = this.authService.getCredentials();
    if (!credentials) {
      throw new Error('Not authenticated');
    }
    const auth = btoa(`${credentials.username}:${credentials.password}`);
    return new HttpHeaders({
      'Authorization': `Basic ${auth}`,
      'Content-Type': 'application/json'
    });
  }

  testAuth(username: string, password: string): Observable<any> {
    const auth = btoa(`${username}:${password}`);
    const headers = new HttpHeaders({
      'Authorization': `Basic ${auth}`,
      'Content-Type': 'application/json'
    });
    return this.http.get<Client[]>(`${this.apiUrl}/clients`, { headers });
  }

  getClients(): Observable<Client[]> {
    return this.http.get<Client[]>(`${this.apiUrl}/clients`, { headers: this.getHeaders() });
  }

  getClient(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.apiUrl}/clients/${id}`, { headers: this.getHeaders() });
  }

  createClient(request: CreateClientRequest): Observable<ClientResponse> {
    return this.http.post<ClientResponse>(`${this.apiUrl}/clients`, request, { headers: this.getHeaders() });
  }

  updateClient(id: number, request: UpdateClientRequest): Observable<ClientResponse> {
    return this.http.put<ClientResponse>(`${this.apiUrl}/clients/${id}`, request, { headers: this.getHeaders() });
  }

  updateClientLimits(id: number, request: ClientLimitRequest): Observable<ClientLimit> {
    return this.http.put<ClientLimit>(`${this.apiUrl}/clients/${id}/limits`, request, { headers: this.getHeaders() });
  }

  getClientLimit(clientId: number): Observable<ClientLimit> {
    return this.http.get<ClientLimit>(`${this.apiUrl}/limits/client/${clientId}`, { headers: this.getHeaders() });
  }

  getClientDetails(clientId: number): Observable<ClientDetailsResponse> {
    return this.http.get<ClientDetailsResponse>(`${this.apiUrl}/clients/${clientId}/details`, { headers: this.getHeaders() });
  }

  getSystemStatus(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/status`, { headers: this.getHeaders() });
  }

  getSystemLimits(): Observable<SystemLimit[]> {
    return this.http.get<SystemLimit[]>(`${this.apiUrl}/system-limits`, { headers: this.getHeaders() });
  }

  createOrUpdateSystemLimit(limit: SystemLimitRequest | SystemLimit): Observable<SystemLimit> {
    return this.http.post<SystemLimit>(`${this.apiUrl}/system-limits`, limit, { headers: this.getHeaders() });
  }

  generateApiKey(): Observable<{apiKey: string, message: string, timestamp: string}> {
    return this.http.get<{apiKey: string, message: string, timestamp: string}>(
      `${this.apiUrl}/generate-api-key`,
      { headers: this.getHeaders() }
    );
  }

  generateApiSecret(clientId: number): Observable<ApiSecretResponse> {
    return this.http.post<ApiSecretResponse>(
      `${this.apiUrl}/clients/${clientId}/generate-secret`,
      {},
      { headers: this.getHeaders() }
    );
  }

  rotateApiSecret(clientId: number): Observable<ApiSecretResponse> {
    return this.http.post<ApiSecretResponse>(
      `${this.apiUrl}/clients/${clientId}/rotate-secret`,
      {},
      { headers: this.getHeaders() }
    );
  }


  updateClientStatus(clientId: number, status: 'ACTIVE' | 'SUSPENDED' | 'REVOKED'): Observable<Client> {
    return this.http.put<Client>(
      `${this.apiUrl}/clients/${clientId}/status`,
      { status },
      { headers: this.getHeaders() }
    );
  }
}
