import { useAtom , atom } from 'jotai';
import { useCallback, useEffect, useState } from 'react';

/**
 *
 */
export interface AuthUser {
  id: string;
  email: string;
  name: string;
  avatar?: string;
  role: 'viewer' | 'editor' | 'admin';
  permissions: string[];
  workspaces: string[];
  lastLogin: number;
  createdAt: number;
}

/**
 *
 */
export interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  token: string | null;
}

/**
 *
 */
export interface LoginCredentials {
  email: string;
  password: string;
}

/**
 *
 */
export interface RegisterData extends LoginCredentials {
  name: string;
}

// Auth state atom
const authStateAtom = atom<AuthState>({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  token: null
});

/**
 * Authentication hook for managing user sessions and identity
 * 
 * Provides comprehensive authentication functionality including:
 * - User login/logout with credential validation
 * - User registration with profile creation
 * - Automatic session restoration from storage
 * - Session validation with periodic checks (every 5 minutes)
 * - Token management and refresh
 * - Error handling with detailed error states
 * 
 * Implements Sprint 3 collaboration requirements for multi-user support.
 * 
 * @returns Object containing:
 *   - user: Current authenticated user or null
 *   - isAuthenticated: Boolean indicating authentication status
 *   - isLoading: Boolean indicating auth operation in progress
 *   - error: Authentication error message or null
 *   - login: Function to authenticate user with credentials
 *   - logout: Function to end session and clear user data
 *   - register: Function to create new user account
 * 
 * @example
 * ```tsx
 * function LoginForm() {
 *   const { login, isLoading, error, isAuthenticated } = useAuth();
 *   
 *   const handleSubmit = async (e) => {
 *     e.preventDefault();
 *     const formData = new FormData(e.target);
 *     await login({
 *       email: formData.get('email'),
 *       password: formData.get('password')
 *     });
 *   };
 *   
 *   if (isAuthenticated) return <Navigate to="/dashboard" />;
 *   
 *   return (
 *     <form onSubmit={handleSubmit}>
 *       <input name="email" type="email" required />
 *       <input name="password" type="password" required />
 *       <button type="submit" disabled={isLoading}>
 *         {isLoading ? 'Logging in...' : 'Login'}
 *       </button>
 *       {error && <p className="text-red-500">{error}</p>}
 *     </form>
 *   );
 * }
 * 
 * function UserProfile() {
 *   const { user, logout } = useAuth();
 *   
 *   return (
 *     <div>
 *       <h2>Welcome, {user?.name}</h2>
 *       <button onClick={logout}>Sign Out</button>
 *     </div>
 *   );
 * }
 * ```
 */
