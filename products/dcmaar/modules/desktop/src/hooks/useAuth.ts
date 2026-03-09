import { useNavigate, useLocation } from 'react-router-dom';
import { useState, useEffect, useCallback } from 'react';
// Mock snackbar
const useSnackbar = () => ({
  enqueueSnackbar: (message: string, options?: unknown) => console.log('Snackbar:', message, options)
});
import { useApiErrorHandler } from './useApiErrorHandler';
// Mock User type and authApi
interface User {
  id: string;
  email: string;
  name: string;
  role?: string;
}

const _authApi = {
  login: async (_credentials: unknown) => ({ user: { id: '1', email: 'test@example.com', name: 'Test User', role: 'user' }, token: 'mock-token' }),
  register: async (_userData: any) => ({ user: { id: '1', email: _userData?.email ?? 'test@example.com', name: _userData?.name ?? 'Test User', role: 'user' }, token: 'mock-token' }),
  logout: async () => ({}),
  refresh: async () => ({ token: 'mock-refreshed-token' }),
  validateToken: async (_token: string) => ({ valid: true }),
  requestPasswordReset: async (_email: string) => ({ success: true }),
  resetPassword: async (_data: unknown) => ({ success: true }),
  getCurrentUser: async () => ({ id: '1', email: 'test@example.com', name: 'Test User', role: 'user' })
};

// mark mocked API as intentionally unused in this environment
void _authApi;

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface AuthContextType extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  register: (userData: { email: string; password: string; name: string }) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

// Default auth state
const defaultAuthState: AuthState = {
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: true,
};

// Get stored auth data from localStorage
const getStoredAuth = (): Partial<AuthState> => {
  try {
    const storage = (globalThis as any).localStorage;
    if (!storage || typeof storage.getItem !== 'function') return {};

    const token = storage.getItem('auth_token');
    const user = storage.getItem('auth_user');

    return {
      token,
      user: user ? JSON.parse(user) : null,
      isAuthenticated: !!token,
    };
  } catch (error) {
    console.error('Failed to parse stored auth data:', error);
    return {};
  }
};

/**
 * Custom hook to handle authentication state and methods
 */
export function useAuth(): AuthContextType {
  const [state, setState] = useState<AuthState>({
    ...defaultAuthState,
    ...getStoredAuth(),
  });

  const navigate = useNavigate();
  const location = useLocation();
  const { enqueueSnackbar } = useSnackbar();
  const { handleError } = useApiErrorHandler();

  // Set default axios auth header
  useEffect(() => {
    if (state.token) {
      // Set the token in axios defaults
      // apiClient.setAuthToken(state.token);
    } else {
      // Clear the token from axios defaults
      // apiClient.setAuthToken(null);
    }
  }, [state.token]);

  // Check for existing session on mount
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const { token, user } = getStoredAuth();

        if (token && user) {
          // Validate the token with the server
          // const { data } = await authApi.getProfile();
          // setState(prev => ({
          //   ...prev,
          //   user: data,
          //   isAuthenticated: true,
          //   isLoading: false,
          // }));
        } else {
          setState(prev => ({
            ...prev,
            isLoading: false,
          }));
        }
      } catch (error) {
        console.error('Auth check failed:', error);
        // Clear invalid auth data
        try {
          const storage = (globalThis as any).localStorage;
          if (storage && typeof storage.removeItem === 'function') {
            storage.removeItem('auth_token');
            storage.removeItem('auth_user');
          }
        } catch (e) {
          // ignore
        }

        setState(prev => ({
          ...prev,
          isLoading: false,
          isAuthenticated: false,
          user: null,
          token: null,
        }));
      }
    };

    checkAuth();
  }, []);

  /**
   * Handle user login
   */
  const login = useCallback(async (_email: string, _password: string) => {
    try {
      setState(prev => ({ ...prev, isLoading: true }));

      // const { data } = await authApi.login({ email, password });
      const data = { user: { id: '1', email: _email, name: 'Test User' }, token: 'test-token' };

      // Store auth data
      try {
        const storage = (globalThis as any).localStorage;
        if (storage && typeof storage.setItem === 'function') {
          storage.setItem('auth_token', data.token);
          storage.setItem('auth_user', JSON.stringify(data.user));
        }
      } catch (e) {
        // ignore
      }

      // Update state
      setState({
        user: data.user,
        token: data.token,
        isAuthenticated: true,
        isLoading: false,
      });

      // Redirect to the requested page or home
      const from = (location.state as any)?.from?.pathname || '/';
      navigate(from, { replace: true });

      enqueueSnackbar('Logged in successfully', { variant: 'success' });
    } catch (error) {
      handleError(error, 'Login failed');
      throw error;
    } finally {
      setState(prev => ({ ...prev, isLoading: false }));
    }
  }, [enqueueSnackbar, handleError, location.state, navigate]);

  /**
   * Handle user registration
   */
  const register = useCallback(async (userData: { email: string; password: string; name: string }) => {
    try {
      setState(prev => ({ ...prev, isLoading: true }));

      // const { data } = await authApi.register(userData);
      const data = { user: { id: '1', ...userData }, token: 'test-token' };

      // Store auth data
      try {
        const storage = (globalThis as any).localStorage;
        if (storage && typeof storage.setItem === 'function') {
          storage.setItem('auth_token', data.token);
          storage.setItem('auth_user', JSON.stringify(data.user));
        }
      } catch (e) {
        // ignore
      }

      // Update state
      setState({
        user: data.user,
        token: data.token,
        isAuthenticated: true,
        isLoading: false,
      });

      // Redirect to home
      navigate('/', { replace: true });

      enqueueSnackbar('Registration successful', { variant: 'success' });
    } catch (error) {
      handleError(error, 'Registration failed');
      throw error;
    } finally {
      setState(prev => ({ ...prev, isLoading: false }));
    }
  }, [enqueueSnackbar, handleError, navigate]);

  /**
   * Handle user logout
   */
  const logout = useCallback(() => {
    // Clear auth data
    try {
      const storage = (globalThis as any).localStorage;
      if (storage && typeof storage.removeItem === 'function') {
        storage.removeItem('auth_token');
        storage.removeItem('auth_user');
      }
    } catch (e) {
      // ignore
    }

    // Update state
    setState({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
    });

    // Redirect to login
    navigate('/login', { replace: true });

    enqueueSnackbar('Logged out successfully', { variant: 'info' });
  }, [enqueueSnackbar, navigate]);

  /**
   * Refresh user data
   */
  const refreshUser = useCallback(async () => {
    if (!state.isAuthenticated) return;

    try {
      // const { data } = await authApi.getProfile();
      // setState(prev => ({
      //   ...prev,
      //   user: data,
      // }));
      // 
      // // Update stored user data
      // localStorage.setItem('auth_user', JSON.stringify(data));
    } catch (error) {
      console.error('Failed to refresh user data:', error);
      // If refresh fails, log the user out
      logout();
    }
  }, [state.isAuthenticated, logout]);

  return {
    ...state,
    login,
    register,
    logout,
    refreshUser,
  };
}

export default useAuth;
