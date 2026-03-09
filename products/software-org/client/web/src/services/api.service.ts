/**
 * API Service
 *
 * Central API client for backend communication.
 * Handles authentication, error handling, and request/response transformation.
 *
 * @package @ghatana/software-org-web
 */

import type { Organization, Department, Team, Person, Role, RestructureProposal, AuditEntry } from '@/types/org.types';

/**
 * API Configuration
 */
const API_CONFIG = {
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
};

/**
 * API Error class
 */
export class APIError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public response?: any
  ) {
    super(message);
    this.name = 'APIError';
  }
}

/**
 * API Client
 */
class APIClient {
  private baseURL: string;
  private token: string | null = null;

  constructor() {
    this.baseURL = API_CONFIG.baseURL;
  }

  /**
   * Set authentication token
   */
  setToken(token: string) {
    this.token = token;
  }

  /**
   * Clear authentication token
   */
  clearToken() {
    this.token = null;
  }

  /**
   * Make HTTP request
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${this.baseURL}${endpoint}`;

    const headers = {
      ...API_CONFIG.headers,
      ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      ...options.headers,
    };

    try {
      const response = await fetch(url, {
        ...options,
        headers,
        signal: AbortSignal.timeout(API_CONFIG.timeout),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new APIError(
          error.message || `HTTP ${response.status}: ${response.statusText}`,
          response.status,
          error
        );
      }

      return await response.json();
    } catch (error) {
      if (error instanceof APIError) throw error;

      if (error instanceof Error) {
        throw new APIError(error.message);
      }

      throw new APIError('An unknown error occurred');
    }
  }

  /**
   * GET request
   */
  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  /**
   * POST request
   */
  async post<T>(endpoint: string, data?: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * PUT request
   */
  async put<T>(endpoint: string, data?: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  /**
   * DELETE request
   */
  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }
}

/**
 * API Service singleton
 */
const apiClient = new APIClient();

/**
 * Organization API
 */
export const organizationAPI = {
  getOrganization: () => apiClient.get<Organization>('/organization'),
  getDepartments: () => apiClient.get<Department[]>('/departments'),
  getDepartment: (id: string) => apiClient.get<Department>(`/departments/${id}`),
  getTeams: () => apiClient.get<Team[]>('/teams'),
  getTeam: (id: string) => apiClient.get<Team>(`/teams/${id}`),
  createTeam: (data: Partial<Team>) => apiClient.post<Team>('/teams', data),
  updateTeam: (id: string, data: Partial<Team>) => apiClient.put<Team>(`/teams/${id}`, data),
  deleteTeam: (id: string) => apiClient.delete(`/teams/${id}`),
};

/**
 * People API
 */
export const peopleAPI = {
  getPeople: () => apiClient.get<Person[]>('/people'),
  getPerson: (id: string) => apiClient.get<Person>(`/people/${id}`),
  updatePerson: (id: string, data: Partial<Person>) => apiClient.put<Person>(`/people/${id}`, data),
};

/**
 * Roles API
 */
export const rolesAPI = {
  getRoles: () => apiClient.get<Role[]>('/roles'),
  getRole: (id: string) => apiClient.get<Role>(`/roles/${id}`),
  createRole: (data: Partial<Role>) => apiClient.post<Role>('/roles', data),
  updateRole: (id: string, data: Partial<Role>) => apiClient.put<Role>(`/roles/${id}`, data),
  deleteRole: (id: string) => apiClient.delete(`/roles/${id}`),
};

/**
 * Restructure API
 */
export const restructureAPI = {
  getProposals: () => apiClient.get<RestructureProposal[]>('/restructure/proposals'),
  getProposal: (id: string) => apiClient.get<RestructureProposal>(`/restructure/proposals/${id}`),
  createProposal: (data: Partial<RestructureProposal>) =>
    apiClient.post<RestructureProposal>('/restructure/proposals', data),
  approveProposal: (id: string) =>
    apiClient.post<RestructureProposal>(`/restructure/proposals/${id}/approve`),
  rejectProposal: (id: string, reason: string) =>
    apiClient.post<RestructureProposal>(`/restructure/proposals/${id}/reject`, { reason }),
};

/**
 * Audit API
 */
export const auditAPI = {
  getAuditLog: (filters?: any) => apiClient.get<AuditEntry[]>('/audit/log'),
  getAuditEntry: (id: string) => apiClient.get<AuditEntry>(`/audit/log/${id}`),
};

/**
 * Auth API
 */
export const authAPI = {
  login: (email: string, password: string) =>
    apiClient.post<{ token: string; user: Person }>('/auth/login', { email, password }),
  logout: () => apiClient.post('/auth/logout'),
  refreshToken: () => apiClient.post<{ token: string }>('/auth/refresh'),
  setToken: (token: string) => apiClient.setToken(token),
  clearToken: () => apiClient.clearToken(),
};

export default apiClient;

