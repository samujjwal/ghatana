/**
 * Authentication Service
 * 
 * Handles user authentication, session management, and authorization
 * Replaces placeholder authentication logic with proper security
 */

import { logger } from '../../utils/Logger';

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
    username: string;
    password: string;
    rememberMe?: boolean;
}

export interface RegisterData {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
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
            logger.info('Login attempt', 'auth', { username: credentials.username });

            // Validate input
            if (!credentials.username || !credentials.password) {
                return { success: false, error: 'Username and password are required' };
            }

            // Call authentication API
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    username: credentials.username,
                    password: credentials.password,
                    rememberMe: credentials.rememberMe || false,
                }),
            });

            if (!response.ok) {
                if (response.status === 401) {
                    logger.warn('Invalid credentials', 'auth', { username: credentials.username });
                    return { success: false, error: 'Invalid username or password' };
                }

                if (response.status === 423) {
                    logger.warn('Account locked', 'auth', { username: credentials.username });
                    return { success: false, error: 'Account temporarily locked' };
                }

                throw new Error(`Authentication failed: ${response.statusText}`);
            }

            const authData = await response.json();

            // Create session
            const session: AuthSession = {
                user: authData.user,
                token: authData.token,
                refreshToken: authData.refreshToken,
                expiresAt: authData.expiresAt,
                permissions: authData.permissions || [],
            };

            this.currentSession = session;
            this.saveSession(session);
            this.setupSessionRefresh();

            logger.info('Login successful', 'auth', {
                userId: session.user.id,
                username: session.user.username,
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
                username: credentials.username
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
            if (!userData.username || !userData.email || !userData.password) {
                return { success: false, error: 'All fields are required' };
            }

            // Call registration API
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(userData),
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

            const authData = await response.json();

            // Auto-login after successful registration
            return this.login({
                username: userData.username,
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
                        'Authorization': `Bearer ${this.currentSession.token}`,
                    },
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
                    'Authorization': `Bearer ${this.currentSession.refreshToken}`,
                },
            });

            if (!response.ok) {
                throw new Error('Token refresh failed');
            }

            const authData = await response.json();

            // Update session with new token
            this.currentSession.token = authData.token;
            this.currentSession.expiresAt = authData.expiresAt;
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
        const refreshTime = expiresAt - this.TOKEN_REFRESH_THRESHOLD;

        if (refreshTime > now) {
            this.sessionTimeout = setTimeout(() => {
                this.refreshToken();
            }, refreshTime - now);
        } else {
            // Token is about to expire, refresh now
            this.refreshToken();
        }
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
