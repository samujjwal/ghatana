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

import type { AuthTokens, AuthUser, LoginCredentials, RegisterData } from './auth.service';

// Java lifecycle service URL - configurable via env
const LIFECYCLE_SERVICE_URL = process.env.LIFECYCLE_SERVICE_URL ?? 'http://localhost:8082';

// Helper function to make HTTP requests to Java lifecycle service
async function makeRequest<T>(endpoint: string, options: RequestInit = {}): Promise<{ data: T }> {
  const url = `${LIFECYCLE_SERVICE_URL}${endpoint}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    signal: AbortSignal.timeout(10000),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const data = await response.json();
  return { data };
}

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
    const response = await makeRequest<{
      user: AuthUser;
      tokens: AuthTokens;
    }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email: credentials.email,
        password: credentials.password,
      }),
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
    const response = await makeRequest<AuthUser>('/api/auth/me', {
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
    const response = await makeRequest<AuthTokens>('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
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
    await makeRequest('/api/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
  }

  /**
   * Get current user via Java lifecycle service
   */
  async getCurrentUser(userId: string): Promise<AuthUser> {
    const apiKey = process.env.YAPPC_API_KEY;
    
    if (!apiKey || apiKey.trim() === '') {
      throw new Error(
        'YAPPC_API_KEY environment variable is required but not set. ' +
        'Please configure the API key for internal service communication.'
      );
    }

    const response = await makeRequest<AuthUser>(`/api/auth/users/${userId}`, {
      headers: {
        'X-API-Key': apiKey,
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
