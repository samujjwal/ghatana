import type { AuthResponse } from '../services/auth.service';

type AuthService = {
  login: (credentials: { email: string; password: string }) => Promise<AuthResponse>;
  register: (data: { email: string; password: string; name?: string }) => Promise<AuthResponse>;
  getToken: () => string | null;
  isAuthenticated: () => boolean;
  logout: () => void;
  saveToken: (token: string) => void;
};

declare global {
  interface Window {
    authService: AuthService;
  }
}

export const MOCK_USER = {
  email: 'demo@example.com',
  password: 'demo123',
  name: 'Demo Parent User',
  role: 'parent',
  id: 'demo-user-123'
};

export const MOCK_TOKEN = 'mock-jwt-token-for-development';

// Global flag to track if mock auth is initialized
let isMockAuthInitialized = false;

export function setupMockAuth() {
  if (import.meta.env.MODE !== 'development' || isMockAuthInitialized) return;

  console.log('[DEV] Setting up mock authentication');
  isMockAuthInitialized = true;

  // Ensure window.authService exists
  if (!window.authService) {
    window.authService = {} as AuthService;
  }

  // Save original methods
  const originalAuthService = { ...window.authService };

  window.authService = {
    ...originalAuthService,

    async login(credentials: { email: string; password: string }): Promise<AuthResponse> {
      if (credentials.email === MOCK_USER.email && credentials.password === MOCK_USER.password) {
        localStorage.setItem('guardian_token', MOCK_TOKEN);
        localStorage.setItem('token', MOCK_TOKEN);
        return {
          accessToken: MOCK_TOKEN,
          user: {
            id: MOCK_USER.id,
            email: MOCK_USER.email,
            name: MOCK_USER.name,
            role: MOCK_USER.role
          }
        };
      }
      throw new Error('Invalid credentials');
    },

    async register(data: { email: string; password: string; name?: string }): Promise<AuthResponse> {
      if (data.email === MOCK_USER.email) {
        return this.login({ email: data.email, password: data.password });
      }
      throw new Error('Registration failed');
    },

    getToken(): string | null {
      return localStorage.getItem('guardian_token') ?? localStorage.getItem('token');
    },

    isAuthenticated(): boolean {
      return !!this.getToken();
    },

    logout(): void {
      localStorage.removeItem('guardian_token');
      localStorage.removeItem('token');
    },

    saveToken(token: string): void {
      localStorage.setItem('guardian_token', token);
      localStorage.setItem('token', token);
    }
  };

  // Ensure the mock methods are properly set
  Object.assign(window.authService, {
    login: async (credentials: { email: string; password: string }): Promise<AuthResponse> => {
      if (credentials.email === MOCK_USER.email && credentials.password === MOCK_USER.password) {
        const response = {
          accessToken: MOCK_TOKEN,
          user: {
            id: MOCK_USER.id,
            email: MOCK_USER.email,
            name: MOCK_USER.name,
            role: MOCK_USER.role
          }
        };
        localStorage.setItem('guardian_token', MOCK_TOKEN);
        localStorage.setItem('token', MOCK_TOKEN);
        return response;
      }
      throw new Error('Invalid credentials');
    },
    register: async (data: { email: string; password: string; name?: string }): Promise<AuthResponse> => {
      if (data.email === MOCK_USER.email) {
        return window.authService.login({ email: data.email, password: data.password });
      }
      throw new Error('Registration failed');
    },
    getToken: (): string | null => localStorage.getItem('guardian_token') ?? localStorage.getItem('token'),
    isAuthenticated: (): boolean => !!(localStorage.getItem('guardian_token') ?? localStorage.getItem('token')),
    logout: (): void => {
      localStorage.removeItem('guardian_token');
      localStorage.removeItem('token');
    },
    saveToken: (token: string): void => {
      localStorage.setItem('guardian_token', token);
      localStorage.setItem('token', token);
    }
  });
}