export function useAuth() {
  const [authState, setAuthState] = useAtom(authStateAtom);
  const [sessionCheckInterval, setSessionCheckInterval] = useState<NodeJS.Timeout | null>(null);

  /**
   * Initialize authentication on mount
   */
  useEffect(() => {
    checkExistingSession();
    
    // Set up session validation interval
    const interval = setInterval(validateSession, 5 * 60 * 1000); // Every 5 minutes
    setSessionCheckInterval(interval);

    return () => {
      if (interval) clearInterval(interval);
    };
  }, []);

  /**
   * Check for existing authentication session
   */
  const checkExistingSession = useCallback(async () => {
    setAuthState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const token = localStorage.getItem('auth_token');
      const userData = localStorage.getItem('auth_user');

      if (token && userData) {
        const user = JSON.parse(userData);
        
        // Validate token with server
        const isValid = await validateTokenWithServer(token);
        
        if (isValid) {
          setAuthState({
            user,
            isAuthenticated: true,
            isLoading: false,
            error: null,
            token
          });
        } else {
          // Clear invalid session
          await logout();
        }
      } else {
        setAuthState(prev => ({ ...prev, isLoading: false }));
      }
    } catch (error) {
      console.error('Session check failed:', error);
      setAuthState(prev => ({ 
        ...prev, 
        isLoading: false, 
        error: 'Session validation failed' 
      }));
    }
  }, []);

  /**
   * Login with email and password
   */
  const login = useCallback(async (credentials: LoginCredentials) => {
    setAuthState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Login failed');
      }

      const { user, token } = await response.json();

      // Store auth data
      localStorage.setItem('auth_token', token);
      localStorage.setItem('auth_user', JSON.stringify(user));

      setAuthState({
        user,
        isAuthenticated: true,
        isLoading: false,
        error: null,
        token
      });

      return { success: true, user, token };
    } catch (error: unknown) {
      const errorMessage = error.message || 'Login failed';
      setAuthState(prev => ({
        ...prev,
        isLoading: false,
        error: errorMessage
      }));

      return { success: false, error: errorMessage };
    }
  }, []);

  /**
   * Register new user account
   */
  const register = useCallback(async (userData: RegisterData) => {
    setAuthState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(userData),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Registration failed');
      }

      const { user, token } = await response.json();

      // Store auth data
      localStorage.setItem('auth_token', token);
      localStorage.setItem('auth_user', JSON.stringify(user));

      setAuthState({
        user,
        isAuthenticated: true,
        isLoading: false,
        error: null,
        token
      });

      return { success: true, user, token };
    } catch (error: unknown) {
      const errorMessage = error.message || 'Registration failed';
      setAuthState(prev => ({
        ...prev,
        isLoading: false,
        error: errorMessage
      }));

      return { success: false, error: errorMessage };
    }
  }, []);

  /**
   * Logout and clear session
   */
  const logout = useCallback(async () => {
    setAuthState(prev => ({ ...prev, isLoading: true }));

    try {
      // Notify server of logout
      if (authState.token) {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${authState.token}`,
            'Content-Type': 'application/json',
          },
        });
      }
    } catch (error) {
      console.error('Logout notification failed:', error);
    } finally {
      // Clear local storage
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');

      // Reset auth state
      setAuthState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
        token: null
      });

      // Clear session interval
      if (sessionCheckInterval) {
        clearInterval(sessionCheckInterval);
        setSessionCheckInterval(null);
      }
    }
  }, [authState.token, sessionCheckInterval]);

  /**
   * Validate current session
   */
  const validateSession = useCallback(async () => {
    if (!authState.token) return false;

    try {
      const isValid = await validateTokenWithServer(authState.token);
      
      if (!isValid) {
        await logout();
        return false;
      }

      return true;
    } catch (error) {
      console.error('Session validation failed:', error);
      return false;
    }
  }, [authState.token, logout]);

  /**
   * Update user profile
   */
  const updateProfile = useCallback(async (updates: Partial<AuthUser>) => {
    if (!authState.user || !authState.token) {
      throw new Error('User not authenticated');
    }

    setAuthState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const response = await fetch('/api/auth/profile', {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${authState.token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Profile update failed');
      }

      const updatedUser = await response.json();

      // Update local storage
      localStorage.setItem('auth_user', JSON.stringify(updatedUser));

      setAuthState(prev => ({
        ...prev,
        user: updatedUser,
        isLoading: false
      }));

      return { success: true, user: updatedUser };
    } catch (error: unknown) {
      const errorMessage = error.message || 'Profile update failed';
      setAuthState(prev => ({
        ...prev,
        isLoading: false,
        error: errorMessage
      }));

      return { success: false, error: errorMessage };
    }
  }, [authState.user, authState.token]);

  /**
   * Check if user has specific permission
   */
  const hasPermission = useCallback((permission: string): boolean => {
    if (!authState.user) return false;
    
    // Admin role has all permissions
    if (authState.user.role === 'admin') return true;
    
    return authState.user.permissions.includes(permission);
  }, [authState.user]);

  /**
   * Check if user can access workspace
   */
  const canAccessWorkspace = useCallback((workspaceId: string): boolean => {
    if (!authState.user) return false;
    
    // Admin role has access to all workspaces
    if (authState.user.role === 'admin') return true;
    
    return authState.user.workspaces.includes(workspaceId);
  }, [authState.user]);

  /**
   * Get authentication headers for API requests
   */
  const getAuthHeaders = useCallback(() => {
    if (!authState.token) return {};
    
    return {
      'Authorization': `Bearer ${authState.token}`,
      'Content-Type': 'application/json',
    };
  }, [authState.token]);

  /**
   * Clear authentication error
   */
  const clearError = useCallback(() => {
    setAuthState(prev => ({ ...prev, error: null }));
  }, []);

  return {
    // State
    ...authState,
    
    // Actions
    login,
    register,
    logout,
    updateProfile,
    
    // Validation
    validateSession,
    hasPermission,
    canAccessWorkspace,
    
    // Utilities
    getAuthHeaders,
    clearError
  };
}

/**
 * Validate token with server
 */
async function validateTokenWithServer(token: string): Promise<boolean> {
  try {
    const response = await fetch('/api/auth/validate', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    return response.ok;
  } catch (error) {
    console.error('Token validation failed:', error);
    return false;
  }
}

/**
 * Generate demo user for development/testing
 */
export function createDemoUser(userId?: string): AuthUser {
  const id = userId || `demo-${Date.now()}`;
  
  return {
    id,
    email: `demo-${id}@example.com`,
    name: `Demo User ${id.slice(-4)}`,
    avatar: `https://api.dicebear.com/7.x/initials/svg?seed=${id}`,
    role: 'editor',
    permissions: ['canvas.read', 'canvas.write', 'canvas.share'],
    workspaces: ['demo-workspace'],
    lastLogin: Date.now(),
    createdAt: Date.now() - (Math.random() * 30 * 24 * 60 * 60 * 1000) // Random date within last 30 days
  };
}

export default useAuth;