/**
 * OAuth 2.0 Utilities
 * Helper functions for OAuth 2.0 authentication flow
 */

import type {
  OAuthProvider,
  OAuthToken,
  AuthorizationCodeResponse,
  TokenResponse,
  UserInfoResponse,
  OAuthUser,
} from './types';

/**
 * OAuth utilities class
 * Static methods for OAuth 2.0 operations
 */
export class OAuthUtils {
  /**
   * Generate authorization URL
   * @param provider - OAuth provider configuration
   * @param state - CSRF protection state parameter
   * @returns Authorization URL
   */
  static generateAuthorizationUrl(provider: OAuthProvider, state: string): string {
    const params = new URLSearchParams({
      client_id: provider.clientId,
      redirect_uri: provider.redirectUri,
      response_type: 'code',
      scope: provider.scopes.join(' '),
      state,
    });

    return `${provider.authorizationUrl}?${params.toString()}`;
  }

  /**
   * Generate CSRF state parameter
   * @returns Random state string
   */
  static generateState(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
  }

  /**
   * Parse authorization code response
   * @param url - Callback URL with authorization code
   * @returns Parsed authorization code response
   */
  static parseAuthorizationResponse(url: string): AuthorizationCodeResponse {
    const urlParams = new URLSearchParams(new URL(url).search);

    return {
      code: urlParams.get('code') || '',
      state: urlParams.get('state') || '',
      error: urlParams.get('error') || undefined,
      errorDescription: urlParams.get('error_description') || undefined,
    };
  }

  /**
   * Exchange authorization code for token
   * @param provider - OAuth provider configuration
   * @param code - Authorization code
   * @returns OAuth token
   */
  static async exchangeCodeForToken(provider: OAuthProvider, code: string): Promise<OAuthToken> {
    const params = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      client_id: provider.clientId,
      client_secret: provider.clientSecret || '',
      redirect_uri: provider.redirectUri,
    });

    const response = await fetch(provider.tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    if (!response.ok) {
      throw new Error(`Token exchange failed: ${response.statusText}`);
    }

    const data: TokenResponse = await response.json();

    return {
      accessToken: data.access_token,
      tokenType: data.token_type,
      expiresIn: data.expires_in,
      refreshToken: data.refresh_token,
      scope: data.scope,
      issuedAt: Date.now(),
    };
  }

  /**
   * Refresh access token
   * @param provider - OAuth provider configuration
   * @param refreshToken - Refresh token
   * @returns New OAuth token
   */
  static async refreshAccessToken(provider: OAuthProvider, refreshToken: string): Promise<OAuthToken> {
    const params = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: provider.clientId,
      client_secret: provider.clientSecret || '',
    });

    const response = await fetch(provider.tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    if (!response.ok) {
      throw new Error(`Token refresh failed: ${response.statusText}`);
    }

    const data: TokenResponse = await response.json();

    return {
      accessToken: data.access_token,
      tokenType: data.token_type,
      expiresIn: data.expires_in,
      refreshToken: data.refresh_token || refreshToken,
      scope: data.scope,
      issuedAt: Date.now(),
    };
  }

  /**
   * Fetch user info from provider
   * @param provider - OAuth provider configuration
   * @param token - OAuth token
   * @returns User info
   */
  static async fetchUserInfo(provider: OAuthProvider, token: OAuthToken): Promise<OAuthUser> {
    const response = await fetch(provider.userInfoUrl, {
      headers: {
        Authorization: `${token.tokenType} ${token.accessToken}`,
      },
    });

    if (!response.ok) {
      throw new Error(`User info fetch failed: ${response.statusText}`);
    }

    const data: UserInfoResponse = await response.json();

    return {
      id: data.id,
      email: data.email,
      name: data.name,
      avatar: data.picture,
      provider: provider.name,
      metadata: data,
    };
  }

  /**
   * Check if token is expired
   * @param token - OAuth token
   * @returns True if token is expired
   */
  static isTokenExpired(token: OAuthToken): boolean {
    const expirationTime = token.issuedAt + token.expiresIn * 1000;
    return Date.now() > expirationTime;
  }

  /**
   * Check if token is expiring soon (within 5 minutes)
   * @param token - OAuth token
   * @returns True if token is expiring soon
   */
  static isTokenExpiringSoon(token: OAuthToken): boolean {
    const expirationTime = token.issuedAt + token.expiresIn * 1000;
    const fiveMinutesMs = 5 * 60 * 1000;
    return Date.now() > expirationTime - fiveMinutesMs;
  }

  /**
   * Store token in localStorage
   * @param token - OAuth token
   * @param key - Storage key
   */
  static storeToken(token: OAuthToken, key: string = 'oauth_token'): void {
    try {
      localStorage.setItem(key, JSON.stringify(token));
    } catch (error) {
      console.warn('Failed to store OAuth token:', error);
    }
  }

  /**
   * Retrieve token from localStorage
   * @param key - Storage key
   * @returns OAuth token or null
   */
  static retrieveToken(key: string = 'oauth_token'): OAuthToken | null {
    try {
      const stored = localStorage.getItem(key);
      return stored ? JSON.parse(stored) : null;
    } catch (error) {
      console.warn('Failed to retrieve OAuth token:', error);
      return null;
    }
  }

  /**
   * Clear stored token
   * @param key - Storage key
   */
  static clearToken(key: string = 'oauth_token'): void {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.warn('Failed to clear OAuth token:', error);
    }
  }
}
