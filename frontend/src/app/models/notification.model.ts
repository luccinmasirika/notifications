export interface NotificationRequest {
  channel: 'SMS' | 'EMAIL';
  to: string;
  message: string;
}

export interface NotificationResponse {
  status: string;
  message: string;
  channel: string;
  recipient: string;
  timestamp: string;
  clientName?: string;
}

export interface RateLimitHeaders {
  limit?: number;
  remaining?: number;
  reset?: number;
  retryAfter?: number;
  softThrottled?: boolean;
}

export interface TestResponse {
  requestNumber: number;
  httpStatus: number;
  usagePercent?: number;
  remaining?: number;
  retryAfter?: number;
  timestamp: Date;
  rateLimitHeaders?: RateLimitHeaders;
}

