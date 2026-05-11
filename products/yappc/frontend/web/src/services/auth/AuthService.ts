/**
 * Authentication Service
 * 
 * Handles user authentication, session management, and authorization
 * Replaces placeholder authentication logic with proper security
 */

import { parseJsonResponse as sharedParseJsonResponse } from '@/lib/http';
import { ApiRequestError, yappcApi } from '@/lib/api/client';
import type { components } from '@/clients/generated/openapi';
import { logger } from '../../utils/Logger';

type ApiRole = components['schemas']['AuthRole'];
type ApiAuthUser = components['schemas']['AuthUser'];
type ApiAuthResponse = components['schemas']['LoginResponse'];
type ApiLoginRequest = components['schemas']['LoginRequest'];
type ApiRefreshTokenRequest = components['schemas']['RefreshTokenRequest'];

type ApiProfileUpdate = Partial<Pick<User, 'firstName' | 'lastName' | 'username' | 'email' | 'avatar'>>;

async function parseJsonResponse<T>(response: Response, context: string): Promise<T> {
    return sharedParseJsonResponse<T>(response, context);
}

function parseStoredSession(storedSession: string): AuthSession {
    return JSON.parse(storedSession) as AuthSession;
}

export function isDemoLoginEnabled(): boolean {
    return import.meta.env.DEV && import.meta.env.VITE_ENABLE_DEMO_LOGIN === 'true';
}

export interface User {
    id: string;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    avatar?: string;
    role: 'admin' | 'user' | 'viewer';
    permissions: string[];
    lastLogin?: string;
    createdAt: string;
    updatedAt: string;
}

export interface AuthSession {
    user: User;
    token: string;
    refreshToken: string;
    expiresAt: string;
    permissions: string[];
}

export interface LoginCredentials {
    email: string;
    password: string;
    rememberMe?: boolean;
}

export interface RegisterData {
    firstName: string;
    lastName: string;
    username: string;
    email: string;
    password: string;
}

export interface AuthResult {
    success: boolean;
    user?: User;
    token?: string;
    error?: string;
    requiresTwoFactor?: boolean;
}

// Removed parseStoredSession function - no longer storing full sessions in localStorage

/**
 * Authentication Service
 */
export class AuthService {
    private static instance: AuthService;
    private currentSession: AuthSession | null = null;
    private sessionTimeout: NodeJS.Timeout | null = null;
    private readonly SESSION_DURATION = 24 * 60 * 60 * 1000; // 24 hours
    private readonly TOKEN_REFRESH_THRESHOLD = 15 * 60 * 1000; // 15 minutes

    private constructor() {
        // Initialize async - don't await in constructor
        void this.initializeFromStorage();
    }

    public static getInstance(): AuthService {
        if (!AuthService.instance) {
            AuthService.instance = new AuthService();
        }
        return AuthService.instance;
    }

