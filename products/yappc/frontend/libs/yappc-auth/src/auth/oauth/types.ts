/**
 * OAuth 2.0 Authentication Types
 * Type definitions for OAuth 2.0 authentication flow
 */

/**
 * OAuth provider configuration
 * Configuration for OAuth 2.0 providers (Google, GitHub, Microsoft, etc.)
 */
export interface OAuthProvider {
  /** Provider name (google, github, microsoft, etc.) */
  name: string;
  /** OAuth client ID */
  clientId: string;
  /** OAuth client secret (server-side only) */
  clientSecret?: string;
  /** Authorization endpoint URL */
  authorizationUrl: string;
  /** Token endpoint URL */
  tokenUrl: string;
  /** User info endpoint URL */
  userInfoUrl: string;
  /** Redirect URI for OAuth callback */
  redirectUri: string;
  /** Scopes to request */
  scopes: string[];
}

/**
 * OAuth token interface
 * Represents an OAuth access token and related metadata
 */
export interface OAuthToken {
  /** Access token */
  accessToken: string;
  /** Token type (usually "Bearer") */
  tokenType: string;
  /** Token expiration time in seconds */
  expiresIn: number;
  /** Refresh token (if available) */
  refreshToken?: string;
  /** Token scope */
  scope: string;
  /** Token issued at timestamp */
  issuedAt: number;
}

/**
 * OAuth user interface
 * Represents an authenticated user from OAuth provider
 */
export interface OAuthUser {
  /** User ID from provider */
  id: string;
  /** User email */
  email: string;
  /** User display name */
  name: string;
  /** User avatar URL */
  avatar?: string;
  /** Provider name */
  provider: string;
  /** Additional provider-specific data */
  metadata?: Record<string, unknown>;
}

/**
 * Authentication state interface
 * Current authentication state
 */
export interface AuthState {
  /** Is user authenticated */
  isAuthenticated: boolean;
  /** Current user (if authenticated) */
  user: OAuthUser | null;
  /** Current OAuth token (if authenticated) */
  token: OAuthToken | null;
  /** Authentication loading state */
  isLoading: boolean;
  /** Authentication error (if any) */
  error: string | null;
}

/**
 * Authorization code response interface
 * Response from authorization server
 */
export interface AuthorizationCodeResponse {
  /** Authorization code */
  code: string;
  /** State parameter for CSRF protection */
  state: string;
  /** Error (if authorization failed) */
  error?: string;
  /** Error description */
  errorDescription?: string;
}

/**
 * Token response interface
 * Response from token endpoint
 */
export interface TokenResponse {
  /** Access token */
  access_token: string;
  /** Token type */
  token_type: string;
  /** Expiration time in seconds */
  expires_in: number;
  /** Refresh token (if available) */
  refresh_token?: string;
  /** Scope */
  scope: string;
}

/**
 * User info response interface
 * Response from user info endpoint
 */
export interface UserInfoResponse {
  /** User ID */
  id: string;
  /** User email */
  email: string;
  /** User name */
  name: string;
  /** User avatar URL */
  picture?: string;
  /** Additional fields */
  [key: string]: unknown;
}
