export interface Client {
  id?: number;
  apiKey: string;
  name: string;
  priority: number;
  active: boolean;
  createdAt?: string;
}

export interface ClientLimit {
  id?: number;
  clientId: number;
  windowSizeSeconds: number;
  maxRequestsPerWindow: number;
  monthlyQuota: number;
  softThrottleThreshold?: number;
  hardRejectThreshold?: number;
  createdAt?: string;
}

export interface CreateClientRequest {
  apiKey: string;
  name: string;
  priority?: number;
  active?: boolean;
}

export interface UpdateClientRequest {
  apiKey?: string;
  name?: string;
  priority?: number;
  active?: boolean;
}

export interface ClientLimitRequest {
  windowSizeSeconds: number;
  maxRequestsPerWindow: number;
  monthlyQuota: number;
  softThrottleThreshold?: number;
  hardRejectThreshold?: number;
}

export interface WindowUsage {
  currentCount: number;
  maxRequests: number;
  windowSizeSeconds: number;
  usagePercent: number;
  remaining: number;
  resetTime: string;
  isSoftThrottled: boolean;
  isBlocked: boolean;
}

export interface MonthlyUsage {
  currentCount: number;
  monthlyQuota: number;
  usagePercent: number;
  remaining: number;
  isSoftThrottled: boolean;
  isBlocked: boolean;
}

export interface ClientStatus {
  isActive: boolean;
  isBlocked: boolean;
  isSoftThrottled: boolean;
  statusMessage: string;
  nextReset: string | null;
}

export interface ClientDetailsResponse {
  client: Client;
  limit: ClientLimit | null;
  windowUsage: WindowUsage | null;
  monthlyUsage: MonthlyUsage | null;
  status: ClientStatus;
}

export interface SystemLimit {
  id?: number;
  name: string;
  windowSizeSeconds: number;
  maxRequestsPerWindow: number;
  active: boolean;
  createdAt?: string;
}

export interface SystemLimitRequest {
  name: string;
  windowSizeSeconds: number;
  maxRequestsPerWindow: number;
  active?: boolean;
}