    /**
     * Initialize authentication from stored session
     * 
     * SECURITY: Only restores non-sensitive metadata from localStorage.
     * Full session validation is done via server-backed /api/auth/me probe.
     */
    private async initializeFromStorage(): Promise<void> {
        try {
            if (typeof window === 'undefined' || !window.localStorage) {
                return;
            }
            
            const storedMetadata = window.localStorage.getItem('auth-session-meta');
            if (!storedMetadata) {
                return;
            }
            
            const metadata = JSON.parse(storedMetadata) as { userId: string; expiresAt: string };
            
            // Check if session is expired based on metadata
            if (new Date(metadata.expiresAt) <= new Date()) {
                this.clearSession();
                logger.info('Stored session expired, cleared', 'auth');
                return;
            }
            
            // Validate session with server-backed /api/auth/me probe
            try {
                const userInfo = await yappcApi.auth.me();
                
                // Ensure role is one of the expected values
                const normalizedUser = {
                    ...userInfo,
                    role: ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'].includes(userInfo.role as string)
                        ? userInfo.role as ApiRole
                        : 'VIEWER' as ApiRole,
                } satisfies ApiAuthUser;
                
                // Reconstruct session from server response
                const session: AuthSession = {
                    user: this.mapApiUser(normalizedUser),
                    token: '', // Tokens are in httpOnly cookies
                    refreshToken: '', // Tokens are in httpOnly cookies
                    expiresAt: metadata.expiresAt,
                    permissions: this.mapPermissions(normalizedUser.role),
                };
                
                this.currentSession = session;
                this.setupSessionRefresh();
                logger.info('Session validated and restored', 'auth', { userId: session.user.id });
            } catch (error) {
                // Server validation failed - clear session
                logger.warn('Server session validation failed, clearing session', 'auth', {
                    error: error instanceof Error ? error.message : String(error)
                });
                this.clearSession();
            }
        } catch (error) {
            logger.error('Failed to initialize auth from storage', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
            this.clearSession();
        }
    }

    /**
     * Login with credentials
     */
    public async login(credentials: LoginCredentials): Promise<AuthResult> {
        try {
            logger.info('Login attempt', 'auth', { email: credentials.email });

            // Validate input
            if (!credentials.email || !credentials.password) {
                return { success: false, error: 'Email and password are required' };
            }

            const authData = await yappcApi.auth.loginSession({
                    email: credentials.email,
                    password: credentials.password,
                } satisfies ApiLoginRequest);
            
            // In cookie mode, tokens are in httpOnly cookies
            // Call /api/auth/me to get complete user info
            const userInfo = await yappcApi.auth.me();
            
            // Ensure role is one of the expected values
            const normalizedUser = {
                ...userInfo,
                role: ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'].includes(userInfo.role as string)
                    ? userInfo.role as ApiRole
                    : 'VIEWER' as ApiRole,
            } satisfies ApiAuthUser;
            
            const session = this.createSessionFromApiResponse({
                ...authData,
                user: normalizedUser,
            });

            this.currentSession = session;
            this.saveSession(session);
            this.setupSessionRefresh();

            logger.info('Login successful', 'auth', {
                userId: session.user.id,
                email: session.user.email,
                role: session.user.role
            });

            return {
                success: true,
                user: session.user,
                token: session.token,
            };

        } catch (error) {
            if (error instanceof ApiRequestError && error.status === 401) {
                logger.warn('Authentication failed - invalid credentials', 'auth', {
                    email: credentials.email,
                    timestamp: new Date().toISOString(),
                });
                return { success: false, error: 'Invalid email or password' };
            }

            if (error instanceof ApiRequestError && error.status === 423) {
                logger.warn('Authentication failed - account locked', 'auth', {
                    email: credentials.email,
                    timestamp: new Date().toISOString(),
                });
                return { success: false, error: 'Account temporarily locked' };
            }

            logger.error('Login error', 'auth', {
                error: error instanceof Error ? error.message : String(error),
                email: credentials.email
            });

            return {
                success: false,
                error: error instanceof Error ? error.message : 'Authentication failed',
            };
        }
    }

    /**
     * Register new user
     */
    public async register(userData: RegisterData): Promise<AuthResult> {
        try {
            logger.info('Registration attempt', 'auth', {
                username: userData.username,
                email: userData.email
            });

            // Validate input
            if (!userData.username || !userData.email || !userData.password || !userData.firstName || !userData.lastName) {
                return { success: false, error: 'All fields are required' };
            }

            logger.warn('Registration route is disabled because no backend endpoint is available', 'auth', {
                username: userData.username,
                email: userData.email,
            });

            return {
                success: false,
                error: 'Registration is not available in this deployment',
            };

        } catch (error) {
            logger.error('Registration error', 'auth', {
                error: error instanceof Error ? error.message : String(error),
                username: userData.username
            });

            return {
                success: false,
                error: error instanceof Error ? error.message : 'Registration failed',
            };
        }
    }

    /**
     * Request a password-reset email for the given address.
     */
    public async forgotPassword(email: string): Promise<{ success: boolean; error?: string }> {
        try {
            if (!email) {
                return { success: false, error: 'Email is required' };
            }

            logger.info('Forgot-password request', 'auth', { email });

            logger.warn('Forgot-password route is disabled because no backend endpoint is available', 'auth', {
                email,
            });
            return {
                success: false,
                error: 'Password reset is not available in this deployment',
            };
        } catch (error) {
            logger.error('Forgot-password error', 'auth', {
                error: error instanceof Error ? error.message : String(error),
            });
            return { success: false, error: 'Failed to send reset email' };
        }
    }

    /**
     * Logout current user
     */
    public async logout(): Promise<void> {
        try {
            if (this.currentSession) {
                logger.info('Logout attempt', 'auth', { userId: this.currentSession.user.id });

                // Call logout API - in cookie mode, no refreshToken needed
                await yappcApi.auth.logout();
            }
        } catch (error) {
            logger.warn('Logout API call failed', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
        } finally {
            this.clearSession();
            logger.info('Logout completed', 'auth');
        }
    }

    /**
     * Get current user
     */
    public getCurrentUser(): User | null {
        return this.currentSession?.user || null;
    }

    /**
     * Get current session
     */
    public getCurrentSession(): AuthSession | null {
        return this.currentSession;
    }

    /**
     * Check if user is authenticated
     */
    public isAuthenticated(): boolean {
        return this.currentSession !== null && this.isSessionValid(this.currentSession);
    }

    /**
     * Check if user has specific permission
     */
    public hasPermission(permission: string): boolean {
        if (!this.currentSession) return false;
        return this.currentSession.permissions.includes(permission) ||
            this.currentSession.permissions.includes('*');
    }

    /**
     * Check if user has specific role
     */
    public hasRole(role: string): boolean {
        if (!this.currentSession) return false;
        return this.currentSession.user.role === role;
    }

    /**
     * Get auth token for API calls
     *
     * SECURITY: In cookie mode, tokens are in httpOnly cookies and cannot be read from JavaScript.
     * This method returns empty string for cookie mode. The API client automatically sends cookies.
     */
    public getAuthToken(): string | null {
        // Tokens are in httpOnly cookies - not accessible from JavaScript
        // The API client handles cookie-based auth automatically
        return null;
    }

    /**
     * Refresh authentication token
     *
     * SECURITY: In cookie mode, the server sets new httpOnly cookies.
     * We only need to update the expiresAt metadata from the response.
     */
    public async refreshToken(): Promise<boolean> {
        try {
            if (!this.currentSession) {
                return false;
            }

            // Call refresh endpoint - server will set new httpOnly cookies
            // In cookie mode, no refreshToken needed in request body
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({}),
            });

            if (!response.ok) {
                logger.warn('Token refresh failed', 'auth', { status: response.status });
                return false;
            }

            const data = await parseJsonResponse<{ expiresAt: string; authMode: string }>(
                response,
                'auth token refresh'
            );

            // Update session expiresAt from response
            this.currentSession.expiresAt = data.expiresAt;
            this.saveSession(this.currentSession);
            this.setupSessionRefresh();

            logger.info('Token refreshed successfully', 'auth', { userId: this.currentSession.user.id });
            return true;

        } catch (error) {
            logger.error('Token refresh failed', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
            this.clearSession();
            return false;
        }
    }

    /**
     * Demo user login for development/testing
     */
    public async demoLogin(): Promise<AuthResult> {
        if (!isDemoLoginEnabled()) {
            logger.warn('Demo login denied outside explicit dev mode', 'auth');
            return {
                success: false,
                error: 'Demo login is unavailable in this environment',
            };
        }

        const demoUser: User = {
            id: 'demo-user',
            username: 'demo',
            email: 'demo@yappc.com',
            firstName: 'Demo',
            lastName: 'User',
            role: 'admin',
            permissions: ['*'],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };

        const session: AuthSession = {
            user: demoUser,
            token: 'demo-token',
            refreshToken: 'demo-refresh',
            expiresAt: new Date(Date.now() + this.SESSION_DURATION).toISOString(),
            permissions: ['*'],
        };

        this.currentSession = session;
        this.saveSession(session);
        this.setupSessionRefresh();

        logger.info('Demo login successful', 'auth', { userId: demoUser.id });

        return {
            success: true,
            user: demoUser,
            token: session.token,
        };
    }

    /**
     * Check if session is valid
     */
    private isSessionValid(session: AuthSession): boolean {
        return new Date(session.expiresAt) > new Date();
    }

    /**
     * Save session to storage
     * 
     * SECURITY: Tokens are stored in httpOnly cookies only.
     * We only persist non-sensitive metadata (userId, expiresAt) to localStorage
     * for session restoration on page reload. Tokens are NEVER stored in localStorage.
     */
    private saveSession(session: AuthSession): void {
        try {
            if (typeof window === 'undefined' || !window.localStorage) {
                return;
            }
            
            // Only store non-sensitive metadata - never tokens
            const sessionMetadata = {
                userId: session.user.id,
                expiresAt: session.expiresAt,
            };
            
            localStorage.setItem('auth-session-meta', JSON.stringify(sessionMetadata));
        } catch (error) {
            logger.error('Failed to save session metadata to storage', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
        }
    }

    private createSessionFromApiResponse(authData: ApiAuthResponse): AuthSession {
        // In cookie mode, tokens are in httpOnly cookies, not in the response body
        // The response only contains user metadata
        const hasTokens = 'tokens' in authData && authData.tokens !== undefined;
        
        return {
            user: this.mapApiUser(authData.user),
            token: hasTokens ? authData.tokens.accessToken : '', // Tokens are in cookies
            refreshToken: hasTokens ? authData.tokens.refreshToken : '', // Tokens are in cookies
            expiresAt: hasTokens 
                ? new Date(Date.now() + authData.tokens.expiresIn * 1000).toISOString()
                : new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(), // Default 24h for cookie mode
            permissions: this.mapPermissions(authData.user.role),
        };
    }

    private mapApiUser(user: ApiAuthUser): User {
        const [firstName, ...lastNameParts] = user.name.trim().split(/\s+/).filter(Boolean);
        const lastName = lastNameParts.join(' ');
        return {
            id: user.id,
            username: user.email,
            email: user.email,
            firstName: firstName || user.email,
            lastName,
            avatar: user.avatar,
            role: this.mapRole(user.role),
            permissions: this.mapPermissions(user.role),
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };
    }

    private mapRole(role: ApiRole): User['role'] {
        if (role === 'ADMIN' || role === 'OWNER') {
            return 'admin';
        }
        if (role === 'VIEWER') {
            return 'viewer';
        }
        return 'user';
    }

    private mapPermissions(role: ApiRole): string[] {
        if (role === 'ADMIN' || role === 'OWNER') {
            return ['*'];
        }
        if (role === 'EDITOR') {
            return ['workspace:read', 'workspace:write', 'project:read', 'project:write'];
        }
        return ['workspace:read', 'project:read'];
    }

    /**
     * Clear current session
     * 
     * SECURITY: Only clears metadata from localStorage.
     * Tokens are cleared by server via httpOnly cookie expiration.
     */
    private clearSession(): void {
        this.currentSession = null;
        if (this.sessionTimeout) {
            clearTimeout(this.sessionTimeout);
            this.sessionTimeout = null;
        }
        try {
            if (typeof window !== 'undefined' && window.localStorage) {
                // Only clear metadata - tokens are in httpOnly cookies
                window.localStorage.removeItem('auth-session-meta');
                // Also clear legacy keys if they exist
                window.localStorage.removeItem('auth-session');
                window.localStorage.removeItem('auth_token');
                window.localStorage.removeItem('api_key');
            }
        } catch (error) {
            logger.warn('Failed to clear session metadata from storage', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
        }
    }

    /**
     * Setup automatic session refresh with activity tracking
     */
    private setupSessionRefresh(): void {
        if (this.sessionTimeout) {
            clearTimeout(this.sessionTimeout);
        }

        if (!this.currentSession) return;

        const expiresAt = new Date(this.currentSession.expiresAt).getTime();
        const now = Date.now();
        const remainingLifetime = expiresAt - now;

        if (remainingLifetime <= 0) {
            void this.refreshToken();
            return;
        }

        const refreshLeadTime = Math.min(
            this.TOKEN_REFRESH_THRESHOLD,
            Math.max(60_000, Math.floor(remainingLifetime / 2))
        );
        const refreshDelay = Math.max(1_000, remainingLifetime - refreshLeadTime);

        this.sessionTimeout = setTimeout(() => {
            void this.refreshToken();
        }, refreshDelay);
        
        // Log security event for session refresh setup
        logger.info('Session refresh scheduled', 'auth', {
            userId: this.currentSession.user.id,
            refreshDelay,
            expiresAt: this.currentSession.expiresAt,
        });
    }

    /**
     * Validate session and redirect if needed
     */
    public validateSession(): boolean {
        if (!this.currentSession) {
            return false;
        }

        if (!this.isSessionValid(this.currentSession)) {
            logger.info('Session expired, clearing', 'auth');
            this.clearSession();
            return false;
        }

        return true;
    }

    /**
     * Update user profile
     */
    public async updateProfile(updates: Partial<User>): Promise<AuthResult> {
        try {
            if (!this.currentSession) {
                return { success: false, error: 'Not authenticated' };
            }

            const updatedUser = await yappcApi.auth.updateProfile(updates);

            // Update session with new user data
            this.currentSession.user = { ...this.currentSession.user, ...updatedUser };
            this.currentSession.user.updatedAt = new Date().toISOString();
            this.saveSession(this.currentSession);

            logger.info('Profile updated successfully', 'auth', { userId: this.currentSession.user.id });

            return {
                success: true,
                user: this.currentSession.user,
                token: this.currentSession.token,
            };

        } catch (error) {
            logger.error('Profile update failed', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });

            return {
                success: false,
                error: error instanceof Error ? error.message : 'Profile update failed',
            };
        }
    }
}

// Export singleton instance
export const authService = AuthService.getInstance();

export default authService;
