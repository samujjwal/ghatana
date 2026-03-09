/**
 * Guardian API Client
 *
 * Centralized API client for all backend communication.
 * Handles authentication, error handling, and request/response normalization.
 *
 * API Base: https://api.guardian.example.com/v1
 * Authentication: JWT Bearer token (from auth store)
 *
 * @see ../stores/auth.store.ts for token management
 */

import axios, {
  AxiosInstance,
  AxiosError,
} from 'axios';

/**
 * API Response wrapper
 */
export interface ApiResponse<T = any> {
  success: boolean;
  data: T;
  error?: {
    code: string;
    message: string;
    details?: Record<string, any>;
  };
  timestamp: number;
}

/**
 * Pagination metadata
 */
export interface PaginationMeta {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasMore: boolean;
}

/**
 * Paginated response
 */
export interface PaginatedResponse<T = any> {
  data: T[];
  meta: PaginationMeta;
}

/**
 * App data from backend
 */
export interface AppData {
  id: string;
  packageName: string;
  name: string;
  category: string;
  isSystemApp: boolean;
  isMonitored: boolean;
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  lastSeen: number;
  usageTime: number; // milliseconds
  permissions: string[];
  policies?: string[];
}

/**
 * Policy data from backend
 */
export interface PolicyData {
  id: string;
  name: string;
  description: string;
  type: 'RESTRICTIVE' | 'PERMISSIVE' | 'RECOMMENDATION';
  rules: Record<string, any>;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
  appliesTo: string[]; // app package names
}

/**
 * Recommendation data
 */
export interface RecommendationData {
  id: string;
  type: 'SECURITY' | 'PERFORMANCE' | 'PRIVACY' | 'OPTIMIZATION';
  title: string;
  description: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  appIds: string[];
  actionRequired: boolean;
  createdAt: number;
  expiresAt?: number;
}

/**
 * Device status from backend
 */
export interface DeviceStatusData {
  id: string;
  deviceId: string;
  tenantId: string;
  appsMonitored: number;
  policiesActive: number;
  lastSync: number;
  syncStatus: 'synced' | 'syncing' | 'pending' | 'failed';
  securityScore: number; // 0-100
  riskCount: {
    critical: number;
    high: number;
    medium: number;
    low: number;
  };
}

/**
 * API request filters
 */
export interface AppFilters {
  search?: string;
  category?: string;
  riskLevel?: string;
  isSystemApp?: boolean;
  isMonitored?: boolean;
  page?: number;
  pageSize?: number;
}

/**
 * Guardian API Client
 */
class GuardianApiClient {
  private axiosInstance: AxiosInstance;
  private baseURL = 'https://api.guardian.example.com/v1';
  private token: string | null = null;

  constructor() {
    this.axiosInstance = axios.create({
      baseURL: this.baseURL,
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'GuardianApp/1.0',
      },
    });

    // Add request interceptor for auth
    this.axiosInstance.interceptors.request.use((config) => {
      if (this.token) {
        config.headers.Authorization = `Bearer ${this.token}`;
      }
      return config;
    });

