/**
 * Authentication API Client
 * 
 * Production-ready auth service with TypeScript types,
 * error handling, and request/response interceptors.
 * 
 * @module api/auth
 * @doc.type service
 * @doc.purpose Backend authentication integration
 * @doc.layer api
 */

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
  private baseUrl: string;
  private timeout: number;
  private retryAttempts: number;
  private onTokenExpired?: () => void;
  private onUnauthorized?: () => void;
  
  constructor(config: AuthServiceConfig = {}) {
    this.baseUrl = config.baseUrl || process.env.VITE_API_BASE_URL || '/api';
    this.timeout = config.timeout || 30000;
    this.retryAttempts = config.retryAttempts || 1;
    this.onTokenExpired = config.onTokenExpired;
    this.onUnauthorized = config.onUnauthorized;
  }
  
  /**
   * Make authenticated request
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    
    // Add default headers
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };
    
    // Add auth token if available
    const token = localStorage.getItem('auth_token');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    
    const config: RequestInit = {
      ...options,
      headers,
      signal: AbortSignal.timeout(this.timeout),
    };
    
    try {
      const response = await fetch(url, config);
      
      // Handle different status codes
      if (response.status === 401) {
        if (this.onUnauthorized) {
          this.onUnauthorized();
        }
        throw new Error('Unauthorized');
      }
      
      if (response.status === 403) {
        if (this.onTokenExpired) {
          this.onTokenExpired();
        }
        throw new Error('Token expired');
      }
      
      if (!response.ok) {
        const error: ApiError = await response.json().catch(() => ({
          message: `HTTP ${response.status}: ${response.statusText}`,
        }));
        throw new Error(error.message);
      }
      
      return await response.json();
    } catch (error) {
      if (error instanceof Error) {
        throw error;
      }
      throw new Error('Network error');
    }
  }
  
  /**
   * Login user
   */
  async login(payload: LoginRequest): Promise<LoginResponse> {
    return this.request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Register new user
   */
  async register(payload: RegisterRequest): Promise<RegisterResponse> {
    return this.request<RegisterResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Logout user
   */
  async logout(payload: LogoutRequest = {}): Promise<void> {
    return this.request<void>('/auth/logout', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Refresh access token
   */
  async refreshToken(payload: RefreshTokenRequest): Promise<RefreshTokenResponse> {
    return this.request<RefreshTokenResponse>('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Get current user profile
   */
  async me(): Promise<User> {
    return this.request<User>('/auth/me', {
      method: 'GET',
    });
  }
  
  /**
   * Request password reset
   */
  async requestPasswordReset(payload: PasswordResetRequest): Promise<void> {
    return this.request<void>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Confirm password reset
   */
  async confirmPasswordReset(payload: PasswordResetConfirmRequest): Promise<void> {
    return this.request<void>('/auth/reset-password/confirm', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Update user profile
   */
  async updateProfile(payload: Partial<User>): Promise<User> {
    return this.request<User>('/auth/profile', {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  }
  
  /**
   * Change password
   */
  async changePassword(payload: {
    currentPassword: string;
    newPassword: string;
  }): Promise<void> {
    return this.request<void>('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }
}

// ============================================================================
// Default Export
// ============================================================================

/**
 * Default auth service instance
 */
export const authService = new AuthService();
