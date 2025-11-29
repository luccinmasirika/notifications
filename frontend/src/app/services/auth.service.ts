import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly STORAGE_KEY = 'admin_credentials';
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.isLoggedIn());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor() {}

  login(username: string, password: string): boolean {
    // Store credentials in sessionStorage
    const credentials = { username, password };
    sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(credentials));
    this.isAuthenticatedSubject.next(true);
    return true;
  }

  logout(): void {
    sessionStorage.removeItem(this.STORAGE_KEY);
    this.isAuthenticatedSubject.next(false);
  }

  isLoggedIn(): boolean {
    return !!sessionStorage.getItem(this.STORAGE_KEY);
  }

  getCredentials(): { username: string; password: string } | null {
    const stored = sessionStorage.getItem(this.STORAGE_KEY);
    if (stored) {
      return JSON.parse(stored);
    }
    return null;
  }

  getUsername(): string | null {
    const credentials = this.getCredentials();
    return credentials?.username || null;
  }

  getPassword(): string | null {
    const credentials = this.getCredentials();
    return credentials?.password || null;
  }
}

