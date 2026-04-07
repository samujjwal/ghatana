// @ts-nocheck
/**
 * Authentication Service
 * 
 * Handles user authentication, session management, and authorization
 * Replaces placeholder authentication logic with proper security
 */

import { logger } from '../../utils/Logger';

type ApiRole = 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';

type ApiAuthUser = {
    id: string;
    email: string;
    name: string;
    role: ApiRole;
    avatar?: string;
};

type ApiAuthTokens = {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
};

type ApiAuthResponse = {
    user: ApiAuthUser;
    tokens: ApiAuthTokens;
};

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
        this.initializeFromStorage();
    }

    public static getInstance(): AuthService {
        if (!AuthService.instance) {
            AuthService.instance = new AuthService();
        }
        return AuthService.instance;
    }

    /**
     * Initialize authentication from stored session
     */
    private initializeFromStorage(): void {
        try {
            const storedSession = localStorage.getItem('auth-session');
            if (storedSession) {
                const session: AuthSession = JSON.parse(storedSession);
                if (this.isSessionValid(session)) {
                    this.currentSession = session;
                    this.setupSessionRefresh();
                    logger.info('Session restored from storage', 'auth', { userId: session.user.id });
                } else {
                    this.clearSession();
                    logger.info('Stored session expired, cleared', 'auth');
                }
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

            // Call authentication API
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    email: credentials.email,
                    password: credentials.password,
                    rememberMe: credentials.rememberMe || false,
                }),
            });

            if (!response.ok) {
                if (response.status === 401) {
                    logger.warn('Invalid credentials', 'auth', { email: credentials.email });
                    return { success: false, error: 'Invalid email or password' };
                }

                if (response.status === 423) {
                    logger.warn('Account locked', 'auth', { email: credentials.email });
                    return { success: false, error: 'Account temporarily locked' };
                }

                throw new Error(`Authentication failed: ${response.statusText}`);
            }

            const authData = await response.json();
            const session = this.createSessionFromApiResponse(authData);

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

            const name = `${userData.firstName} ${userData.lastName}`.trim() || userData.username;

            // Call registration API
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    email: userData.email,
                    password: userData.password,
                    name,
                }),
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));

                if (response.status === 409) {
                    return { success: false, error: 'Username or email already exists' };
                }

                if (response.status === 422) {
                    return { success: false, error: errorData.message || 'Invalid data provided' };
                }

                throw new Error(`Registration failed: ${response.statusText}`);
            }

            // Auto-login after successful registration
            return this.login({
                email: userData.email,
                password: userData.password,
            });

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

            const response = await fetch('/api/auth/forgot-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email }),
            });

            if (!response.ok) {
                const err = await response.json().catch(() => ({}));
                return { success: false, error: err.message || 'Failed to send reset email' };
            }

            return { success: true };
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

                // Call logout API
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        refreshToken: this.currentSession.refreshToken,
                    }),
                });
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
     */
    public getAuthToken(): string | null {
        return this.currentSession?.token || null;
    }

    /**
     * Refresh authentication token
     */
    public async refreshToken(): Promise<boolean> {
        try {
            if (!this.currentSession) {
                return false;
            }

            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    refreshToken: this.currentSession.refreshToken,
                }),
            });

            if (!response.ok) {
                throw new Error('Token refresh failed');
            }

            const authData = await response.json() as ApiAuthTokens;

            // Update session with new token pair
            this.currentSession.token = authData.accessToken;
            this.currentSession.refreshToken = authData.refreshToken;
            this.currentSession.expiresAt = new Date(Date.now() + authData.expiresIn * 1000).toISOString();
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
     */
    private saveSession(session: AuthSession): void {
        try {
            localStorage.setItem('auth-session', JSON.stringify(session));
        } catch (error) {
            logger.error('Failed to save session to storage', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
        }
    }

    private createSessionFromApiResponse(authData: ApiAuthResponse): AuthSession {
        return {
            user: this.mapApiUser(authData.user),
            token: authData.tokens.accessToken,
            refreshToken: authData.tokens.refreshToken,
            expiresAt: new Date(Date.now() + authData.tokens.expiresIn * 1000).toISOString(),
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
     */
    private clearSession(): void {
        this.currentSession = null;
        if (this.sessionTimeout) {
            clearTimeout(this.sessionTimeout);
            this.sessionTimeout = null;
        }
        try {
            localStorage.removeItem('auth-session');
        } catch (error) {
            logger.warn('Failed to clear session from storage', 'auth', {
                error: error instanceof Error ? error.message : String(error)
            });
        }
    }

    /**
     * Setup automatic session refresh
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

            const response = await fetch('/api/auth/profile', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.currentSession.token}`,
                },
                body: JSON.stringify(updates),
            });

            if (!response.ok) {
                throw new Error(`Profile update failed: ${response.statusText}`);
            }

            const updatedUser = await response.json();

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