    // Add response interceptor for error handling
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => this.handleError(error)
    );
  }

  /**
   * Set authentication token
   */
  setToken(token: string | null): void {
    this.token = token;
  }

  /**
   * Handle API errors
   */
  private handleError(error: AxiosError): Promise<never> {
    const errorMessage = error.response?.data as any;
    const apiError = {
      code: error.response?.status?.toString() || 'UNKNOWN',
      message: errorMessage?.error?.message || error.message,
      details: errorMessage?.error?.details,
      status: error.response?.status,
    };

    console.error('[GuardianApi] Error:', apiError);
    return Promise.reject(apiError);
  }

  /**
   * Get list of monitored apps
   */
  async getApps(tenantId: string, filters?: AppFilters): Promise<PaginatedResponse<AppData>> {
    const params = {
      tenantId,
      ...filters,
    };

    const response = await this.axiosInstance.get<ApiResponse<PaginatedResponse<AppData>>>(
      '/apps',
      { params }
    );

    return response.data.data;
  }

  /**
   * Get single app details
   */
  async getApp(tenantId: string, appId: string): Promise<AppData> {
    const response = await this.axiosInstance.get<ApiResponse<AppData>>(
      `/apps/${appId}`,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Update app monitoring status
   */
  async updateApp(
    tenantId: string,
    appId: string,
    data: Partial<AppData>
  ): Promise<AppData> {
    const response = await this.axiosInstance.patch<ApiResponse<AppData>>(
      `/apps/${appId}`,
      data,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Get policies for tenant
   */
  async getPolicies(tenantId: string, filters?: { page?: number; pageSize?: number }): Promise<PaginatedResponse<PolicyData>> {
    const params = {
      tenantId,
      ...filters,
    };

    const response = await this.axiosInstance.get<ApiResponse<PaginatedResponse<PolicyData>>>(
      '/policies',
      { params }
    );

    return response.data.data;
  }

  /**
   * Get single policy details
   */
  async getPolicy(tenantId: string, policyId: string): Promise<PolicyData> {
    const response = await this.axiosInstance.get<ApiResponse<PolicyData>>(
      `/policies/${policyId}`,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Create new policy
   */
  async createPolicy(tenantId: string, policy: Omit<PolicyData, 'id' | 'createdAt' | 'updatedAt'>): Promise<PolicyData> {
    const response = await this.axiosInstance.post<ApiResponse<PolicyData>>(
      '/policies',
      policy,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Update existing policy
   */
  async updatePolicy(
    tenantId: string,
    policyId: string,
    data: Partial<PolicyData>
  ): Promise<PolicyData> {
    const response = await this.axiosInstance.patch<ApiResponse<PolicyData>>(
      `/policies/${policyId}`,
      data,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Delete policy
   */
  async deletePolicy(tenantId: string, policyId: string): Promise<void> {
    await this.axiosInstance.delete(`/policies/${policyId}`, {
      params: { tenantId },
    });
  }

  /**
   * Get recommendations
   */
  async getRecommendations(tenantId: string): Promise<RecommendationData[]> {
    const response = await this.axiosInstance.get<ApiResponse<RecommendationData[]>>(
      '/recommendations',
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Get single recommendation
   */
  async getRecommendation(tenantId: string, recommendationId: string): Promise<RecommendationData> {
    const response = await this.axiosInstance.get<ApiResponse<RecommendationData>>(
      `/recommendations/${recommendationId}`,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Dismiss recommendation
   */
  async dismissRecommendation(tenantId: string, recommendationId: string): Promise<void> {
    await this.axiosInstance.post(
      `/recommendations/${recommendationId}/dismiss`,
      {},
      { params: { tenantId } }
    );
  }

  /**
   * Get device status
   */
  async getDeviceStatus(tenantId: string, deviceId: string): Promise<DeviceStatusData> {
    const response = await this.axiosInstance.get<ApiResponse<DeviceStatusData>>(
      `/devices/${deviceId}/status`,
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Get all devices for tenant
   */
  async getDevices(tenantId: string): Promise<DeviceStatusData[]> {
    const response = await this.axiosInstance.get<ApiResponse<DeviceStatusData[]>>(
      '/devices',
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Trigger device sync
   */
  async syncDevice(tenantId: string, deviceId: string): Promise<DeviceStatusData> {
    const response = await this.axiosInstance.post<ApiResponse<DeviceStatusData>>(
      `/devices/${deviceId}/sync`,
      {},
      { params: { tenantId } }
    );

    return response.data.data;
  }

  /**
   * Health check
   */
  async healthCheck(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }> {
    const response = await this.axiosInstance.get<ApiResponse<{ status: string }>>(
      '/health'
    );

    return {
      status: response.data.data.status as any,
    };
  }

  /**
   * Update tenant-level configuration
   * This is a lightweight generic endpoint to persist UI-controlled settings.
   * In production the backend should provide strongly-typed endpoints for each setting.
   */
  async updateConfig(tenantId: string, data: Record<string, any>): Promise<unknown> {
    const response = await this.axiosInstance.post<ApiResponse<unknown>>(
      '/config',
      data,
      { params: { tenantId } }
    );

    return response.data.data;
  }
}

// Export singleton instance
export const guardianApi = new GuardianApiClient();
