/**
 * Authentication API Client
 *
 * Production-ready auth service with TypeScript types,
 * error handling, and request/response interceptors.
 *
 * Uses @ghatana/api `ApiClient` as the HTTP substrate — auth token injection
 * and 401/403 handling are wired as middleware, not reimplemented here.
 *
 * @module api/auth
 * @doc.type service
 * @doc.purpose Backend authentication integration
 * @doc.layer api
 */

import { ApiClient, createCorrelationMiddleware } from '@ghatana/api';
import type { ApiRequest, ApiResponse as GhatanaApiResponse } from '@ghatana/api';

// ============================================================================
// Types
// ============================================================================

/**
 * User data from API
 */
export interface User {
  id: string;
  email: string;
  name: string;
  avatar?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Login request payload
 */
export interface LoginRequest {
  email: string;
  password: string;
  rememberMe?: boolean;
}

/**
 * Login response
 */
export interface LoginResponse {
  user: User;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/**
 * Register request payload
 */
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

/**
 * Register response
 */
export interface RegisterResponse {
  user: User;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/**
 * Refresh token request
 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/**
 * Refresh token response
 */
export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/**
 * Logout request
 */
export interface LogoutRequest {
  refreshToken?: string;
}

/**
 * Password reset request
 */
export interface PasswordResetRequest {
  email: string;
}

/**
 * Password reset confirm request
 */
export interface PasswordResetConfirmRequest {
  token: string;
  password: string;
}

/**
 * API error response
 */
export interface ApiError {
  message: string;
  code?: string;
  details?: Record<string, unknown>;
}

/**
 * Auth service configuration
 */
export interface AuthServiceConfig {
  baseUrl?: string;
  timeout?: number;
  retryAttempts?: number;
  onTokenExpired?: () => void;
  onUnauthorized?: () => void;
}

function resolveApiBaseUrl(configuredBaseUrl?: string): string {
  if (configuredBaseUrl) {
    return configuredBaseUrl;
  }

  const viteEnv =
    typeof import.meta !== 'undefined'
      ? (import.meta.env as Record<string, string | undefined> | undefined)
      : undefined;

  return viteEnv?.VITE_API_BASE_URL ?? '/api';
}

// ============================================================================
// Auth Service Class
// ============================================================================

/**
 * Authentication service
 * Handles all authentication-related API calls
 *
 * @example
 * const authService = new AuthService({
 *   baseUrl: 'https://api.example.com',
 *   onTokenExpired: () => navigate('/login'),
 * });
 *
 * const response = await authService.login({
 *   email: 'user@example.com',
 *   password: 'password123',
 * });
 */
export class AuthService {
  private readonly apiClient: ApiClient;
  private readonly onTokenExpired?: () => void;
  private readonly onUnauthorized?: () => void;

  /**
   * @doc.type constructor
   * @doc.purpose Wire ApiClient with auth middleware for token injection and
   *               YAPPC-specific 401/403 side-effects (callbacks).
   */
  constructor(config: AuthServiceConfig = {}) {
    const baseUrl = resolveApiBaseUrl(config.baseUrl);

    this.onTokenExpired = config.onTokenExpired;
    this.onUnauthorized = config.onUnauthorized;

    this.apiClient = new ApiClient({
      baseUrl,
      timeoutMs: config.timeout ?? 30000,
      retry: { attempts: config.retryAttempts ?? 1 },
    });

    this.apiClient.useRequest(createCorrelationMiddleware());

    // Inject Bearer token from localStorage on every request
    this.apiClient.useRequest((request: ApiRequest): ApiRequest => {
      const token =
        typeof localStorage !== 'undefined'
          ? localStorage.getItem('auth_token')
          : null;
      if (token) {
        return {
          ...request,
          headers: { ...request.headers, Authorization: `Bearer ${token}` },
        };
      }
      return request;
    });

    // Handle 401/403 side-effects via response middleware
    this.apiClient.useResponse((response: GhatanaApiResponse<unknown>, _request: ApiRequest): GhatanaApiResponse<unknown> => {
      if (response.status === 401) {
        this.onUnauthorized?.();
      } else if (response.status === 403) {
        this.onTokenExpired?.();
      }
      return response;
    });
  }

  /**
   * Internal helper: unwraps ApiResponse<T> to T.
   * ApiClient already handles status-code classification and retries.
   */
  private async request<T>(
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE',
    endpoint: string,
    body?: unknown,
  ): Promise<T> {
    const response = await this.apiClient.request<T>({ url: endpoint, method, body });
    return response.data;
  }

  /**
   * Login user
   */
  async login(payload: LoginRequest): Promise<LoginResponse> {
    return this.request<LoginResponse>('POST', '/auth/login', payload);
  }

  /**
   * Register new user
   */
  async register(payload: RegisterRequest): Promise<RegisterResponse> {
    return this.request<RegisterResponse>('POST', '/auth/register', payload);
  }

  /**
   * Logout user
   */
  async logout(payload: LogoutRequest = {}): Promise<void> {
    return this.request<void>('POST', '/auth/logout', payload);
  }

  /**
   * Refresh access token
   */
  async refreshToken(
    payload: RefreshTokenRequest
  ): Promise<RefreshTokenResponse> {
    return this.request<RefreshTokenResponse>('POST', '/auth/refresh', payload);
  }

  /**
   * Get current user profile
   */
  async me(): Promise<User> {
    return this.request<User>('GET', '/auth/me');
  }

  /**
   * Request password reset
   */
  async requestPasswordReset(payload: PasswordResetRequest): Promise<void> {
    return this.request<void>('POST', '/auth/reset-password', payload);
  }

  /**
   * Confirm password reset
   */
  async confirmPasswordReset(
    payload: PasswordResetConfirmRequest
  ): Promise<void> {
    return this.request<void>('POST', '/auth/reset-password/confirm', payload);
  }

  /**
   * Update user profile
   */
  async updateProfile(payload: Partial<User>): Promise<User> {
    return this.request<User>('PATCH', '/auth/profile', payload);
  }

  /**
   * Change password
   */
  async changePassword(payload: {
    currentPassword: string;
    newPassword: string;
  }): Promise<void> {
    return this.request<void>('POST', '/auth/change-password', payload);
  }
}

// ============================================================================
// Default Export
// ============================================================================

/**
 * Default auth service instance
 */
export const authService = new AuthService();
