import { apiClient } from '../lib/api';

export interface RegisterData {
  email: string;
  password: string;
  name?: string;
}

export interface LoginData {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  user: {
    id: string;
    email: string;
    name?: string;
    role: string;
  };
}

export const authService = {
  async register(data: RegisterData): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/register', data as unknown as Record<string, unknown>);
    return response;
  },

  async login(data: LoginData): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/login', data as unknown as Record<string, unknown>);
    return response;
  },

  async logout(): Promise<void> {
    // Clear both new canonical token and legacy token key
    apiClient.clearToken();
    localStorage.removeItem('token');
  },

  saveToken(token: string): void {
    // Set canonical token used by apiClient and keep legacy key for older code paths
    apiClient.setToken(token);
    localStorage.setItem('token', token);
  },

  getToken(): string | null {
    // Prefer canonical guardian_token managed by apiClient, fall back to legacy key
    return apiClient.getToken() ?? localStorage.getItem('token');
  },

  isAuthenticated(): boolean {
    return !!this.getToken();
  },
};
