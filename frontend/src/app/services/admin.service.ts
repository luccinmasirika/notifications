import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Client, ClientLimit, CreateClientRequest, UpdateClientRequest, ClientLimitRequest, ClientDetailsResponse } from '../models/client.model';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private apiUrl = 'http://localhost:8080/admin';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

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
    // Test authentication by trying to get clients
    return this.http.get<Client[]>(`${this.apiUrl}/clients`, { headers });
  }

  getClients(): Observable<Client[]> {
    return this.http.get<Client[]>(`${this.apiUrl}/clients`, { headers: this.getHeaders() });
  }

  getClient(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.apiUrl}/clients/${id}`, { headers: this.getHeaders() });
  }

  createClient(request: CreateClientRequest): Observable<Client> {
    return this.http.post<Client>(`${this.apiUrl}/clients`, request, { headers: this.getHeaders() });
  }

  updateClient(id: number, request: UpdateClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.apiUrl}/clients/${id}`, request, { headers: this.getHeaders() });
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
}

