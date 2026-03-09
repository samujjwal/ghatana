/**
 * OAuth Provider Configurations
 * 
 * Pre-configured OAuth providers for common services.
 * 
 * @module auth/oauth
 */

import type { OAuthProvider } from './types';

/**
 * Get OAuth redirect URI based on environment
 */
const getRedirectUri = (provider: string): string => {
  const baseUrl = typeof window !== 'undefined' 
    ? window.location.origin 
    : process.env.REACT_APP_BASE_URL || 'http://localhost:3000';
  return `${baseUrl}/auth/callback/${provider}`;
};

/**
 * Google OAuth Provider Configuration
 * 
 * @param clientId - Google OAuth client ID
 * @param clientSecret - Google OAuth client secret (optional, server-side only)
 * @returns Google OAuth provider configuration
 * 
 * @example
 * ```ts
 * const google = createGoogleProvider(
 *   process.env.REACT_APP_GOOGLE_CLIENT_ID!
 * );
 * ```
 */
export function createGoogleProvider(
  clientId: string,
  clientSecret?: string
): OAuthProvider {
  return {
    name: 'google',
    clientId,
    clientSecret,
    authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
    tokenUrl: 'https://oauth2.googleapis.com/token',
    userInfoUrl: 'https://www.googleapis.com/oauth2/v2/userinfo',
    redirectUri: getRedirectUri('google'),
    scopes: [
      'openid',
      'https://www.googleapis.com/auth/userinfo.email',
      'https://www.googleapis.com/auth/userinfo.profile',
    ],
  };
}

/**
 * GitHub OAuth Provider Configuration
 * 
 * @param clientId - GitHub OAuth client ID
 * @param clientSecret - GitHub OAuth client secret (optional, server-side only)
 * @returns GitHub OAuth provider configuration
 * 
 * @example
 * ```ts
 * const github = createGitHubProvider(
 *   process.env.REACT_APP_GITHUB_CLIENT_ID!
 * );
 * ```
 */
export function createGitHubProvider(
  clientId: string,
  clientSecret?: string
): OAuthProvider {
  return {
    name: 'github',
    clientId,
    clientSecret,
    authorizationUrl: 'https://github.com/login/oauth/authorize',
    tokenUrl: 'https://github.com/login/oauth/access_token',
    userInfoUrl: 'https://api.github.com/user',
    redirectUri: getRedirectUri('github'),
    scopes: ['read:user', 'user:email'],
  };
}

/**
 * Microsoft OAuth Provider Configuration
 * 
 * @param clientId - Microsoft OAuth client ID
 * @param clientSecret - Microsoft OAuth client secret (optional, server-side only)
 * @param tenant - Microsoft tenant ID (default: 'common')
 * @returns Microsoft OAuth provider configuration
 */
export function createMicrosoftProvider(
  clientId: string,
  clientSecret?: string,
  tenant: string = 'common'
): OAuthProvider {
  return {
    name: 'microsoft',
    clientId,
    clientSecret,
    authorizationUrl: `https://login.microsoftonline.com/${tenant}/oauth2/v2.0/authorize`,
    tokenUrl: `https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token`,
    userInfoUrl: 'https://graph.microsoft.com/v1.0/me',
    redirectUri: getRedirectUri('microsoft'),
    scopes: ['openid', 'profile', 'email', 'User.Read'],
  };
}

/**
 * Pre-configured OAuth providers
 * 
 * Use environment variables to configure providers:
 * - REACT_APP_GOOGLE_CLIENT_ID
 * - REACT_APP_GITHUB_CLIENT_ID
 * - REACT_APP_MICROSOFT_CLIENT_ID
 */
export const OAuthProviders = {
  /**
   * Get Google OAuth provider
   */
  google: () => {
    const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID;
    if (!clientId) {
      throw new Error('REACT_APP_GOOGLE_CLIENT_ID is not configured');
    }
    return createGoogleProvider(clientId);
  },

  /**
   * Get GitHub OAuth provider
   */
  github: () => {
    const clientId = process.env.REACT_APP_GITHUB_CLIENT_ID;
    if (!clientId) {
      throw new Error('REACT_APP_GITHUB_CLIENT_ID is not configured');
    }
    return createGitHubProvider(clientId);
  },

  /**
   * Get Microsoft OAuth provider
   */
  microsoft: (tenant?: string) => {
    const clientId = process.env.REACT_APP_MICROSOFT_CLIENT_ID;
    if (!clientId) {
      throw new Error('REACT_APP_MICROSOFT_CLIENT_ID is not configured');
    }
    return createMicrosoftProvider(clientId, undefined, tenant);
  },
};
