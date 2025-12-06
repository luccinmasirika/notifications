import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NotificationRequest, NotificationResponse, RateLimitHeaders } from '../models/notification.model';
import { ConfigService } from './config.service';
import { HmacService } from './hmac.service';

export interface TestResponseData {
  requestNumber: number;
  httpStatus: number;
  usagePercent?: number;
  limit?: number;
  remaining?: number;
  retryAfter?: number;
  isSoftThrottled?: boolean;
  timestamp: Date;
  rateLimitHeaders?: RateLimitHeaders;
}

@Injectable({
  providedIn: 'root'
})
export class ClientTesterService {
  private apiUrl = '';

  constructor(
    private http: HttpClient,
    private configService: ConfigService,
    private hmacService: HmacService
  ) {
    this.configService.getConfig().subscribe(config => {
      this.apiUrl = config.notificationApiUrl;
    });
  }

  async sendNotification(
    apiKey: string,
    apiSecret: string,
    request: NotificationRequest
  ): Promise<Observable<HttpResponse<NotificationResponse>>> {
    const timestamp = this.hmacService.getCurrentTimestamp();
    const method = 'POST';
    const path = '/api/notifications';
    const body = JSON.stringify(request);

    const signature = await this.hmacService.generateSignatureAsync(
      apiSecret,
      timestamp,
      method,
      path,
      body
    );

    const headers = new HttpHeaders({
      'X-API-KEY': apiKey,
      'X-TIMESTAMP': timestamp.toString(),
      'X-SIGNATURE': signature,
      'Content-Type': 'application/json'
    });

    return this.http.post<NotificationResponse>(
      this.apiUrl,
      request,
      {
        headers,
        observe: 'response'
      }
    );
  }

  extractRateLimitHeaders(response: HttpResponse<any> | HttpErrorResponse): RateLimitHeaders {
    const headers = response.headers;
    const rateLimitHeaders: RateLimitHeaders = {};
    const getHeader = (name: string): string | null => {
      let value = headers.get(name);
      if (value !== null) return value;
      const allKeys = headers.keys();
      const lowerName = name.toLowerCase();
      for (const key of allKeys) {
        if (key.toLowerCase() === lowerName) {
          return headers.get(key);
        }
      }
      return null;
    };

    const limit = getHeader('X-RateLimit-Limit');
    const remaining = getHeader('X-RateLimit-Remaining');
    const reset = getHeader('X-RateLimit-Reset');
    const retryAfter = getHeader('Retry-After');
    const softThrottled = getHeader('X-Soft-Throttled');

    if (limit) {
      rateLimitHeaders.limit = parseInt(limit, 10);
    }
    if (remaining !== null && remaining !== undefined && remaining !== '') {
      rateLimitHeaders.remaining = parseInt(remaining, 10);
    }
    if (reset) {
      rateLimitHeaders.reset = parseInt(reset, 10);
    }
    if (retryAfter) {
      rateLimitHeaders.retryAfter = parseInt(retryAfter, 10);
    }
    if (softThrottled !== null && softThrottled !== undefined && softThrottled !== '') {
      rateLimitHeaders.softThrottled = softThrottled.toLowerCase() === 'true';
    }

    if (response instanceof HttpErrorResponse && response.status === 429 && response.error) {
      const errorBody = response.error;
      if (typeof errorBody === 'object') {
        if (errorBody.limit !== undefined) rateLimitHeaders.limit = errorBody.limit;
        if (errorBody.remaining !== undefined) rateLimitHeaders.remaining = errorBody.remaining;
        if (errorBody.reset !== undefined) rateLimitHeaders.reset = errorBody.reset;
        if (errorBody.retryAfter !== undefined) rateLimitHeaders.retryAfter = errorBody.retryAfter;
      }
    }

    return rateLimitHeaders;
  }

  calculateUsagePercent(headers: RateLimitHeaders, errorBody?: any): number | undefined {
    if (errorBody && errorBody.message) {
      const usageMatch = errorBody.message.match(/Usage:\s*([\d.]+)%/);
      if (usageMatch) {
        return Math.round(parseFloat(usageMatch[1]));
      }
    }
    if (headers.limit && headers.remaining !== undefined) {
      return Math.round((1 - headers.remaining / headers.limit) * 100);
    }
    return undefined;
  }
}
