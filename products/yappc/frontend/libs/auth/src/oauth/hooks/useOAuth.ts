/**
 * OAuth React Hook
 * 
 * React hook for OAuth 2.0 authentication flow.
 * Handles authorization, token exchange, and user info fetching.
 * 
 * @module auth/oauth/hooks
 */

import { useState, useCallback, useEffect } from 'react';
import type { OAuthProvider, OAuthToken, OAuthUser, AuthState } from '../types';
import { OAuthUtils } from '../utils';

export interface UseOAuthConfig {
  /** OAuth provider configuration */
  provider: OAuthProvider;
  
  /** Callback when authentication succeeds */
  onSuccess?: (user: OAuthUser, token: OAuthToken) => void;
  
  /** Callback when authentication fails */
  onError?: (error: Error) => void;
  
  /** Auto-refresh token when expiring */
  autoRefresh?: boolean;
  
  /** Storage key for token persistence */
  storageKey?: string;
}

/**
 * OAuth Authentication Hook
 * 
 * Provides OAuth 2.0 authentication functionality.
 * 
 * Features:
 * - Authorization URL generation
 * - Token exchange
 * - User info fetching
 * - Token refresh
 * - Token persistence
 * - Auto-refresh on expiration
 * 
 * @example
 * ```tsx
 * const oauth = useOAuth({
 *   provider: OAuthProviders.google(),
 *   onSuccess: (user, token) => {
 *     console.log('Logged in:', user);
 *   },
 *   onError: (error) => {
 *     console.error('Login failed:', error);
 *   },
 *   autoRefresh: true,
 * });
 * 
 * // Initiate login
 * <button onClick={oauth.login}>Login with Google</button>
 * 
 * // Handle callback
 * useEffect(() => {
 *   oauth.handleCallback(window.location.href);
 * }, []);
 * 
 * // Logout
 * <button onClick={oauth.logout}>Logout</button>
 * ```
 */
export function useOAuth(config: UseOAuthConfig) {
  const {
    provider,
    onSuccess,
    onError,
    autoRefresh = true,
    storageKey = `oauth_token_${provider.name}`,
  } = config;

  const [authState, setAuthState] = useState<AuthState>({
    isAuthenticated: false,
    user: null,
    token: null,
    isLoading: true,
    error: null,
  });

  /**
   * Initialize authentication state from storage
   */
  useEffect(() => {
    const initAuth = async () => {
      try {
        const storedToken = OAuthUtils.retrieveToken(storageKey);
        
        if (!storedToken) {
          setAuthState(prev => ({ ...prev, isLoading: false }));
          return;
        }

        // Check if token is expired
        if (OAuthUtils.isTokenExpired(storedToken)) {
          // Try to refresh if refresh token available
          if (storedToken.refreshToken && autoRefresh) {
            await refreshToken();
          } else {
            // Clear expired token
            OAuthUtils.clearToken(storageKey);
            setAuthState(prev => ({ ...prev, isLoading: false }));
          }
          return;
        }

        // Fetch user info with valid token
        const user = await OAuthUtils.fetchUserInfo(provider, storedToken);
        
        setAuthState({
          isAuthenticated: true,
          user,
          token: storedToken,
          isLoading: false,
          error: null,
        });

        onSuccess?.(user, storedToken);
      } catch (error) {
        const err = error instanceof Error ? error : new Error('Authentication failed');
        setAuthState({
          isAuthenticated: false,
          user: null,
          token: null,
          isLoading: false,
          error: err.message,
        });
        onError?.(err);
      }
    };

    initAuth();
  }, [provider, storageKey, autoRefresh, onSuccess, onError]);

  /**
   * Auto-refresh token when expiring
   */
  useEffect(() => {
    if (!autoRefresh || !authState.token || !authState.token.refreshToken) {
      return;
    }

    const checkAndRefresh = async () => {
      if (authState.token && OAuthUtils.isTokenExpiringSoon(authState.token)) {
        await refreshToken();
      }
    };

    // Check every minute
    const interval = setInterval(checkAndRefresh, 60 * 1000);
    return () => clearInterval(interval);
  }, [autoRefresh, authState.token]);

  /**
   * Initiate OAuth login flow
   */
  const login = useCallback(() => {
    try {
      // Generate CSRF state
      const state = OAuthUtils.generateState();
      
      // Store state for verification
      sessionStorage.setItem(`oauth_state_${provider.name}`, state);
      
      // Generate authorization URL
      const authUrl = OAuthUtils.generateAuthorizationUrl(provider, state);
      
      // Redirect to authorization URL
      window.location.href = authUrl;
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Login failed');
      setAuthState(prev => ({ ...prev, error: err.message }));
      onError?.(err);
    }
  }, [provider, onError]);

  /**
   * Handle OAuth callback
   */
  const handleCallback = useCallback(async (callbackUrl: string) => {
    try {
      setAuthState(prev => ({ ...prev, isLoading: true, error: null }));

      // Parse authorization response
      const response = OAuthUtils.parseAuthorizationResponse(callbackUrl);

      // Check for errors
      if (response.error) {
        throw new Error(response.errorDescription || response.error);
      }

      // Verify state (CSRF protection)
      const storedState = sessionStorage.getItem(`oauth_state_${provider.name}`);
      if (response.state !== storedState) {
        throw new Error('Invalid state parameter - possible CSRF attack');
      }

      // Clear stored state
      sessionStorage.removeItem(`oauth_state_${provider.name}`);

      // Exchange code for token
      const token = await OAuthUtils.exchangeCodeForToken(provider, response.code);

      // Fetch user info
      const user = await OAuthUtils.fetchUserInfo(provider, token);

      // Store token
      OAuthUtils.storeToken(token, storageKey);

      // Update state
      setAuthState({
        isAuthenticated: true,
        user,
        token,
        isLoading: false,
        error: null,
      });

      onSuccess?.(user, token);
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Callback handling failed');
      setAuthState({
        isAuthenticated: false,
        user: null,
        token: null,
        isLoading: false,
        error: err.message,
      });
      onError?.(err);
    }
  }, [provider, storageKey, onSuccess, onError]);

  /**
   * Refresh access token
   */
  const refreshToken = useCallback(async () => {
    try {
      if (!authState.token?.refreshToken) {
        throw new Error('No refresh token available');
      }

      const newToken = await OAuthUtils.refreshAccessToken(
        provider,
        authState.token.refreshToken
      );

      // Store new token
      OAuthUtils.storeToken(newToken, storageKey);

      // Update state
      setAuthState(prev => ({
        ...prev,
        token: newToken,
      }));

      return newToken;
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Token refresh failed');
      
      // Clear auth state on refresh failure
      logout();
      
      onError?.(err);
      throw err;
    }
  }, [authState.token, provider, storageKey, onError]);

  /**
   * Logout user
   */
  const logout = useCallback(() => {
    // Clear stored token
    OAuthUtils.clearToken(storageKey);

    // Clear state
    setAuthState({
      isAuthenticated: false,
      user: null,
      token: null,
      isLoading: false,
      error: null,
    });
  }, [storageKey]);

  return {
    // State
    ...authState,
    
    // Actions
    login,
    logout,
    handleCallback,
    refreshToken,
  };
}

export default useOAuth;
