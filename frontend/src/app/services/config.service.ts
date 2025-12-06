import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, firstValueFrom } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';

export interface AppConfig {
  apiUrl: string;
  notificationApiUrl: string;
}

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private config$: Observable<AppConfig>;
  private config: AppConfig | null = null;

  constructor(private http: HttpClient) {
    this.config$ = this.loadConfig().pipe(
      shareReplay(1),
      catchError(() => {
        const defaultConfig: AppConfig = {
          apiUrl: 'http://localhost:8080/admin',
          notificationApiUrl: 'http://localhost:8080/api/notifications'
        };
        console.warn('Failed to load config.json, using default configuration');
        return of(defaultConfig);
      })
    );
  }

  private loadConfig(): Observable<AppConfig> {
    return this.http.get<AppConfig>('/assets/config.json').pipe(
      map(config => {
        this.config = config;
        return config;
      })
    );
  }

  getConfig(): Observable<AppConfig> {
    return this.config$;
  }

  getApiUrl(): string {
    return this.config?.apiUrl || 'http://localhost:8080/admin';
  }

  getNotificationApiUrl(): string {
    return this.config?.notificationApiUrl || 'http://localhost:8080/api/notifications';
  }

  async init(): Promise<AppConfig> {
    return await firstValueFrom(this.config$);
  }
}

