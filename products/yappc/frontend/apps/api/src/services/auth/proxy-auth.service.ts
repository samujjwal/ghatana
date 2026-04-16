/**
 * Copyright (c) 2025 Ghatana Technologies
 * Proxy Authentication Service
 *
 * Proxies all authentication requests to the canonical Java lifecycle service.
 * This eliminates duplicate auth logic and ensures single source of truth.
 *
 * @doc.type service
 * @doc.purpose Proxy auth to Java lifecycle service
 * @doc.layer product
 * @doc.pattern Proxy/Adapter
 */

import { ApiClient } from '@ghatana/api';
import type { AuthTokens, AuthUser, LoginCredentials, RegisterData } from './auth.service';

// Java lifecycle service URL - configurable via env
const LIFECYCLE_SERVICE_URL = process.env.LIFECYCLE_SERVICE_URL ?? 'http://localhost:8082';

// Reuse shared platform API client
const apiClient = new ApiClient({
  baseUrl: LIFECYCLE_SERVICE_URL,
  timeoutMs: 10000,
  defaultHeaders: {
    'Content-Type': 'application/json',
  },
});

/**
 * Proxy Auth Service
 *
 * Delegates all auth operations to the Java lifecycle service.
 * The Java service is the canonical auth authority.
 */
export class ProxyAuthService {
  /**
   * Authenticate user credentials via Java lifecycle service
   */
  async login(credentials: LoginCredentials): Promise<{ user: AuthUser; tokens: AuthTokens }> {
    const response = await apiClient.post<{
      user: AuthUser;
      tokens: AuthTokens;
    }>('/api/auth/login', {
      body: {
        email: credentials.email,
        password: credentials.password,
      },
    });

    if (!response.data) {
      throw new Error('Authentication failed: no response from lifecycle service');
    }

    return response.data;
  }

  /**
   * Validate access token via Java lifecycle service
   */
  async validateAccessToken(token: string): Promise<AuthUser> {
    const response = await apiClient.get<AuthUser>('/api/auth/me', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.data) {
      throw new Error('Token validation failed: no response from lifecycle service');
    }

    return response.data;
  }

  /**
   * Refresh tokens via Java lifecycle service
   */
  async refreshTokens(refreshToken: string): Promise<AuthTokens> {
    const response = await apiClient.post<AuthTokens>('/api/auth/refresh', {
      body: { refreshToken },
    });

    if (!response.data) {
      throw new Error('Token refresh failed: no response from lifecycle service');
    }

    return response.data;
  }

  /**
   * Logout via Java lifecycle service
   */
  async logout(refreshToken: string): Promise<void> {
    await apiClient.post('/api/auth/logout', {
      body: { refreshToken },
    });
  }

  /**
   * Get current user via Java lifecycle service
   */
  async getCurrentUser(userId: string): Promise<AuthUser> {
    const response = await apiClient.get<AuthUser>(`/api/auth/users/${userId}`, {
      headers: {
        'X-API-Key': process.env.YAPPC_API_KEY ?? '',
      },
    });

    if (!response.data) {
      throw new Error('User lookup failed: no response from lifecycle service');
    }

    return response.data;
  }
}

// Export singleton instance
export const proxyAuthService = new ProxyAuthService();
